package com.github.rdagent.transformer.intercepter;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.rdagent.AgentOptions;
import com.github.rdagent.loader.Agent3rdPartyClassloader;

/**
 * intercept servlet's or struts filter's method which contain HttpServletRequest
 * get identification of request's invoker from HttpServletRequest and bind it to work thread
 * @author uniqueT
 *
 */
public class HttpReqInterceptor {
	
	private static Pattern p = Pattern.compile("^\\d{1,3}?\\.\\d{1,3}?\\.\\d{1,3}?\\.\\d{1,3}?$");
	
	public static void bindIP(Object request) {
		/* don't use HttpServletRequest directly here, the reason is written in DefaultServletAdatper
		 */
		try {
			String ipAddress = getCustomIdentity(request);
			bindIP(ipAddress);
		}catch(Exception e) {
			System.err.println("HttpReqInterceptor Exception : "+e.getMessage());
			e.printStackTrace();
		}
		return;
	}
	
	public static void bindIP(String ipAddress) {
		try {
			if (ipAddress != null && !"".equals(ipAddress) && AgentOptions.isDependIP()) {
				// check if thread has already been binded
				if (IPmap.getIpMap().containsKey(Thread.currentThread())) {
					// ip is the lowest priority identification
					String oldIp = IPmap.getIpMap().get(Thread.currentThread());
					Matcher m = p.matcher(oldIp);
					// if formal binding is an ip, use this identification to override it
					if (m.find()) {
						IPmap.bindIP(ipAddress);
					} else {
						// don't override identification, just add binding counter
						IPmap.increaseBindCount();
					}
				} else {
					IPmap.bindIP(ipAddress);
				}
			} else {
				IPmap.increaseBindCount();
			}
		} catch (Exception e) {
			System.err.println("HttpReqInterceptor Exception : " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	private static String getCustomIdentity(Object request) {
		//getInvokerFromThread
		List<String[]> handlers = AgentOptions.getCustomHandlers();
		StackTraceElement[] se = Thread.currentThread().getStackTrace();
		//put index here and sort it
		List<Integer> handlerIndexList = new ArrayList<Integer>();
		//the handler must be in the stack trace
		for(StackTraceElement e : se) {
			int tmp = indexOfHandlerList(handlers, e.getClassName());
			if(tmp > -1 && !handlerIndexList.contains(tmp)) {
				handlerIndexList.add(tmp);
			}
		}
		Collections.sort(handlerIndexList);
		for(int i : handlerIndexList) {
			try {
				//System.out.println("handlers.get(i)[1] = "+handlers.get(i)[1]);
				Class<?> c = Class.forName(handlers.get(i)[1], true, Agent3rdPartyClassloader.getClassloader());
				//DefaultServletAdatper d = new DefaultServletAdatper();
				Object defaultServletAdatper = c.newInstance();
				//d.setRequestObject(request)
				Method m = c.getMethod("setRequestObject", Object.class);
				m.invoke(defaultServletAdatper, request);
				//return d.extractIdentity()
				m = c.getMethod("extractIdentity", new Class[0]);
				return (String) m.invoke(defaultServletAdatper, new Object[0]);
			} catch (Exception e1) {
				//if any Exception thrown here, means c is not a sub class of DefaultServletAdatper
			}
		}
		
		throw new IllegalStateException("can not find valid ServletHandler");
	}
	
	private static int indexOfHandlerList(List<String[]> handlers, String name) {
		for(int i=0;i<handlers.size();i++) {
			if(handlers.get(i)[0].equals(name)) {
				return i;
			}
		}
		return -1;
	}

}
