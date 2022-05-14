package com.github.rdagent.transformer.intercepter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.github.rdagent.AgentOptions;
import com.github.rdagent.Constants;
import com.github.rdagent.annontation.BothUsing;
import com.github.rdagent.transformer.intercepter.vo.TraceVo;

@BothUsing
public class IPmap{
	
	//store ip and methods invoked by which
	private static Map<String, Set<String>> methodMap;
	//key=Ip&Method
	private static Map<String, boolean[]> methodCover;
	//store methods and their line number tables
	private static Map<String, ArrayList<Integer>> methodsLine;
	//store ip and thread's binding relation
	private static Map<Thread, String> ipMap;
	private static ThreadLocal<Integer> bindCount; //binding times of current thread
	//for invoking chain
	private static Map<String, LinkedList<TraceVo>> traceMap;
	//invoking depth
	private static Map<String, Integer> traceSwitchMap;
	
	static {
		methodMap = new ConcurrentHashMap<String, Set<String>>(32);
		methodMap.put(Constants.virtualIp, Collections.synchronizedSet(new HashSet<String>(4096)));
		methodCover = new ConcurrentHashMap<String, boolean[]>(4096);
		methodsLine = new ConcurrentHashMap<String, ArrayList<Integer>>(4096);
		ipMap = new ConcurrentHashMap<Thread, String>(128);
		bindCount = new ThreadLocal<Integer>() {
			protected Integer initialValue() {
				return 0;
			}
		};
		traceMap = new ConcurrentHashMap<String, LinkedList<TraceVo>>();
		traceSwitchMap = new ConcurrentHashMap<String, Integer>();
	}
	
	public static void bindIP(String ip){
		if(!ipMap.containsKey(Thread.currentThread())) {
			ipMap.put(Thread.currentThread(), ip);
			bindCount.set(1);
		}else {
			increaseBindCount();
		}
		if(!methodMap.containsKey(ip)) {
			methodMap.put(ip, Collections.synchronizedSet(new HashSet<String>(4096)));
		}
		return ;
	}
	
	public static void unbindIP() {
		if(bindCount.get() > 1) {
			decreaseBindCount();
		}else {
			ipMap.remove(Thread.currentThread());
		}
	}
	
	//for intercepters invoking
	public static void increaseBindCount() {
		bindCount.set(bindCount.get()+1);
	}
	public static void decreaseBindCount(){
		bindCount.set(bindCount.get()-1);
	}
	
	public static boolean hasAddress() {
		//System.out.println("hasAddress map size : "+ipMap.size());
		//System.out.println("hasAddress : "+Thread.currentThread());
		//System.out.println("hasAddress ip : "+ipMap.get(Thread.currentThread()));
		return ipMap.containsKey(Thread.currentThread());
	}
	
	//invoke when method AppIntercepter.onMethodIn
	public static void stroeMethod(String method) {
		String ip = AgentOptions.isDependIP() ? ipMap.get(Thread.currentThread()) : Constants.virtualIp;
		Set<String> l = methodMap.get(ip);
		l.add(method);
	}
	
	//invoke when AppIntercepter.onMethodOut
	public static void stroeMethod(String method, boolean[] coverage) {
		String ip = AgentOptions.isDependIP() ? ipMap.get(Thread.currentThread()) : Constants.virtualIp;
		String key=ip+"&"+method;
		boolean[] oldCover = methodCover.get(key);
		if(oldCover==null) {
			methodCover.put(key, coverage);
		}else {
			for(int i=0;i<oldCover.length;i++) {
				oldCover[i] = oldCover[i] || coverage[i];
			}
		}
	}
	
	public static Map<String, Set<String>> getMethodMap(){
		return methodMap;
	}
	
	public static Map<String, boolean[]> getCoverMap(){
		return methodCover;
	}
	
	public static Map<Thread, String> getIpMap() {
		return ipMap;
	}
	
	public static void recordMethodLine(Map<String, ArrayList<Integer>> ml) {
		methodsLine.putAll(ml);
	}
	
	public static Map<String, ArrayList<Integer>> getMethodLineMap() {
		return methodsLine;
	}
	
	public static void clean() {
		Iterator<Map.Entry<String, Set<String>>> it = methodMap.entrySet().iterator();
		while(it.hasNext()) {
			it.next().getValue().clear();
		}
		methodCover = new ConcurrentHashMap<String, boolean[]>(4096);
		return;
	}
	
	public static void clean(String ip) {
		methodMap.put(ip, Collections.synchronizedSet(new HashSet<String>(4096)));
		Iterator<Map.Entry<String, boolean[]>> it = methodCover.entrySet().iterator();
		while(it.hasNext()) {
			Map.Entry<String, boolean[]> entry = it.next();
			if(entry.getKey().contains(ip)) {
				it.remove();
			}
		}
		return;
	}
	
	public static void startTracing(String ip) {
		if(!traceMap.containsKey(ip)) {
			traceMap.put(ip, new LinkedList<TraceVo>());
		}
		if(traceSwitchMap.get(ip)==null) {
			traceSwitchMap.put(ip, 0);
		}else if(traceSwitchMap.get(ip) < 0) {
			traceSwitchMap.put(ip, 0);
		}
	}
	
	public static void stopTracing(String ip) {
		traceSwitchMap.put(ip, -999);
	}
	
	public static void cleanTracing(String ip) {
		if(traceMap.containsKey(ip)) {
			traceMap.put(ip, new LinkedList<TraceVo>());
		}
	}
	
	public static boolean canTrace() {
		String ip ;
		if(AgentOptions.isDependIP()) {
			ip = ipMap.get(Thread.currentThread());
		}else {
			ip = Constants.virtualIp;
		}
		if(traceSwitchMap.get(ip)!=null) {
			return traceSwitchMap.get(ip)>-1;
		}else {
			return false;
		}
	}

	//invoke when method AppIntercepter.onMethodIn
	public static void traceMethod(String method, Object[] args) {
		TraceVo vo = new TraceVo();
		vo.setMethod(method);
		String[] parameStrs = new String[args.length];
		for(int i=0;i<args.length;i++) {
			parameStrs[i] = obj2Str(args[i], 0);
		}
		vo.setParameters(parameStrs);
		
		String ip ;
		if(AgentOptions.isDependIP()) {
			ip = ipMap.get(Thread.currentThread());
		}else {
			ip = Constants.virtualIp;
		}
		int deep = traceSwitchMap.get(ip);
		vo.setDeep(deep);
		deep++;
		traceSwitchMap.put(ip, deep);
		
		LinkedList<TraceVo> list = traceMap.get(ip);
		vo.setStartTime(System.currentTimeMillis());
		list.add(vo);
		if(list.size() > AgentOptions.getTraceMax()) {
			//dequeue 10 elements once
			for(int i=0;i<10;i++) {
				list.poll();
			}
		}
	}

	//invoke when method AppIntercepter.onMethodOut
	public static void traceMethod(String methodName, Object returnValue) {
		String ip ;
		if(AgentOptions.isDependIP()) {
			ip = ipMap.get(Thread.currentThread());
		}else {
			ip = Constants.virtualIp;
		}
		List<TraceVo> list = traceMap.get(ip);
		//stack data structure
		for(int i=list.size()-1;i>=0;i--) {
			TraceVo vo = list.get(i);
			if(methodName.equals(vo.getMethod()) && vo.getReturnValue()==null) {
				vo.setEndTime(System.currentTimeMillis());
				vo.setReturnValue(obj2Str(returnValue, 0));
				break;
			}
		}
		int deep = traceSwitchMap.get(ip);
		deep--;
		traceSwitchMap.put(ip, deep);
	}
	
	//can't store original object in TraceVo, need to only record the value of parameters in this "timestamp"
	//use depth to prevent StackOverflowExceprion
	@SuppressWarnings("rawtypes")
	private static String obj2Str(Object param, int depth) {
		depth++;
		if(depth>3) {
			return param.toString();
		}
		StringBuilder s = new StringBuilder("");
		if(param == null) {
			s.append("null");
		}else if(param.getClass().isArray()) {
			Object[] oarray = null;
			try {
				oarray = (Object[]) param;
			}catch(ClassCastException e) {
				//primitive array can't be cast to Object array
				return basicTypeArray2Str(param);
			}
			s.append("[");
			for(Object o : oarray) {
				s.append(obj2Str(o, depth)).append(", ");
			}
			if(s.length()>=2) {
				s.setLength(s.length()-2);
			}
			s.append("]");
		}else if(param instanceof Iterable) {
			s.append("[");
			Iterator it = ((Iterable)param).iterator();
			while(it.hasNext()) {
				s.append(obj2Str(it.next(), depth)).append(", ");
			}
			if(s.length()>=2) {
				s.setLength(s.length()-2);
			}
			s.append("]");
		}else if(param instanceof Exception) {
			s.append("Exception:").append(param.toString());
		}else if("".equals(param)) {
			s.append("\"\"");
		}else {
			s.append(param.toString());
		}
		//depth--;
		return s.toString();
	}

	private static String basicTypeArray2Str(Object param) {
		StringBuilder s = new StringBuilder("[");
		String className = param.getClass().getName();
		if("[I".equals(className)) {
			int[] tmp = (int[])param;
			for(int x : tmp) {
				s.append(x).append(", ");
			}
		}else if("[Z".equals(className)) {
			boolean[] tmp = (boolean[])param;
			for(boolean x : tmp) {
				s.append(x).append(", ");
			}
		}else if("[F".equals(className)) {
			float[] tmp = (float[])param;
			for(float x : tmp) {
				s.append(x).append(", ");
			}
		}else if("[D".equals(className)) {
			double[] tmp = (double[])param;
			for(double x : tmp) {
				s.append(x).append(", ");
			}
		}else if("[J".equals(className)) {
			long[] tmp = (long[])param;
			for(long x : tmp) {
				s.append(x).append(", ");
			}
		}else if("[S".equals(className)) {
			short[] tmp = (short[])param;
			for(short x : tmp) {
				s.append(x).append(", ");
			}
		}else if("[B".equals(className)) {
			byte[] tmp = (byte[])param;
			if(tmp.length < 30) {
				for(byte x : tmp) {
					s.append(x).append(", ");
				}
			}else {
				s.append("large byte stream, ");
			}
		}else if("[C".equals(className)) {
			char[] tmp = (char[])param;
			for(char x : tmp) {
				s.append(x).append(", ");
			}
		}
		if(s.length()>2) {
			s.setLength(s.length()-2);
		}
		s.append("]");
		return s.toString();
	}

	public static Map<String, LinkedList<TraceVo>> getTraceMap() {
		return traceMap;
	}

}
