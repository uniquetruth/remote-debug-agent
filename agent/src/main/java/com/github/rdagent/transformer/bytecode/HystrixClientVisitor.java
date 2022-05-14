package com.github.rdagent.transformer.bytecode;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.commons.AdviceAdapter;

import org.objectweb.asm.Opcodes;

public class HystrixClientVisitor extends ClassVisitor {
	
	private int api;
	private String contructor = "<init>";
	private String targetMethod = "run";
	private boolean isFieldPresent = false;
	//add a String field, store ip binded by thread when creating instance,
	//in run method, bind this ip to new thread
	private String additionFieldName = "_coverage_parentThread";
	
	public HystrixClientVisitor(int api, ClassVisitor classVisitor) {
		super(api, classVisitor);
		this.api = api;
	}
	
	@Override
    public FieldVisitor visitField(int access, String name, String desc,
            String signature, Object value) {
        if (name.equals(additionFieldName)) {
            isFieldPresent = true;
        }
        return cv.visitField(access, name, desc, signature, value);
	}
	
	@Override
    public void visitEnd() {
        if (!isFieldPresent) {
        	// create a field
            FieldVisitor fv = cv.visitField(Opcodes.ACC_PRIVATE, additionFieldName, "Ljava/lang/String;", null, null);
            if (fv != null) {
                fv.visitEnd();
            }
        }
        cv.visitEnd();
    }
	
	@Override
	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
			String[] exceptions) {
		MethodVisitor mv = cv.visitMethod(access, name, descriptor, signature, exceptions);
		if(contructor.equals(name)){
			return new ConstructVisitor(api, mv, access, name, descriptor);
		}else if(targetMethod.equals(name)){
			return new RunVisitor(api, mv, access, name, descriptor);
		}
		return mv;
	}
	
	/**
	 * While creating the proxy client instance, Thread.currentThread() is business thread, it has binded a ip
	 * So we can store this ip into the custom new field
	 * @author uniqueT
	 *
	 */
	class ConstructVisitor extends AdviceAdapter{

		protected ConstructVisitor(int api, MethodVisitor methodVisitor, int access, String name, String descriptor) {
			super(api, methodVisitor, access, name, descriptor);
		}
		
		@Override
		protected void onMethodEnter() {
			//load [this]
			mv.visitVarInsn(Opcodes.ALOAD, 0);
			mv.visitMethodInsn(Opcodes.INVOKESTATIC,
					"com/github/rdagent/transformer/intercepter/HystrixIntercepter",
					"getMainThreadIdentity",
					"()Ljava/lang/String;", false);
			mv.visitFieldInsn(Opcodes.PUTFIELD, "feign/hystrix/HystrixInvocationHandler$1", additionFieldName, "Ljava/lang/String;");
		}
	}
	
	/**
	 * run method is executed by Hystrix's thread
	 * need to bind and unbind this thread with the ip in the custom new field
	 * @author uniqueT
	 *
	 */
	class RunVisitor extends AdviceAdapter{
		
		protected RunVisitor(int api, MethodVisitor methodVisitor, int access, String name, String descriptor) {
			super(api, methodVisitor, access, name, descriptor);
		}
		
		@Override
		protected void onMethodEnter() {
			mv.visitVarInsn(Opcodes.ALOAD, 0);
			mv.visitFieldInsn(Opcodes.GETFIELD, "feign/hystrix/HystrixInvocationHandler$1", additionFieldName, "Ljava/lang/String;");
			mv.visitMethodInsn(Opcodes.INVOKESTATIC,
					"com/github/rdagent/transformer/intercepter/HystrixIntercepter",
					"bingNewThread",
					"(Ljava/lang/String;)V", false);
		}
		
		@Override
		protected void onMethodExit(int opcode) {
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, "com/github/rdagent/transformer/intercepter/IPmap", "unbindIP", "()V", false);
		}
	}

}
