package com.github.rdagent.transformer.bytecode;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.AdviceAdapter;

/**
 * Inject dubbo HeaderExchangeHandler.received method
 * @author uniqueT
 *
 */
public class DubboVisitor extends ClassVisitor {
	
	private String targetMethod = "handleRequest";
	private String targetMethod2 = "telnet";
	private int api;
    private int dubboVersion;

	public DubboVisitor(int api, ClassVisitor classVisitor, int dubboVersion) {
		super(api, classVisitor);
		this.api = api;
        this.dubboVersion = dubboVersion;
	}
	
	@Override
	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
			String[] exceptions) {
		MethodVisitor mv = cv.visitMethod(access, name, descriptor, signature, exceptions);
		if(targetMethod.equals(name)){
			return new BindIpVisitor(api, mv, access, name, descriptor);
		}else if(targetMethod2.equals(name)){
            return new TelnetBindIpVisitor(api, mv, access, name, descriptor);
		}
		return mv;
	}
	
	class BindIpVisitor extends AdviceAdapter{

		protected BindIpVisitor(int api, MethodVisitor methodVisitor, int access, String name, String descriptor) {
			super(api, methodVisitor, access, name, descriptor);
		}
		
		@Override
		protected void onMethodEnter() {
			mv.visitVarInsn(Opcodes.ALOAD, 1);
			mv.visitVarInsn(Opcodes.ALOAD, 2);
            if(dubboVersion==2){
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
					"com/alibaba/dubbo/remoting/exchange/Request",
					"getData",
					"()Ljava/lang/Object;", false);
            }else{
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
					"org/apache/dubbo/remoting/exchange/Request",
					"getData",
					"()Ljava/lang/Object;", false);
            }
			mv.visitMethodInsn(Opcodes.INVOKESTATIC,
					"com/github/rdagent/transformer/intercepter/DubboIntercepter",
					"bindIP",
					"(Ljava/lang/Object;Ljava/lang/Object;)V", false);
		}
		
		@Override
		protected void onMethodExit(int opcode) {
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, "com/github/rdagent/transformer/intercepter/IPmap", "unbindIP", "()V", false);
		}

	}

	class TelnetBindIpVisitor extends AdviceAdapter{
		protected TelnetBindIpVisitor(int api, MethodVisitor methodVisitor, int access, String name, String descriptor) {
			super(api, methodVisitor, access, name, descriptor);
		}
		
		@Override
		protected void onMethodEnter() {
			mv.visitVarInsn(Opcodes.ALOAD, 1);
			mv.visitInsn(Opcodes.ACONST_NULL);
			mv.visitMethodInsn(Opcodes.INVOKESTATIC,
					"com/github/rdagent/transformer/intercepter/DubboIntercepter",
					"bindIP",
					"(Ljava/lang/Object;Ljava/lang/Object;)V", false);
		}
		
		@Override
		protected void onMethodExit(int opcode) {
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, "com/github/rdagent/transformer/intercepter/IPmap", "unbindIP", "()V", false);
		}
	}
}
