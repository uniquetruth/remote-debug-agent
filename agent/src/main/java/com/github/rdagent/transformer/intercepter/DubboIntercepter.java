package com.github.rdagent.transformer.intercepter;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;

import com.github.rdagent.AgentOptions;
import com.github.rdagent.Constants;

/**
 * intercept dubbo framework, get invoker's ip from Channel instance and and bind it to work thread
 * @author uniqueT
 *
 */
public class DubboIntercepter {
	
	public static void bindIP(Object channel, Object data) {
		try {
			String ip = getDubboIp(channel, data);
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
	private static String getDubboIp(Object channel, Object data) throws Exception {
		System.out.println("luanfei +++ debug request data is: " + data.getClass());
		ClassLoader threadCl = Thread.currentThread().getContextClassLoader();
		Class<?> dubboChannelClass = null;
		Class<?> requestData = null;
		try {
			if(data!=null){
			    requestData = Class.forName("org.apache.dubbo.rpc.RpcInvocation", true, threadCl);
			    Method m = requestData.getMethod("getAttachment", String.class);
			    String customIP = (String) m.invoke(data, Constants.customIpHeader);
			    System.out.println("luanfei +++ debug custom ip is: " + customIP);
		    }

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
