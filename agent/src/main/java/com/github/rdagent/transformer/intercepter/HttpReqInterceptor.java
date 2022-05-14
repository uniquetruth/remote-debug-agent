package com.github.rdagent.transformer.intercepter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.rdagent.AgentOptions;

/**
 * intercept servlet's or struts filter's method which contain HttpServletRequest
 * get identification of request's invoker from HttpServletRequest and bind it to work thread
 * @author uniqueT
 *
 */
public class HttpReqInterceptor {
	
	private static Pattern p = Pattern.compile("^\\d{1,3}?\\.\\d{1,3}?\\.\\d{1,3}?\\.\\d{1,3}?$");
	
	public static void bindIP(Object request) {
		/* don't user HttpServletRequest directly here, reason is written in InterceptorUtil
		 */
		try {
			String ipAddress = InterceptorUtil.getHttpIp(request);
			if(ipAddress != null && !"".equals(ipAddress) && AgentOptions.isDependIP()) {
				//check if thread has already been binded
				if(IPmap.getIpMap().containsKey(Thread.currentThread())){
					//ip is the lowest priority identification
					String oldIp = IPmap.getIpMap().get(Thread.currentThread());
					Matcher m = p.matcher(oldIp);
					//if formal binding is an ip, use this identification to override it
					if(m.find()) {
						IPmap.bindIP(ipAddress);
					}else {
						//don't override identification, just add binding counter
						IPmap.increaseBindCount(); 
					}
				}else {
					IPmap.bindIP(ipAddress);
				}
			}else {
				IPmap.increaseBindCount();
			}
		}catch(Exception e) {
			System.err.println("HttpReqInterceptor Exception : "+e.getMessage());
			e.printStackTrace();
		}
		return;
	}

}
