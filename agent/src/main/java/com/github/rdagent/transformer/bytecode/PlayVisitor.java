package com.github.rdagent.transformer.bytecode;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.AdviceAdapter;

public class PlayVisitor extends ClassVisitor {
	
	private int api;

	public PlayVisitor(int api, ClassVisitor classVisitor) {
		super(api, classVisitor);
		this.api = api;
	}
	
	@Override
	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
			String[] exceptions) {
		MethodVisitor mv = cv.visitMethod(access, name, descriptor, signature, exceptions);
		if(name.contains("apply")) {
			if("(Lplay/api/mvc/Request;)Lscala/concurrent/Future;".equals(descriptor)) {
				//Action is an interface, doesn't store 'this' in slot 0 
				return new ApplyVisitor(0, api, mv, access, name, descriptor);
			}else if("(Lplay/api/mvc/Action;Lplay/api/mvc/RequestHeader;Lscala/util/Either;)Lscala/concurrent/Future;".equals(descriptor)) {
				return new ApplyVisitor(1, api, mv, access, name, descriptor);
			}
			
		}
		return mv;
	}
	
	/**
	 * use ip as default identification
	 * @author uniqueT
	 *
	 */
	class ApplyVisitor extends AdviceAdapter {
		
		private int reqIndex;

		protected ApplyVisitor(int reqIndex, int api, MethodVisitor methodVisitor, int access, String name, String descriptor) {
			super(api, methodVisitor, access, name, descriptor);
			this.reqIndex = reqIndex;
		}
		
		@Override
		protected void onMethodEnter() {
			mv.visitVarInsn(Opcodes.ALOAD, reqIndex);
			mv.visitMethodInsn(Opcodes.INVOKEINTERFACE,
					"play/api/mvc/RequestHeader",
					"remoteAddress",
					"()Ljava/lang/String;", true);
			
			mv.visitMethodInsn(Opcodes.INVOKESTATIC,
					"com/github/rdagent/transformer/intercepter/HttpReqInterceptor",
					"bindIP",
					"(Ljava/lang/String;)V", false);
		}
		
		@Override
		protected void onMethodExit(int opcode) {
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, "com/github/rdagent/transformer/intercepter/IPmap", "unbindIP", "()V", false);
		}
		
	}

}
