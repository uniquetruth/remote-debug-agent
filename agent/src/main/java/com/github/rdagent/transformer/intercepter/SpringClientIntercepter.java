package com.github.rdagent.transformer.intercepter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.github.rdagent.Constants;

public class SpringClientIntercepter {
	
	/**
	 * add custom header into header map
	 * @param headers
	 */
	public static void addCustomHeader(Map<String, Collection<String>> headers) {
		if(!headers.containsKey(Constants.customIpHeader)) {
			if(IPmap.hasAddress()) {
				List<String> l = new ArrayList<String>();
				l.add(IPmap.getIpMap().get(Thread.currentThread()));
				headers.put(Constants.customIpHeader, l);
			}
		}
	}

}
