package com.github.rdagent.transformer.handler;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import com.github.rdagent.Constants;
import com.github.rdagent.transformer.AbstractHandler;
import com.github.rdagent.transformer.bytecode.DubboClientVisitor;

public class DubboClientHandler extends AbstractHandler {

	private String clientChannel2 = "com/alibaba/dubbo/remoting/exchange/support/header/HeaderExchangeClient";
    private String clientChannel3 = "org/apache/dubbo/remoting/exchange/support/header/HeaderExchangeClient";
	private int dubboVersion = 0;
	
	@Override
	public boolean filterClassName(String className) {
		if(clientChannel2.equals(className)){
			dubboVersion = 2;
			return true;
		}else if(clientChannel3.equals(className)){
			dubboVersion = 3;
			return true;
		}
		return false;
	}

	@Override
	public byte[] process(String className, byte[] classfileBuffer, ClassLoader loader) {
		ClassReader cr = new ClassReader(classfileBuffer);
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		DubboClientVisitor tv = new DubboClientVisitor(Constants.asmApiVersion, cw, dubboVersion);
		cr.accept(tv, ClassReader.EXPAND_FRAMES);
		return cw.toByteArray();
	}
	
	public int getPriority() {
		return 1;
	}
    
}
