package com.github.rdagent.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
	
	/**
	 * convert List&lt;TraceVo&gt; into json string
	 * @param l trace list
	 * @param displayTime 
	 * @param coverage 
	 * @return json string
	 */
	public static String getJsonTrace(String ip, boolean displayTime, boolean coverage) {
		List<TraceVo> l = IPmap.getTraceMap().get(ip);
		if(l==null||l.size()==0) {
			return "[]";
		}
		CoverageAnalyzer analyzer = null;
		if(coverage) {
			analyzer = new CoverageAnalyzer(ip);
		}
		List<HashMap<String, Object>> result = prepareGeneralObject(l, displayTime, analyzer);
		Gson gson = new GsonBuilder().disableHtmlEscaping().create();
		return gson.toJson(result);
	}
	
	private static List<HashMap<String, Object>> prepareGeneralObject(List<TraceVo> l, boolean displayTime, CoverageAnalyzer analyzer) {
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
				if(analyzer!=null) {
					object.put("coverage", analyzer.analyse(vo.getMethod()));
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
				lastO.put("calls", prepareGeneralObject(subList, displayTime, analyzer));
				i = j;
			}
		}
		return result;
	}

}
