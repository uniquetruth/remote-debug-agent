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
	 * 代理客户端的构造器在实例化时，是由业务线程执行的，此时的当前线程是有绑定ip的线程
	 * 此时将ip写入自定义新增的字段中保存下来
	 * @author luanfei
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
	 * run方法是Hystrix的线程执行的，在这里需要将当前线程与自定义字段里的ip进行绑定和解绑
	 * @author luanfei
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
