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
	private String telnetChannel2 = "com/alibaba/dubbo/remoting/telnet/support/TelnetHandlerAdapter";
	//dubbo 3.X
	private String dubboChannel3 = "org/apache/dubbo/remoting/exchange/support/header/HeaderExchangeHandler";
	private String telnetChannel3 = "org/apache/dubbo/remoting/telnet/support/TelnetHandlerAdapter";

    private int dubboVersion = 0;
	
	@Override
	public boolean filterClassName(String className) {
        if(dubboChannel2.equals(className) || telnetChannel2.equals(className)){
            dubboVersion = 2;
            return true;
        }else if(dubboChannel3.equals(className) || telnetChannel3.equals(className)){
            dubboVersion = 3;
            return true;
        }
		return false;
	}

	@Override
	public byte[] process(String className, byte[] classfileBuffer, ClassLoader loader) {
		ClassReader cr = new ClassReader(classfileBuffer);
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		DubboVisitor tv = new DubboVisitor(Constants.asmApiVersion, cw, dubboVersion);
		cr.accept(tv, ClassReader.EXPAND_FRAMES);
		return cw.toByteArray();
	}
	
	public int getPriority() {
		return 1;
	}

}
