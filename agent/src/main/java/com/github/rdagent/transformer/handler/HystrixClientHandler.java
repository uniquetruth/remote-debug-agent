package com.github.rdagent.transformer.handler;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import com.github.rdagent.Constants;
import com.github.rdagent.transformer.AbstractHandler;
import com.github.rdagent.transformer.bytecode.HystrixClientVisitor;

public class HystrixClientHandler extends AbstractHandler {
	
	//Hystrix client send request asynchronous, it runs in a different thread from business thread
	private String hystrixInvocation = "feign/hystrix/HystrixInvocationHandler$1";

	@Override
	public boolean filterClassName(String className) {
		return hystrixInvocation.equals(className);
	}

	@Override
	public byte[] process(String className, byte[] classfileBuffer) {
		ClassReader cr = new ClassReader(classfileBuffer);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        HystrixClientVisitor visitor = new HystrixClientVisitor(Constants.asmApiVersion, cw);
        cr.accept(visitor, ClassReader.EXPAND_FRAMES);
        return cw.toByteArray();
	}
	
	public int getPriority() {
		return 1;
	}

}
