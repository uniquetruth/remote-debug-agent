package com.github.rdagent;

import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;

import com.github.rdagent.server.AgentServer;
import com.github.rdagent.transformer.AsmTransformer;

/**
 * Contains agent's logic
 * @author uniqueT
 *
 */
public class RealAgent {
	
	public RealAgent() {
		
	}
	
	/**
	 * 1. Setup varies parameters
	 * 2. Add a AsmTransformer to Instrumentation
	 * 3. Register a shutdown Thread to store necessary data
	 * 4. If isHotPlugging set to true, retransform classes
	 * @param agentArgs
	 * @param inst
	 * @param jarDir
	 * @param isHotPlugging
	 * @throws UnmodifiableClassException
	 * @throws ClassNotFoundException
	 * @throws IllegalAccessException 
	 * @throws InstantiationException 
	 */
	public void doAgentEnter(String agentArgs, Instrumentation inst, String jarDir, boolean isHotPlugging) throws UnmodifiableClassException, ClassNotFoundException, InstantiationException, IllegalAccessException {
		AgentOptions.initOptions(agentArgs, jarDir);
		//register transformer
		AsmTransformer atf = new AsmTransformer();
		inst.addTransformer(atf, true);
		//start AgentServer
		AgentServer.start();
		//register JVM shutdown thread
		TraceRecorder t = new TraceRecorder(AgentOptions.getOutputDir());
		Runtime.getRuntime().addShutdownHook(t);
		
		AgentOptions.storeAgent(atf, inst);
		AgentOptions.storeSDHook(t);
		
		if(isHotPlugging) {
			inst.retransformClasses(atf.retransFilter(inst.getAllLoadedClasses()));
		}
	}

}
