package com.github.rdagent.transformer.intercepter;

import com.github.rdagent.AgentOptions;

public class HystrixIntercepter {
	
	public static void bingNewThread(String ipAddress) {
		//System.out.println("run thread print main "+ipAddress);
		try {
			if(ipAddress != null && !"".equals(ipAddress) && AgentOptions.isDependIP()) {
				IPmap.bindIP(ipAddress);
			}
		}catch(Exception e) {
			System.err.println("HystrixIntercepter Exception : "+e.getMessage());
			e.printStackTrace();
		}
		return;
	}
	
	/*public static String getMainThreadIdentity() {
		return IPmap.getIpMap().get(Thread.currentThread());
	}*/

}
