package com.github.rdagent.transformer.bytecode;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.AdviceAdapter;

import com.github.rdagent.Constants;

public class SpringRabbitVisitor extends ClassVisitor {
	
	private int api;
	private String targetMethod = "doReceiveAndExecute";

	public SpringRabbitVisitor(int api, ClassVisitor classVisitor) {
		super(api, classVisitor);
		this.api = api;
	}
	
	@Override
	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
			String[] exceptions) {
		MethodVisitor mv = cv.visitMethod(access, name, descriptor, signature, exceptions);
		if(targetMethod.equals(name)){
			return new BindIpVisitor(api, mv, access, name, descriptor);
		}
		return mv;
	}
	
	class BindIpVisitor extends AdviceAdapter{

		protected BindIpVisitor(int api, MethodVisitor methodVisitor, int access, String name, String descriptor) {
			super(api, methodVisitor, access, name, descriptor);
		}
		
		@Override
		protected void onMethodEnter() {
			//message queue is asynchronous, no need to get remote ip
			mv.visitLdcInsn(Constants.virtualIp);
			mv.visitMethodInsn(Opcodes.INVOKESTATIC,
					"com/github/rdagent/transformer/intercepter/IPmap",
					"bindIP",
					"(Ljava/lang/String;)V", false);
		}
		
		@Override
		protected void onMethodExit(int opcode) {
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, "com/github/rdagent/transformer/intercepter/IPmap", "unbindIP", "()V", false);
		}

	}

}
