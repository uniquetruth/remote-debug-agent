package com.github.rdagent.server;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import com.github.rdagent.AgentOptions;
import com.github.rdagent.Constants;

public class ServerUtil {
	
	public static String getQueryParam(HttpServletRequest request, String name) {
		String str = request.getQueryString();
		//System.out.println(str);
		if(str==null) {
			return "";
		}
		Pattern p = Pattern.compile(name+"=([^&]+?)(&|$)");
		Matcher m = p.matcher(request.getQueryString());
		if(m.find()) {
			return m.group(1);
		}else {
			return "";
		}
	}
	
	public static List<String> getQueryParamList(HttpServletRequest request, String name) {
		List<String> result = new ArrayList<String>();
		String str = request.getQueryString();
		if(str==null) {
			return result;
		}
		Pattern p = Pattern.compile(name+"=([^&]+?)(&|$)");
		Matcher m = p.matcher(request.getQueryString());
		while(m.find()) {
			result.add(m.group(1));
		}
		return result;
	}

	public static String getIpAddr(HttpServletRequest request) {
		if(AgentOptions.isDependIP()) {
			return getIpFromHttprequest(request);
		}else {
			return Constants.virtualIp;
		}
	}
	
	private static String getIpFromHttprequest(HttpServletRequest request) {
		//get ip from custom header firstly
		String ip = request.getHeader(Constants.customIpHeader);
		if(ip != null && !"".equals(ip)) {
			//System.out.println("debug +++ getHeader is not null ");
			Pattern p = Pattern.compile("(\\d{1,3}?\\.\\d{1,3}?\\.\\d{1,3}?\\.\\d{1,3}?)([^\\d]|$)");
			Matcher m = p.matcher(ip);
			if(m.find()) {
				//System.out.println("debug +++ custom header found ");
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
		//for multiple proxies
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
