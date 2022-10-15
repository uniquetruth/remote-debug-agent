package com.github.rdagent.transformer.intercepter;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;

import com.github.rdagent.AgentOptions;

/**
 * intercept dubbo framework, get invoker's ip from Channel instance and and bind it to work thread
 * @author uniqueT
 *
 */
public class DubboIntercepter {
	
	public static void bindIP(Object channel) {
		try {
			String ip = getDubboIp(channel);
			System.out.println("dubbo provider get ip : "+ip);
			
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
	
	// get client ip from dubbo framework
	private static String getDubboIp(Object channel) throws Exception {
		ClassLoader threadCl = Thread.currentThread().getContextClassLoader();
		Class<?> dubboChannelClass = null;
		try {
			// dubbo 3.X
			dubboChannelClass = Class.forName("org.apache.dubbo.remoting.Channel", true, threadCl);
		} catch (ClassNotFoundException e) {
			// dubbo 2.X
			dubboChannelClass = Class.forName("com.alibaba.dubbo.remoting.Channel", true, threadCl);
		}
		// InetSocketAddress address = channel.getRemoteAddress();
		Method method = dubboChannelClass.getMethod("getRemoteAddress", new Class[0]);
		InetSocketAddress address = (InetSocketAddress) method.invoke(channel, new Object[0]);
		return address.getAddress().getHostAddress();
	}

}
