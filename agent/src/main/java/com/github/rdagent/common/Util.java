package com.github.rdagent.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import com.github.rdagent.Constants;
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
	
	public static String getIpAddr(HttpServletRequest request) {
		//优先从自定义header中获取ip
		String ip = request.getHeader(Constants.customIpHeader);
		if(ip != null && !"".equals(ip)) {
			//System.out.println("luanfei debug +++ getHeader is not null ");
			//各种各样的http client框架可能会给header加格式，这里再提取一下
			Pattern p = Pattern.compile("(\\d{1,3}?\\.\\d{1,3}?\\.\\d{1,3}?\\.\\d{1,3}?)([^\\d]|$)");
			Matcher m = p.matcher(ip);
			if(m.find()) {
				//System.out.println("luanfei debug +++ custom header found ");
				return m.group(1);
			}else {
				return ip;
			}
		}
		ip = request.getHeader("x-forwarded-for");
		if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
			ip = request.getHeader("Proxy-Client-IP");
		}
		if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
			ip = request.getHeader("WL-Proxy-Client-IP");
		}
		if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
			ip = request.getRemoteAddr();
		}
		//System.out.println("debug InterceptorUtil 4");
		//处理多级代理
		if(ip!=null && ip.length()>15){ //"***.***.***.***".length() = 15
			//System.out.println("debug InterceptorUtil 5");
			if(ip.indexOf(",")>0){
				ip = ip.substring(0, ip.indexOf(","));
				//System.out.println("debug InterceptorUtil 6");
			}
		}
		//System.out.println("debug InterceptorUtil 7");
		return ip;
	}

}
