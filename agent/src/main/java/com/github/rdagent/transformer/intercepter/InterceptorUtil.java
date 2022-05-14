package com.github.rdagent.transformer.intercepter;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.rdagent.Constants;

public class InterceptorUtil {
	
	public static String getHttpIp(Object request) throws Exception {
		/*-----------------------------
		 *    Watch out here
		 *-----------------------------
		 * Some middleware use WebappClassloader to load servlet-api.jar, which means the input parameter is loaded by WebappClassloader.
		 * JVM always use invoker's classloader to load class, so here it will try to load HttpServletRequest by this class's loader,
		 * which is AppClassloader(because agent jar is default added to classpath). As servlet-api.jar isn't in classpath and 
		 * WebappClassloader is the child loader of AppClassloader, the AppClassloader can't load HttpServletRequest. 
		 * So if we call HttpServletRequest's method directly here, An ClassNotFoundException may be thrown.
		 */
		
		//The currentThread is middleware's work thread, it must be able to load HttpServletRequest
		Class<?> reqClass = Class.forName("javax.servlet.http.HttpServletRequest", true,
				Thread.currentThread().getContextClassLoader());
		
		//get ip from custom header firstly
		Method method = reqClass.getMethod("getHeader", String.class);
		String ip = (String)method.invoke(request, Constants.customIpHeader);
		//String ip = request.getHeader(Constants.customIpHeader);
		
		if(ip != null && !"".equals(ip)) {
			//some clients add extra format for http header, try to have a filter
			Pattern p = Pattern.compile("(\\d{1,3}?\\.\\d{1,3}?\\.\\d{1,3}?\\.\\d{1,3}?)([^\\d]|$)");
			Matcher m = p.matcher(ip);
			if(m.find()) {
				//System.out.println("luanfei debug +++ custom header found ");
				return m.group(1);
			}else {
				return ip;
			}
		}
		//ip = request.getHeader("x-forwarded-for");
		ip =(String)method.invoke(request, "x-forwarded-for");
		if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
			//ip = request.getHeader("Proxy-Client-IP");
			ip =(String)method.invoke(request, "Proxy-Client-IP");
		}
		if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
			//ip = request.getHeader("WL-Proxy-Client-IP");
			ip =(String)method.invoke(request, "WL-Proxy-Client-IP");
		}
		if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
			//ip = request.getRemoteAddr();
			method = reqClass.getMethod("getRemoteAddr", new Class[0]);
			ip =(String)method.invoke(request, new Object[0]);
		}
		//for multiple proxies
		if(ip!=null && ip.length()>15){ //"***.***.***.***".length() = 15
			if(ip.indexOf(",")>0){
				ip = ip.substring(0, ip.indexOf(","));
			}
		}
		return ip;
	}
	
	//get client ip from dubbo framework
	public static String getDubboIp(Object channel) throws Exception {
		ClassLoader threadCl = Thread.currentThread().getContextClassLoader();
		Class<?> dubboChannelClass = null;
		try {
			//dubbo 3.X
			dubboChannelClass = Class.forName("org.apache.dubbo.remoting.Channel", true, threadCl);
		}catch(ClassNotFoundException e) {
			//dubbo 2.X
			dubboChannelClass = Class.forName("com.alibaba.dubbo.remoting.Channel", true, threadCl);
		}
		//InetSocketAddress address = channel.getRemoteAddress();
		Method method = dubboChannelClass.getMethod("getRemoteAddress", new Class[0]);
		InetSocketAddress address = (InetSocketAddress) method.invoke(channel, new Object[0]);
		return address.getAddress().getHostAddress();
	}

}
