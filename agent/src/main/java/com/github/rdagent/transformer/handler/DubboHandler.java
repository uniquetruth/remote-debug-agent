package com.github.rdagent.transformer.handler;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import com.github.rdagent.Constants;
import com.github.rdagent.transformer.AbstractHandler;
import com.github.rdagent.transformer.bytecode.DubboVisitor;

/**
 * For dubbo framework, identification binding
 * @author uniqueT
 *
 */
public class DubboHandler extends AbstractHandler {
	
	//dubbo 2.X
	private String dubboChannel2 = "com/alibaba/dubbo/remoting/exchange/support/header/HeaderExchangeHandler";
	//dubbo 3.X
	private String dubboChannel3 = "org/apache/dubbo/remoting/exchange/support/header/HeaderExchangeHandler";	
	
	@Override
	public boolean filterClassName(String className) {
		return dubboChannel2.equals(className) || dubboChannel3.equals(className);
	}

	@Override
	public byte[] process(String className, byte[] classfileBuffer) {
		ClassReader cr = new ClassReader(classfileBuffer);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        DubboVisitor tv = new DubboVisitor(Constants.asmApiVersion, cw);
        cr.accept(tv, ClassReader.EXPAND_FRAMES);
		return cw.toByteArray();
	}
	
	public int getPriority() {
		return 1;
	}

}
