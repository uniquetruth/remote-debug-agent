package com.github.rdagent.transformer.bytecode;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.AdviceAdapter;

public class SpringSimpleClientVisitor extends ClassVisitor {
	
	private int api;
	private String targetMethod = "executeInternal";

	public SpringSimpleClientVisitor(int api, ClassVisitor classVisitor) {
		super(api, classVisitor);
		this.api = api;
	}
	
	@Override
	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
			String[] exceptions) {
		MethodVisitor mv = cv.visitMethod(access, name, descriptor, signature, exceptions);
		if(targetMethod.equals(name)){
			return new SpringClientMethodVisitor(api, mv, access, name, descriptor);
		}
		return mv;
	}
	
	class SpringClientMethodVisitor extends AdviceAdapter{
		
		protected SpringClientMethodVisitor(int api, MethodVisitor methodVisitor, int access, String name, String descriptor) {
			super(api, methodVisitor, access, name, descriptor);
		}
		
		@Override
		protected void onMethodEnter() {
			//the first parameter is headers
			mv.visitVarInsn(Opcodes.ALOAD, 1);
			mv.visitMethodInsn(Opcodes.INVOKESTATIC,
					"com/github/rdagent/transformer/intercepter/SpringClientIntercepter",
					"addCustomHeader",
					"(Ljava/util/Map;)V", false);
		}
	}

}
