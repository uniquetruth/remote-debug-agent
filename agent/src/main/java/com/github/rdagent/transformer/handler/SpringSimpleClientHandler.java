package com.github.rdagent.transformer.handler;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import com.github.rdagent.Constants;
import com.github.rdagent.transformer.AbstractHandler;
import com.github.rdagent.transformer.bytecode.SpringSimpleClientVisitor;

public class SpringSimpleClientHandler extends AbstractHandler {
	
	//spring-web自己的http客户端，实际用的是java.net.HttpURLConnection，调用微服务接口的传统服务用的是这个类
	private String springHTTPClient = "org/springframework/http/client/SimpleBufferingClientHttpRequest";

	@Override
	public boolean filterClassName(String className) {
		return springHTTPClient.equals(className);
	}

	@Override
	public byte[] process(String className, byte[] classfileBuffer) {
		ClassReader cr = new ClassReader(classfileBuffer);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        SpringSimpleClientVisitor hv = new SpringSimpleClientVisitor(Constants.asmApiVersion, cw);
        cr.accept(hv, ClassReader.EXPAND_FRAMES);
		return cw.toByteArray();
	}
	
	public int getPriority() {
		return 1;
	}

}
