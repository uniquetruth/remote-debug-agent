package com.github.rdagent.transformer.intercepter;

import java.util.ArrayList;

import com.github.rdagent.AgentOptions;

/**
 * intercept methods of classes which are in the agent includes scope
 * @author uniqueT
 *
 */
public class AppIntercepter{
	
	/**
	 * The exception which this thread is dealing with
	 */
	private static ThreadLocal<StackTraceElement[]> stack = new ThreadLocal<StackTraceElement[]>();
	
	private static ThreadLocal<ArrayList<Integer>> skipStackIndex = new ThreadLocal<ArrayList<Integer>>() {
		protected ArrayList<Integer> initialValue() {
			return new ArrayList<Integer>();
		}
	};

	/**
	 * record invoking info, parameters' value
	 * @param methodName
	 * @param args
	 */
	public static void onMethodIn(String methodName, Object[] args) {
		//System.out.println("cl in AppIntercepter : "+AppIntercepter.class.getClassLoader());
		try {
			//only record binded thread
			if(IPmap.hasAddress() || !AgentOptions.isDependIP()) {
				//check trace switch
				IPmap.stroeMethod(methodName);
				if(IPmap.canTrace()) {
					IPmap.traceMethod(methodName, args);
				}
			}
		}catch(Exception e) {
			System.err.println("AppInterceptor Exception : "+e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * record return value or exception, line coverage
	 * @param returnValue return value or exception
	 * @param methodName
	 * @param probes
	 */
	public static void onMethodOut(Object returnValue, String methodName, boolean[] probes) {
		try {
			if(returnValue instanceof Exception) { //adjust probe
				//if exception thrown by deeper method has been progressed by this method, and method itself throws another exception 
				//need to adjust probes twice
				if(stack.get() != null && stack.get() != returnValue) {  
					adjustProbes(stack.get(), methodName, probes);
					StackTraceElement[] earray = ((Exception)returnValue).getStackTrace();
					//store new exception into thread local var
					stack.set(earray);
					skipStackIndex.set(new ArrayList<Integer>());
					adjustProbes(earray, methodName, probes);
				}else if(stack.get() != null){  //if returnValue is current exception
					adjustProbes(stack.get(), methodName, probes);
				}else {  //a new exception, before here this thread hadn't been in exception progressing
					StackTraceElement[] earray = ((Exception)returnValue).getStackTrace();
					adjustProbes(earray, methodName, probes);
					//store new exception into thread local var
					stack.set(earray);
				}
			}else if(stack.get() != null){
				//if return value is not a exception, but there is an exception in thread local var
				//means this method progressed the exception in its own catch block, and here we have to adjust probes the last time
				adjustProbes(stack.get(), methodName, probes);
				stack.set(null);
				skipStackIndex.set(new ArrayList<Integer>());
			}
			//only record binded thread
			if(IPmap.hasAddress() || !AgentOptions.isDependIP()) {
				IPmap.stroeMethod(methodName, probes);
				if(IPmap.canTrace()) {
					IPmap.traceMethod(methodName, returnValue);
				}
			}
		}catch(Exception e) {
			System.err.println("AppInterceptor Exception : "+e.getMessage());
			e.printStackTrace();
		}
	}
	
	/**
	 * adjust probes array。<br/>
	 * the exception may not be thrown by this method directly, so we have to locate this method's line when occurred exception
	 * @param earray
	 * @param methodName
	 * @param probes
	 */
	private static void adjustProbes(StackTraceElement[] earray, String methodName, boolean[] probes) {
		StackTraceElement se = null;
		//locate this method's line
		int i=0;
		for(i=0;i<earray.length;i++) {
			if(skipStackIndex.get().contains(i)) {
				continue;
			}
			se = earray[i];
			String eMethod = se.getClassName()+"."+se.getMethodName();
			if(methodName.contains(eMethod)) {
				break;
			}
		}
		//do nothing when can not find the line
		if(i==earray.length) {
			return;
		}
		int exceptionLine = se.getLineNumber();
		//System.out.println("exception line =" +exceptionLine);
		skipStackIndex.get().add(new Integer(i));
		
		ArrayList<Integer> methodLines = IPmap.getMethodLineMap().get(methodName);
		for(i=0;i<methodLines.size();i++) {
			if(Math.abs(methodLines.get(i))==exceptionLine) {
				break;
			}
		}
		//line number table may not be compilie into class file
		if(i < methodLines.size()) {
			probes[i*2] = true;
			probes[i*2+1] = true;
		}
	}

}
