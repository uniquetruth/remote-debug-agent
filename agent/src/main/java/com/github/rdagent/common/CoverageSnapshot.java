package com.github.rdagent.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.rdagent.transformer.intercepter.IPmap;

/**
 * Snapshot coverage data from 3 maps
 * @author uniqueT
 *
 */
public class CoverageSnapshot {

	private static Pattern classNameExtra = Pattern.compile("\\s(.+?)\\.[^\\.]+?\\(");
	
	private Map<String, Set<String>> ipMethodMap = null;
	private Map<String, boolean[]> coverageMap = null;
	private Map<String, ArrayList<Integer>> recordLineMap = null;
	
	public CoverageSnapshot() {
		recordLineMap = new HashMap<String, ArrayList<Integer>>();
		clone3Maps();
	}
	
	private void clone3Maps() {
		Map<String, ArrayList<Integer>> methodLineMap = cloneLineMap(IPmap.getMethodLineMap());
		//No need to synchronize after deep clone
		coverageMap = cloneCoverMap(IPmap.getCoverMap());
		// Sequential execution blocks only contain probe of first line.
		// So we have to fill the full probe according to line number table
		for (Map.Entry<String, boolean[]> entry : coverageMap.entrySet()) {
			boolean[] probe = entry.getValue();
			ArrayList<Integer> methodLines = methodLineMap.get(entry.getKey().split("&")[1]);
			Util.fillProbe(probe, methodLines);
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
