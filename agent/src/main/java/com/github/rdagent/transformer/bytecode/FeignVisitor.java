package com.github.rdagent.transformer.bytecode;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.AdviceAdapter;

public class FeignVisitor extends ClassVisitor {
	
	private int api;
	private String targetMethod = "<init>";
	
	public FeignVisitor(int api, ClassVisitor classVisitor) {
		super(api, classVisitor);
		this.api = api;
	}
	
	@Override
	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
			String[] exceptions) {
		MethodVisitor mv = cv.visitMethod(access, name, descriptor, signature, exceptions);
		if(targetMethod.equals(name)){
			return new FeignMethodVisitor(api, mv, access, name, descriptor);
		}
		return mv;
	}
	
	class FeignMethodVisitor extends AdviceAdapter{
		
		protected FeignMethodVisitor(int api, MethodVisitor methodVisitor, int access, String name, String descriptor) {
			super(api, methodVisitor, access, name, descriptor);
		}
		
		@Override
		protected void onMethodExit(int opcode) {
			//load [this]
			mv.visitVarInsn(Opcodes.ALOAD, 0);
			mv.visitMethodInsn(Opcodes.INVOKESTATIC,
					"com/github/rdagent/transformer/intercepter/FeignTemplateIntercepter",
					"addCustomHeader",
					"(Ljava/lang/Object;)V", false);
		}
	}

}
