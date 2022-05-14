package com.github.rdagent.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.rdagent.transformer.intercepter.IPmap;

import java.util.TreeMap;

/**
 * Analyze coverage data from 3 maps
 * @author luanfei
 *
 */
public class CoverageAnalyzer {

	private static Pattern classNameExtra = Pattern.compile("\\s(.+?)\\.[^\\.]+?\\(");
	
	private Map<String, Set<String>> ipMethodMap = new HashMap<String, Set<String>>();
	private Map<String, boolean[]> coverageMap = new HashMap<String, boolean[]>();
	private Map<String, ArrayList<Integer>> recordLineMap = new HashMap<String, ArrayList<Integer>>();
	private String ip;
	
	public CoverageAnalyzer() {
		clone3Maps();
	}
	
	public CoverageAnalyzer(String ip) {
		this();
		this.ip = ip;
	}
	
	public String analyse(String method){
		return analyse(this.ip, method);
	}

	public String analyse(String ip, String method){
		if(method.contains("<clinit>")) {
			return getClinitCoverage(ip, method);
		}else {
			return getCoverage(ip, method);
		}			
	}
	
	private String getCoverage(String ip, String method) {
		ArrayList<Integer> lineList = recordLineMap.get(method);
		if(lineList==null) {
			return "no method line data";
		}
		boolean[] coverage = coverageMap.get(ip+"&"+method);
		if(coverage==null) {
			return "";
		}
		if(coverage.length != lineList.size()*2) {
			return "maps' line size don't match. lineList.size="+lineList.size()+" coverage.length="+coverage.length;
		}
	    //line number table is not sequence, need pre-progress
	    TreeMap<Integer, Integer> sortedLines = new TreeMap<Integer, Integer>();
	    int cover;
	    for(int i=0;i<lineList.size();i++) {
	    	Integer n = lineList.get(i);
	    	if(coverage[2*i] && coverage[2*i+1]) {
	    		cover = 1;
	    	}else if(coverage[2*i] && !coverage[2*i+1]){
	    		cover = 0;
	    	}else {
	    		cover = -1;
	    	}
	    	if(sortedLines.containsKey(n)) {
	    		if(sortedLines.get(n)<cover) {
	    			sortedLines.put(n, cover);
	    		}
	    	}else {
	    		sortedLines.put(n, cover);
	    	}
	    }
	    
	    StringBuilder range = new StringBuilder("");
	    Iterator<Entry<Integer, Integer>> it = sortedLines.entrySet().iterator();
	    boolean isCovered = false;
	    int lastLine = -1;
	    while(it.hasNext()) {
	    	Entry<Integer, Integer> entry = it.next();
	    	//start from a line which covered
	    	if(!isCovered && entry.getValue()==1) {
	    		range.append("[").append(entry.getKey()).append(",");
	    		isCovered = true;
	    	}else if(isCovered && entry.getValue()==0){
	    		range.append(entry.getKey()).append(")");
	    		isCovered = false;
	    	}else if(isCovered && entry.getValue()==-1){
	    		range.append(lastLine).append("]");
	    		isCovered = false;
	    	}
	    	lastLine = entry.getKey();
	    }
	    if(range.toString().endsWith(",")) {
	    	range.append(lastLine).append("]");
	    }
	    return range.toString();
	}
	
	private String getClinitCoverage(String ip, String method) {
		ArrayList<Integer> lineList = recordLineMap.get(method);
		if(lineList==null) {
			return "no method line data";
		}
		boolean[] coverage = coverageMap.get(ip+"&"+method);
		if(coverage==null) {
			return "";
		}
		if(coverage.length != lineList.size()*2) {
			return "maps' line size don't match. lineList.size="+lineList.size()+" coverage.length="+coverage.length;
		}
		
		StringBuilder range = new StringBuilder("{");
		for(int i=0;i<coverage.length;i+=2) {
			if(coverage[i]) {
				range.append(lineList.get(i/2)).append(", ");
			}
		}
		if(range.length()>2) {
			range.setLength(range.length()-2);
		}
		range.append("}");
		return range.toString();
	}
	
	public void clone3Maps() {
		Map<String, ArrayList<Integer>> methodLineMap = cloneLineMap(IPmap.getMethodLineMap());
		//No need to synchronize after deep clone
		coverageMap = cloneCoverMap(IPmap.getCoverMap());
		//Sequential execution blocks only contain probe of first line. 
		//So we have to fill the full probe according to line number table
		boolean needFill = false;
		for (Map.Entry<String, boolean[]> entry : coverageMap.entrySet()) {
			boolean[] probes = entry.getValue();
			ArrayList<Integer> methodLines = methodLineMap.get(entry.getKey().split("&")[1]);
			for (int i = 0; i < methodLines.size(); i++) {
				// If this is the first line if a sequential execution block, and it has been executed
				// means the following codes all have been executed too
				if (methodLines.get(i) < 0 && probes[i * 2]) {
					needFill = true;
				}
				// If this isn't a line of sequential execution block,
				// or a break point by caused by exception(see also AppInterceptor.onMethodOut).
				// Means the following codes haven't been executed.
				if (methodLines.get(i) > 0 || methodLines.get(i) < 0 && probes[i * 2 + 1]) {
					needFill = false;
				}
				if (needFill) {
					probes[i * 2] = true;
					probes[i * 2 + 1] = true;
				}
			}
		}

		//only record line number of invoked methods by ip
		ipMethodMap = cloneMethodMap(IPmap.getMethodMap());

		HashSet<String> classSet = new HashSet<String>();
		for (Map.Entry<String, Set<String>> entry : ipMethodMap.entrySet()) {
			for (String method : entry.getValue()) {
				String className = getClassName(method);
				if (!classSet.contains(className) && className != null) {
					classSet.add(className);
				}
			}
		}
		for (Map.Entry<String, ArrayList<Integer>> entry : methodLineMap.entrySet()) {
			String method = entry.getKey();
			if (isInClassSet(method, classSet)) {
				if (!recordLineMap.containsKey(method)) {
					ArrayList<Integer> posLineNums = new ArrayList<Integer>();
					for (int i : entry.getValue()) {
						posLineNums.add(i > 0 ? i : -i);
					}
					recordLineMap.put(method, posLineNums);
				}
			}
		}
	}
	
	private String getClassName(String method) {
		Matcher m = classNameExtra.matcher(method);
		if(m.find()) {
			return m.group(1);
		}
		return null;
	}
	
	private boolean isInClassSet(String method, HashSet<String> classSet) {
		for(String className : classSet) {
			if(method.contains(className)) {
				return true;
			}
		}
		return false;
	}
	
	private Map<String, ArrayList<Integer>> cloneLineMap(Map<String, ArrayList<Integer>> map){
		Map<String, ArrayList<Integer>> m = new HashMap<String, ArrayList<Integer>>();
		String key=null;
		ArrayList<Integer> value = null;
		Iterator<Map.Entry<String, ArrayList<Integer>>> it = map.entrySet().iterator();
		while(it.hasNext()) {
			Map.Entry<String, ArrayList<Integer>> entry = it.next();
			key = new String(entry.getKey());
			value = new ArrayList<Integer>();
			for(int i : entry.getValue()) {
				value.add(i);
			}
			m.put(key, value);
		}
		return m;
	}
	
	private Map<String, boolean[]> cloneCoverMap(Map<String, boolean[]> map){
		Map<String, boolean[]> m = new HashMap<String, boolean[]>();
		String key=null;
		boolean[] value = null;
		Iterator<Map.Entry<String, boolean[]>> it = map.entrySet().iterator();
		while(it.hasNext()) {
			Map.Entry<String, boolean[]> entry = it.next();
			key = new String(entry.getKey());
			value = entry.getValue().clone();
			m.put(key, value);
		}
		return m;
	}
	
	private Map<String, Set<String>> cloneMethodMap(Map<String, Set<String>> map){
		Map<String, Set<String>> m = new HashMap<String, Set<String>>();
		String key=null;
		HashSet<String> value = null;
		Iterator<Map.Entry<String, Set<String>>> it = map.entrySet().iterator();
		while(it.hasNext()) {
			Map.Entry<String, Set<String>> entry = it.next();
			key = new String(entry.getKey());
			value = new HashSet<String>();
			synchronized(entry.getValue()) { //here is Collections.synchronizedSet, should be locked before iterator
				Iterator<String> t = entry.getValue().iterator();
				while(t.hasNext()) {
					value.add(t.next());
				}
			}
			m.put(key, value);
		}
		return m;
	}
	
	public Map<String, Set<String>> getIpMethodMap(){
		return ipMethodMap;
	}
	public Map<String, boolean[]> getCoverageMap(){
		return coverageMap;
	}
	public Map<String, ArrayList<Integer>> getRecordLineMap(){
		return recordLineMap;
	}

}
