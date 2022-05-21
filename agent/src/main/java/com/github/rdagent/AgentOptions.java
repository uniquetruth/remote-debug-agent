package com.github.rdagent;

import java.io.File;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.rdagent.annontation.BothUsing;
import com.github.rdagent.transformer.intercepter.IPmap;

/**
 * store all agent parameters
 * @author uniqueT
 *
 */
@BothUsing
public class AgentOptions {
	
	//debug scopes
	private static List<String> scopes = new ArrayList<String>();
	//exclude scopes
	private static List<String> exScopes = new ArrayList<String>();
	//output directory
	private static String outputDir;
	//agent's service port, if 0, do not provide service
	private static int apiPort = 0;
	//if true, only trace binded thread. If false, trace every thread. Default is true
	private static boolean dependIP = true;
	//directory which agent jar is in.
	private static String jarDir;
	//agent parameters
	private static String commdArgs;
	//limit of trace list, prevent oom
	private static int traceMax = Constants.TraceMaxLength;
	//for local java program using
	private static boolean procTrace = false;
	private static boolean procTraceTime = false;
	private static boolean procTraceLines = false;
	private static boolean procCoverage = false;
	
	//for hot-plugging
	private static ClassFileTransformer cft;
	private static Instrumentation inst;
	private static Thread shutdownHook;
	
	//customHandlers[0]:injected class name, customHandlers[1] handler's class name
	private static List<String[]> customHandlers = new ArrayList<String[]>();
	
	public static void initOptions(String _commdArgs, String _jarDir) {
		commdArgs = _commdArgs;
		jarDir = _jarDir;
		String scpoeStr = getParam(commdArgs, "includes=(.+?)(,|$)");
		if(scpoeStr == null) {
			throw new IllegalArgumentException("includes must not be null");
		}
		String[] sl = scpoeStr.split(":");
		for(String temp : sl) {
			scopes.add(temp);
		}
		
		String exScpoeStr = getParam(commdArgs, "excludes=(.+?)(,|$)");
		if(exScpoeStr != null) {
			String[] exsl = exScpoeStr.split(":");
			for(String temp : exsl) {
				exScopes.add(temp);
			}
		}
		//some default excluding
		exScopes.add("com.github.uniqueT.rdagent");
		
		outputDir = getParam(commdArgs, "outputdir=(.+?)(,|$)");
		if(outputDir == null) { //use directory of agent jar as default
			outputDir = jarDir;
		}else if(!outputDir.startsWith(File.separator) && !outputDir.contains(":")) { //relative dir
			outputDir = jarDir + outputDir;
		}
		
		try {
			apiPort = Integer.parseInt(getParam(commdArgs, "apiport=(.+?)(,|$)"));
		}catch(NumberFormatException e) {
			apiPort = 0;
		}
		
		String depIP = getParam(commdArgs, "dependIP=(.+?)(,|$)");
		if("false".equals(depIP)){
			dependIP = false;
		}
		
		try {
			String traceConfig = System.getProperty("test.coverage.traceMax");
			if(traceConfig==null || traceConfig.equals("")) {
				traceConfig = getParam(commdArgs, "traceMax=(.+?)(,|$)");
			}
			if(traceConfig!=null) {
				traceMax = Integer.parseInt(traceConfig);
			}
		}catch(NumberFormatException e) {
			System.out.println("test.coverage.traceMax is not a number");
		}
		
		if("true".equals(System.getProperty(Constants.procTraceSwitch))) {
			IPmap.startTracing(Constants.virtualIp);
			procTrace = true;
		}
		if("true".equals(System.getProperty(Constants.procTimeSwitch))) {
			procTraceTime = true;
		}
		if("true".equals(System.getProperty(Constants.procLinesSwitch))) {
			procTraceLines = true;
		}
		if("true".equals(System.getProperty(Constants.procCoverageSwitch))) {
			procCoverage = true;
		}
	}
	
	public static List<String> getScopes(){
		return scopes;
	}
	
	public static List<String> getExScopes(){
		return exScopes;
	}
	
	public static String getOutputDir() {
		return outputDir;
	}
	
	public static int getApiPort() {
		return apiPort;
	}
	
	private static String getParam(String agentArgs, String pattern) {
		Pattern p = Pattern.compile(pattern);
		Matcher m = p.matcher(agentArgs);
		if(m.find()) {
			return m.group(1);
		}
		return null;
	}

	public static boolean isDependIP() {
		return dependIP;
	}
	
	public static boolean getDependIp() {
		return dependIP;
	}
	public static void setDependIp(boolean dependIP) {
		AgentOptions.dependIP = dependIP;
	}
	
	public static int getTraceMax() {
		return traceMax;
	}
	
	public static boolean getProcTrace() {
		return procTrace;
	}
	
	public static boolean getProcTraceTime() {
		return procTraceTime;
	}
	
	public static boolean getProcTraceLines() {
		return procTraceLines;
	}
	
	public static boolean getProcCoverage() {
		return procCoverage;
	}
	
	public static void storeAgent(ClassFileTransformer _cft, Instrumentation _inst) {
		cft = _cft;
		inst = _inst;
	}
	
	public static ClassFileTransformer getTransformer() {
		return cft;
	}
	
	public static Instrumentation getInstrumentation() {
		return inst;
	}

	public static void storeSDHook(Thread t) {
		shutdownHook = t;
	}
	
	public static Thread getSDHook() {
		return shutdownHook;
	}
	
	public static void storeHandlerNames(List<String> injectClassNames, String handlerClassName) {
		out: for (String name : injectClassNames) {
			for (String customName[] : customHandlers) {
				if (customName[0].equals(name)) {
					continue out;
				}
			}
			String[] s = new String[2];
			s[0] = name;
			s[1] = handlerClassName;
			customHandlers.add(s);
		}
	}
	
	public static List<String[]> getCustomHandlers(){
		return customHandlers;
	}

}
