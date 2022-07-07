package com.github.rdagent.transformer.intercepter;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.rdagent.Constants;

public class FeignTemplateIntercepter {
	
	/**
	 * add custom header into feign template's headers
	 * @param headers
	 */
	public static void addCustomHeader(Object templateRequest) {
		if (IPmap.hasAddress()) {
			List<String> l = new ArrayList<String>();
			l.add(IPmap.getIpMap().get(Thread.currentThread()));
			Map<String, Collection<String>> map = new HashMap<String, Collection<String>>();
			map.put(Constants.customIpHeader, l);
			try {
				//feign.RequestTemplate.headers(Map<String, Collection<String>>)
				Method m = templateRequest.getClass().getMethod("headers", Map.class);
				m.invoke(templateRequest, map);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

}
