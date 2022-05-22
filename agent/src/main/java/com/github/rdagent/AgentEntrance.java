package com.github.rdagent;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import com.github.rdagent.loader.Agent3rdPartyClassloader;

/**
 * A standard java agent entrace
 * @author uniqueT
 *
 */
public class AgentEntrance {

	/**
	 * Agent program starts here, just call doPremain with isHotPlugging is false.<br/>
	 * See also {@link #doPremain(String agentArgs, Instrumentation inst, boolean isHotPlugging)}
	 * @param agentArgs
	 * @param inst
	 * @throws ClassNotFoundException
	 */
	public static void premain(String agentArgs, Instrumentation inst) throws ClassNotFoundException{
		doPremain(agentArgs, inst, false);
	}
	
	/**
	 * Just call doPremain with isHotPlugging is true.<br/>
	 * See also {@link #premain(String agentArgs, Instrumentation inst)}
	 * @param agentArgs
	 * @param inst
	 * @throws ClassNotFoundException
	 */
	public static void agentmain(String agentArgs, Instrumentation inst) throws ClassNotFoundException {
		doPremain(agentArgs, inst, true);
	}
	
	/**
	 * Just setup the custom classloader, and invoke RealAgent.doAgentEnter<br/><br/>
	 * 
	 * We don't want compatibility problems in agent classes and application classes. Normally java middlewares have class isolation mechanics, 
	 * but this agent is not only designed for program runs in middleware. I hope all java program can use this agent so I have to write a 
	 * custom classloader to load RealAgent and do the rest works.<br/>
	 * See also {@link com.github.rdagent.loader.Agent3rdPartyClassloader}
	 * @param agentArgs
	 * @param inst
	 * @param isHotPlugging
	 * @throws ClassNotFoundException
	 */
	protected static void doPremain(String agentArgs, Instrumentation inst, boolean isHotPlugging) throws ClassNotFoundException {
		System.out.println("AgentEntrance.premain() was called. +++ agentArgs:" + agentArgs);
		
		//get the path of agent's jar
		ClassLoader cl = AgentEntrance.class.getClassLoader(); //may be AppClassLoader or null
		String path = null;
		if(cl ==null) {
			path = ClassLoader.getSystemClassLoader().getResource("com/github/rdagent/AgentEntrance.class").getPath();
		}else {
			path = cl.getResource("com/github/rdagent/AgentEntrance.class").getPath();
		}
		//System.out.println("path="+path);
		path = path.substring(5, path.indexOf(".jar!")+4);
		
		//prepare an Agent3rdPartyClassloader
		Agent3rdPartyClassloader.init(path, cl);
		//let other class be loaded by Agent3rdPartyClassloader
		Class<?> c = Agent3rdPartyClassloader.getClassloader().loadClass("com.github.rdagent.RealAgent");
		try {
			Method m = c.getMethod("doAgentEnter", String.class, Instrumentation.class, String.class, boolean.class);
			m.invoke(c.newInstance(), agentArgs, inst, path.substring(0, path.lastIndexOf("/")), isHotPlugging);
		} catch (Exception e) {
			throw new RuntimeException("RealAgent reflect exception.", e);
		}
	}

}
