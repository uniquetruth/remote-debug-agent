package com.github.rdagent.transformer.handler;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import com.github.rdagent.Constants;
import com.github.rdagent.transformer.AbstractHandler;
import com.github.rdagent.transformer.bytecode.SpringRabbitVisitor;

public class SpringRabbitHandler extends AbstractHandler {
	
	//spring-rabbit-1.4.0
	private String springRabbit = "org/springframework/amqp/rabbit/listener/SimpleMessageListenerContainer";

	@Override
	public boolean filterClassName(String className) {
		return springRabbit.equals(className);
	}

	@Override
	public byte[] process(String className, byte[] classfileBuffer, ClassLoader loader) {
		ClassReader cr = new ClassReader(classfileBuffer);
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		SpringRabbitVisitor rv = new SpringRabbitVisitor(Constants.asmApiVersion, cw);
		cr.accept(rv, ClassReader.EXPAND_FRAMES);
		return cw.toByteArray();
	}
	
	public int getPriority() {
		return 1;
	}

}
