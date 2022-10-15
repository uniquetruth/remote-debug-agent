package com.github.rdagent.transformer.handler;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import com.github.rdagent.Constants;
import com.github.rdagent.transformer.AbstractHandler;
import com.github.rdagent.transformer.bytecode.DubboClientVisitor;

public class DubboClientHandler extends AbstractHandler {

    private String clientChannel = "org/apache/dubbo/remoting/exchange/support/header/HeaderExchangeClient";
	
	@Override
	public boolean filterClassName(String className) {
		return clientChannel.equals(className);
	}

	@Override
	public byte[] process(String className, byte[] classfileBuffer, ClassLoader loader) {
		ClassReader cr = new ClassReader(classfileBuffer);
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		DubboClientVisitor tv = new DubboClientVisitor(Constants.asmApiVersion, cw);
		cr.accept(tv, ClassReader.EXPAND_FRAMES);
		return cw.toByteArray();
	}
	
	public int getPriority() {
		return 1;
	}
    
}
