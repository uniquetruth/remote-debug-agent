package com.github.rdagent;

import org.objectweb.asm.Opcodes;

public class Constants {
	
	/**
	 * custom http header. Used to identify client or transport invoke chain
	 */
	public static final String customIpHeader = "test-coverage-sip";
	
	/**
	 * a virtual identification
	 */
	public static final String virtualIp = "999.999.999.999";
	
	/**
	 * version of asm api. 5 is pretty ok, I don't know much.
	 */
	public static final int asmApiVersion = Opcodes.ASM5;
	
	/**
	 * max length of tracing list. used to limit memory cost
	 */
	public static final int TraceMaxLength = 65535;
	
	/**
	 * for local program, use this system property to turn on debug record
	 */
	public static final String procTraceSwitch = "rdagent.trace";
	/**
	 * for local program, specify if dump method running duration
	 */
	public static final String procTimeSwitch = "rdagent.trace.time";
	/**
	 * for local program, specify if dump method coverage data
	 */
	public static final String procLinesSwitch = "rdagent.trace.lines";
	/**
	 * for local program, specify if dump method coverage data
	 */
	public static final String procCoverageSwitch = "rdagent.coverage";

}
