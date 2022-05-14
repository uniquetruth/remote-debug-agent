package com.github.rdagent.transformer.intercepter;

import com.github.rdagent.AgentOptions;

/**
 * intercept dubbo framework, get invoker's ip from Channel instance and and bind it to work thread
 * @author uniqueT
 *
 */
public class DubboIntercepter {
	
	public static void bindIP(Object channel) {
		try {
			String ip = InterceptorUtil.getDubboIp(channel);
			
			if(ip != null && !"".equals(ip) && AgentOptions.isDependIP()) {
				//ignore non-ip identification situation temporarily
				IPmap.bindIP(ip);
			}
		}catch(Exception e) {
			System.err.println("DubboIntercepter Exception : "+e.getMessage());
			e.printStackTrace();
		}
		return;
	}

}
