package com.github.rdagent.transformer.bytecode;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;

/**
 * change service(HttpServletRequest, HttpServletResponse) method
 * @author uniqueT
 *
 */
public class ServletVisitor extends ClassVisitor {
	
	private String targetMethod = "service";
	//private String httpClassName = HttpServletRequest.class.getName();
	private String httpClassName = "javax.servlet.http.HttpServletRequest";
	private int api;
	//which parameter is HttpServletRequest. Default is 1
	private int reqIndex = 1;
	
	public ServletVisitor(int api, ClassVisitor classVisitor, String methodName, int reqIndex) {
		super(api, classVisitor);
		this.api = api;
		this.reqIndex = reqIndex;
		targetMethod = methodName;
	}
	
	@Override
	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
			String[] exceptions) {
		MethodVisitor mv = cv.visitMethod(access, name, descriptor, signature, exceptions);
		Type[] parameters = Type.getArgumentTypes(descriptor);
		if(targetMethod.equals(name) && hasHttpArgs(parameters)){
			//System.out.println("uniqueT debug +++ alter HttpServlet.service");
			return new BindIpVisitor(api, mv, access, name, descriptor);
		}
		return mv;
	}
	
	private boolean hasHttpArgs(Type[] parameters) {
		for(Type param : parameters) {
			if(httpClassName.equals(param.getClassName())) {
				return true;
			}
		}
		return false;
	}
	
	class BindIpVisitor extends AdviceAdapter{

		protected BindIpVisitor(int api, MethodVisitor methodVisitor, int access, String name, String descriptor) {
			super(api, methodVisitor, access, name, descriptor);
		}
		
		@Override
		protected void onMethodEnter() {
			mv.visitVarInsn(Opcodes.ALOAD, reqIndex);
			mv.visitMethodInsn(Opcodes.INVOKESTATIC,
					"com/github/rdagent/transformer/intercepter/HttpReqInterceptor",
					"bindIP",
					"(Ljava/lang/Object;)V", false);
		}
		
		@Override
		protected void onMethodExit(int opcode) {
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, "com/github/rdagent/transformer/intercepter/IPmap", "unbindIP", "()V", false);
		}

	}

}
