package com.github.rdagent.transformer.bytecode;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.AdviceAdapter;

public class DubboClientVisitor extends ClassVisitor {

    private String targetMethod = "request";
    private int dubboVersion;
    private int api;

	public DubboClientVisitor(int api, ClassVisitor classVisitor, int dubboVersion) {
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
		}
		return mv;
	}

    class BindIpVisitor extends AdviceAdapter {

        protected BindIpVisitor(int api, MethodVisitor methodVisitor, int access, String name, String descriptor) {
			super(api, methodVisitor, access, name, descriptor);
		}
		
		@Override
		protected void onMethodEnter() {
			mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            if(dubboVersion==2){
                mv.visitFieldInsn(Opcodes.GETFIELD, 
                    "com/alibaba/dubbo/remoting/exchange/support/header/HeaderExchangeClient", 
                    "channel", 
                    "Lcom/alibaba/dubbo/remoting/exchange/ExchangeChannel;");
            }else{
                mv.visitFieldInsn(Opcodes.GETFIELD, 
                    "org/apache/dubbo/remoting/exchange/support/header/HeaderExchangeClient", 
                    "channel", 
                    "Lorg/apache/dubbo/remoting/exchange/ExchangeChannel;");
            }
            mv.visitIntInsn(Opcodes.SIPUSH, dubboVersion);
			mv.visitMethodInsn(Opcodes.INVOKESTATIC,
			    "com/github/rdagent/transformer/intercepter/DubboClientIntercepter",
			    "bindIP",
			    "(Ljava/lang/Object;Ljava/lang/Object;I)V", false);
		}

    }
    
}
