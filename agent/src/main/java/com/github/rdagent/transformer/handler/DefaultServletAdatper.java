package com.github.rdagent.transformer.handler;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import com.github.rdagent.Constants;
import com.github.rdagent.annontation.SelfRegister;
import com.github.rdagent.transformer.AbstractHandler;
import com.github.rdagent.transformer.bytecode.ServletVisitor;

/**
 * the default implementation of HttpServlet injecting. you can extend this class 
 * if default implementation doesn't fit your require
 * @author uniqueT
 *
 */
@SelfRegister
public abstract class DefaultServletAdatper extends AbstractHandler {
	
	private Object request;
	private Class<?> reqClass = null;
	private Method getHeaderMethod = null;
	private Method getRemoteAddrMethod = null;
	private Method getQueryStringMethod = null;
	private Method getRequestURIMethod = null;
	
	public void setRequestObject(Object o) throws ClassNotFoundException {
		request = o;
		
		/*-----------------------------
		 *    Watch out here
		 *-----------------------------
		 * Some middleware use WebappClassloader to load servlet-api.jar, which means the input parameter is loaded by WebappClassloader.
		 * JVM always uses invoker's classloader to load class, so here it will try to load HttpServletRequest by this class's loader,
		 * which is AppClassloader(because agent jar is default added to classpath). As servlet-api.jar isn't in classpath and 
		 * WebappClassloader is the child loader of AppClassloader, the AppClassloader can't load HttpServletRequest. 
		 * So if we call HttpServletRequest's method directly here, An ClassNotFoundException may be thrown.
		 */
		
		//The currentThread is middleware's work thread, it must be able to load HttpServletRequest
		reqClass = Class.forName("javax.servlet.http.HttpServletRequest", true,
				Thread.currentThread().getContextClassLoader());
	}

	@Override
	public final boolean filterClassName(String className) {
		List<String> nameList = injectClassNameList();
		for(String name : nameList) {
			if(name.replace(".", "/").equals(className)) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * name of the method that contains HttpServletRequest
	 * @return default is "service"
	 */
	protected String getMethodName() {
		return "service";
	}
	
	/**
	 * index of HttpServletRequest parameter in {@link #getMethodName()} method 
	 * @return default is 1
	 */
	protected int getReqInext() {
		return 1;
	}

	@Override
	public final byte[] process(String _className, byte[] _classfileBuffer, ClassLoader loader) {
		ClassReader cr = new ClassReader(_classfileBuffer);
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		ServletVisitor tv = new ServletVisitor(Constants.asmApiVersion, cw, getMethodName(), getReqInext());
		cr.accept(tv, ClassReader.EXPAND_FRAMES);
		return cw.toByteArray();
	}
	
	public int getPriority() {
		return 10;
	}
	
	/**
	 * get identity from HttpServletRequest. Please use getHttpXXX method to implement this method
	 * @return default: use client ip as identity. can get origin ip from some forwarders' headers<br/>
	 * like "x-forwarded-for", "Proxy-Client-IP", "WL-Proxy-Client-IP"
	 * @throws Exception
	 */
	public String extractIdentity() throws Exception {
		// get ip from custom header firstly
		String ip = getHttpHeader(Constants.customIpHeader);
		if (ip != null && !"".equals(ip)) {
			// some clients add extra format for http header, try to have a filter
			Pattern p = Pattern.compile("(\\d{1,3}?\\.\\d{1,3}?\\.\\d{1,3}?\\.\\d{1,3}?)([^\\d]|$)");
			Matcher m = p.matcher(ip);
			if (m.find()) {
				// System.out.println("uniqueT debug +++ custom header found ");
				return m.group(1);
			} else {
				return ip;
			}
		}

		ip = getHttpHeader("x-forwarded-for");
		if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
			ip = getHttpHeader("Proxy-Client-IP");
		}
		if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
			ip = getHttpHeader("WL-Proxy-Client-IP");
		}
		if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
			ip = getHttpRemoteAddr();
		}
		// for multiple proxies
		if (ip != null && ip.length() > 15) { // "***.***.***.***".length() = 15
			if (ip.indexOf(",") > 0) {
				ip = ip.substring(0, ip.indexOf(","));
			}
		}
		return ip;
	}
	
	/**
	 * return the names of Classes that this Adapter injects.
	 * @return default is javax.servlet.http.HttpServlet
	 */
	public List<String> injectClassNameList(){
		List<String> nameList = new ArrayList<String>();
		nameList.add("javax.servlet.http.HttpServlet");
		return nameList;
	}
	
	/**
	 * use reflect to get a http header from HttpServletRequest.<br/>
	 * see also {@link javax.servlet.http.HttpServletRequest#getHeader}
	 * 
	 * @param headerName
	 * @return
	 * @throws NoSuchMethodException
	 * @throws SecurityException
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 * @throws InvocationTargetException
	 */
	protected String getHttpHeader(String headerName) throws NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		if(getHeaderMethod==null) {
			getHeaderMethod = reqClass.getMethod("getHeader", String.class);
		}
		return (String)getHeaderMethod.invoke(request, headerName);
	}
	
	/**
	 * use reflect to get client address from HttpServletRequest.<br/>
	 * see also {@link javax.servlet.http.HttpServletRequest#getRemoteAddr}
	 * 
	 * @return
	 * @throws NoSuchMethodException
	 * @throws SecurityException
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 * @throws InvocationTargetException
	 */
	protected String getHttpRemoteAddr() throws NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		if(getRemoteAddrMethod==null) {
			getRemoteAddrMethod = reqClass.getMethod("getRemoteAddr", new Class[0]);
		}
		return (String)getRemoteAddrMethod.invoke(request, new Object[0]);
	}
	
	/**
	 * use reflect to get query string in from HttpServletRequest.<br/>
	 * see also {@link javax.servlet.http.HttpServletRequest#getQueryString}
	 * 
	 * @return
	 * @throws NoSuchMethodException
	 * @throws SecurityException
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 * @throws InvocationTargetException
	 */
	protected String getHttpQueryString() throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
		if(getQueryStringMethod==null) {
			getQueryStringMethod = reqClass.getMethod("getQueryString", new Class[0]);
		}
		return (String)getQueryStringMethod.invoke(request, new Object[0]);
	}
	
	/**
	 * use reflect to get url in from HttpServletRequest.<br/>
	 * see also {@link javax.servlet.http.HttpServletRequest#getRequestURI}
	 * 
	 * @return
	 * @throws NoSuchMethodException
	 * @throws SecurityException
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 * @throws InvocationTargetException
	 */
	protected String getHttpRequestURI() throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
		if(getRequestURIMethod==null) {
			getRequestURIMethod = reqClass.getMethod("getRequestURI", new Class[0]);
		}
		return (String)getRequestURIMethod.invoke(request, new Object[0]);
	}

}
