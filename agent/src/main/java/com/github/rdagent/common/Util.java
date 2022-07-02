package com.github.rdagent.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;
import java.util.Map.Entry;

import com.github.rdagent.transformer.intercepter.IPmap;
import com.github.rdagent.transformer.intercepter.vo.TraceVo;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * common tools
 * @author uniqueT
 *
 */
public class Util {
	
	public static String getJsonTrace(String ip) {
		return getJsonTrace(ip, false);
	}
	
	public static String getJsonTrace(String ip, boolean displayTime) {
		return getJsonTrace(ip, displayTime, false);
	}
	
	public static String getJsonTrace(String ip, boolean displayTime, boolean sql) {
		return getJsonTrace(ip, displayTime, sql);
	}
	
	/**
	 * convert List&lt;TraceVo&gt; into json string
	 * @param l trace list
	 * @param displayTime 
	 * @param coverage 
	 * @return json string
	 */
	public static String getJsonTrace(String ip, boolean displayTime, boolean coverage, boolean sql) {
		List<TraceVo> l = IPmap.getTraceMap().get(ip);
		if(l==null||l.size()==0) {
			return "[]";
		}
		
		CoverageSnapshot snapshot = null;
		if(coverage) { 
			snapshot = new CoverageSnapshot();
		}
		List<HashMap<String, Object>> result = prepareGeneralObject(l, displayTime, sql, snapshot);
		Gson gson = new GsonBuilder().disableHtmlEscaping().create();
		return gson.toJson(result);
	}
	
	private static List<HashMap<String, Object>> prepareGeneralObject(List<TraceVo> l, boolean displayTime, boolean sql, CoverageSnapshot snapshot) {
		List<HashMap<String, Object>> result = new ArrayList<HashMap<String, Object>>();
		
		int deep = l.get(0).getDeep();
		HashMap<String, Object> lastO = null;
		for(int i=0;i<l.size();i++) {
			TraceVo vo = l.get(i);
			if(vo.getDeep()==deep) {
				HashMap<String, Object> object = new HashMap<String, Object>();
				object.put("method", vo.getMethod());
				if(vo.getParameters().length > 0) {
					object.put("parameters", vo.getParameters());
				}
				if(!vo.getMethod().startsWith("void ") || vo.getReturnValue().startsWith("Exception:")) {
					object.put("return value", vo.getReturnValue());
				}
				if(displayTime) {
					object.put("cost time", vo.getEndTime() - vo.getStartTime());
				}
				if(sql) {
					object.put("sql", vo.getSqlList());
				}
				if(snapshot != null) {
					boolean[] probe = vo.getCoverage();
					ArrayList<Integer> lines = snapshot.getRecordLineMap().get(vo.getMethod());
					//use map that contains minus line number to fill the probe
					fillProbe(probe, IPmap.getMethodLineMap().get(vo.getMethod()));
					object.put("coverage", getCoverageStr(lines, probe, vo.getMethod().contains("<clinit>")));
				}
				result.add(object);
				lastO = object;
			}else if(vo.getDeep() > deep) {
				List<TraceVo> subList = new ArrayList<TraceVo>();
				int j;
				for(j=i;j<l.size();j++) {
					if(l.get(j).getDeep()==deep) {
						break;
					}
					subList.add(l.get(j));
				}
				lastO.put("calls", prepareGeneralObject(subList, displayTime, sql, snapshot));
				i = j - 1;
			}
		}
		return result;
	}
	
	private static String getCoverageStr(ArrayList<Integer> lineList, boolean[] coverage, boolean isClinit) {
		if(coverage.length != lineList.size()*2) {
			return "maps' line size don't match. lineList.size="+lineList.size()+" coverage.length="+coverage.length;
		}
		
		if(isClinit) {
			return getClinitCoverage(lineList, coverage);
		}else {
			return getCoverage(lineList, coverage);
		}
	}
	
	private static String getCoverage(ArrayList<Integer> lineList, boolean[] coverage) {
		//line number table is not sequential, need pre-progress
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
	
	private static String getClinitCoverage(ArrayList<Integer> lineList, boolean[] coverage) {
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
	
	public static void fillProbe(boolean[] probe, ArrayList<Integer> methodLines) {
		boolean needFill = false;
		for (int i = 0; i < methodLines.size(); i++) {
			// If this is the first line if a sequential execution block, and it has been executed
			// means the following codes all have been executed too
			if (methodLines.get(i) < 0 && probe[i * 2]) {
				needFill = true;
			}
			// If this isn't a line of sequential execution block, or a break point by caused by exception(see also AppInterceptor.onMethodOut).
			// Means the following codes haven't been executed.
			if (methodLines.get(i) > 0 || methodLines.get(i) < 0 && probe[i * 2 + 1]) {
				needFill = false;
			}
			if (needFill) {
				probe[i * 2] = true;
				probe[i * 2 + 1] = true;
			}
		}
	}

}
