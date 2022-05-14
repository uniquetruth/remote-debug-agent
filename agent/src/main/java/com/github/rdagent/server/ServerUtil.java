package com.github.rdagent.server;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import com.github.rdagent.AgentOptions;
import com.github.rdagent.Constants;
import com.github.rdagent.common.Util;

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
			return Util.getIpAddr(request);
		}else {
			return Constants.virtualIp;
		}
	}

}
