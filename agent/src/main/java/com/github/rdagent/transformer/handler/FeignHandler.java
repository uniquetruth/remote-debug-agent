package com.github.rdagent.transformer.handler;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import com.github.rdagent.Constants;
import com.github.rdagent.transformer.AbstractHandler;
import com.github.rdagent.transformer.bytecode.FeignVisitor;

public class FeignHandler extends AbstractHandler {
	
	//add custom header when a RequestTemplate is creating
	private String feignRequest = "feign/RequestTemplate";

	@Override
	public boolean filterClassName(String className) {
		return feignRequest.equals(className);
	}

	@Override
	public byte[] process(String className, byte[] classfileBuffer, ClassLoader loader) {
		ClassReader cr = new ClassReader(classfileBuffer);
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		FeignVisitor fv = new FeignVisitor(Constants.asmApiVersion, cw);
		cr.accept(fv, ClassReader.EXPAND_FRAMES);
		return cw.toByteArray();
	}
	
	public int getPriority() {
		return 1;
	}

}
