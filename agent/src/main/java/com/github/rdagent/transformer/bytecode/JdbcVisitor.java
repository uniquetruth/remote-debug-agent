package com.github.rdagent.transformer.bytecode;

import java.io.IOException;
import java.io.InputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.tree.ClassNode;

public class JdbcVisitor extends ClassVisitor {
	
	private int api;
	private ClassLoader loader;
	private boolean isConnection = false;
	private boolean isStatement = false;
	private boolean isPreparedStatement = false;
	private String superClass;
	private String[] interfaces;

	public JdbcVisitor(int api, ClassVisitor classVisitor, ClassLoader loader) {
		super(api, classVisitor);
		this.api = api;
		this.loader = loader;
	}
	
	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		super.visit(version, access, name, signature, superName, interfaces);
		this.superClass = superName;
		this.interfaces = interfaces;
	}
	
	@Override
	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
			String[] exceptions) {
		MethodVisitor mv = cv.visitMethod(access, name, descriptor, signature, exceptions);
		//not an abstract method
		if((access & Opcodes.ACC_ABSTRACT) == 0) {
			//methods in java.sql.Connection and about sql
			if(isConSqlMethod(name, descriptor)) {
				//make sure just analyse interface once
				if(!isConnection && !isStatement) {
					analyseInterface();
				}
				//make sure it is a Connection class
				if(isConnection) {
					return new SqlVisitor(api, mv, access, name, descriptor);
				}
			}else if(isStSqlMethod(name, descriptor)) { //methods in java.sql.Statement and about sql
				if(!isConnection && !isStatement) {
					analyseInterface();
				}
				if(isStatement) {
					return new SqlVisitor(api, mv, access, name, descriptor);
				}
			}else if(isPstParamMethod(name, descriptor)) { //methods in java.sql.PreparedStatement and about params
				if(!isConnection && !isStatement) {
					analyseInterface();
				}
				if(isPreparedStatement) {
					return new ParamsVisitor(api, mv, access, name, descriptor);
				}
			}
		}
		return mv;
	}
	
	private void analyseInterface() {
		if(isSpecificClass("java/sql/Connection")) {
			isConnection = true;
		}else if(isSpecificClass("java/sql/PreparedStatement")){
			isStatement = true;
			isPreparedStatement = true;
		}else if(isSpecificClass("java/sql/Statement")){
			isStatement = true;
		}
	}
	
	private boolean isSpecificClass(String specificClass) {
		for(String inf : interfaces) {
			if(specificClass.equals(inf)) {
				return true;
			}
		}
		try {
			if(isInterfaceClass(superClass, specificClass)) {
				return true;
			}
			for(String inf : interfaces) {
				if(isInterfaceClass(inf, specificClass)) {
					return true;
				}
			}
		}catch (IOException e) {
			return false;
		}
		return false;
	}
	
	private boolean isConSqlMethod(String name, String descriptor) {
		return "prepareCall".equals(name) && ("(Ljava/lang/String;)Ljava/sql/CallableStatement;".equals(descriptor)
				|| "(Ljava/lang/String;II)Ljava/sql/CallableStatement;".equals(descriptor)
				|| "(Ljava/lang/String;III)Ljava/sql/CallableStatement;".equals(descriptor))
			|| "prepareStatement".equals(name) && ("(Ljava/lang/String;)Ljava/sql/PreparedStatement;".equals(descriptor)
				|| "(Ljava/lang/String;I)Ljava/sql/PreparedStatement;".equals(descriptor)
				|| "(Ljava/lang/String;[I)Ljava/sql/PreparedStatement;".equals(descriptor)
				|| "(Ljava/lang/String;II)Ljava/sql/PreparedStatement;".equals(descriptor)
				|| "(Ljava/lang/String;III)Ljava/sql/PreparedStatement;".equals(descriptor)
				|| "(Ljava/lang/String;[Ljava/lang/String;)Ljava/sql/PreparedStatement;".equals(descriptor));
	}
	
	private boolean isStSqlMethod(String name, String descriptor) {
		return "addBatch".equals(name) && "(Ljava/lang/String;)V".equals(descriptor)
			|| "execute".equals(name) && ("(Ljava/lang/String;)Z".equals(descriptor)
				|| "(Ljava/lang/String;I)Z".equals(descriptor) || "(Ljava/lang/String;[I)Z".equals(descriptor)
				|| "(Ljava/lang/String;[Ljava/lang/String;)Z".equals(descriptor) )
			|| "executeQuery".equals(name) && "(Ljava/lang/String;)Ljava/sql/ResultSet;".equals(descriptor)
			|| "executeUpdate".equals(name) && ("(Ljava/lang/String;)I".equals(descriptor)
				|| "(Ljava/lang/String;I)I".equals(descriptor) || "(Ljava/lang/String;[I)I".equals(descriptor)
				|| "(Ljava/lang/String;[Ljava/lang/String;)I".equals(descriptor));
	}
	
	private boolean isPstParamMethod(String name, String descriptor) {
		return "setString".equals(name) && "(ILjava/lang/String;)V".equals(descriptor)
			|| "setInt".equals(name) && "(II)V".equals(descriptor)
			|| "setLong".equals(name) && "(IJ)V".equals(descriptor)
			|| "setFloat".equals(name) && "(IF)V".equals(descriptor)
			|| "setDouble".equals(name) && "(ID)V".equals(descriptor)
			|| "setShort".equals(name) && "(IS)V".equals(descriptor)
			|| "setBigDecimal".equals(name) && "(ILjava/math/BigDecimal;)V".equals(descriptor)
			|| "setDate".equals(name) && "(ILjava/sql/Date;)V".equals(descriptor)
			|| "setTime".equals(name) && "(ILjava/sql/Time;)V".equals(descriptor)
			|| "setTimestamp".equals(name) && "(ILjava/sql/Timestamp;)V".equals(descriptor);
	}
	
	private boolean isInterfaceClass(String className, String intfClass) throws IOException {
		InputStream in = loader.getResourceAsStream(className+".class");
		if(in != null) {
			ClassReader cr = new ClassReader(in);
			ClassNode cn = new ClassNode();
			cr.accept(cn, 0);
			if(intfClass.equals(cn.superName)) {
				return true;
			}
			for(String inf : cn.interfaces) {
				//System.out.println("inf: "+inf);
				if(intfClass.equals(inf)) {
					return true;
				}
			}
			//System.out.println("cn.superName: "+cn.superName);
			if(isInterfaceClass(cn.superName, intfClass)) {
				return true;
			}
			for(String inf : cn.interfaces) {
				if(isInterfaceClass(inf, intfClass)) {
					return true;
				}
			}
			in.close();
		}
		return false;
	}
	
	class SqlVisitor extends AdviceAdapter{

		protected SqlVisitor(int api, MethodVisitor methodVisitor, int access, String name, String descriptor) {
			super(api, methodVisitor, access, name, descriptor);
		}
		
		@Override
		protected void onMethodEnter() {
			mv.visitVarInsn(Opcodes.ALOAD, 1);
			mv.visitMethodInsn(Opcodes.INVOKESTATIC,
					"com/github/rdagent/transformer/intercepter/SqlIntercepter",
					"recordSql",
					"(Ljava/lang/String;)V", false);
		}
		
		@Override
		protected void onMethodExit(int opcode) {
			mv.visitMethodInsn(Opcodes.INVOKESTATIC, 
					"com/github/rdagent/transformer/intercepter/SqlIntercepter", 
					"endSql", 
					"()V", false);
		}
	}
	
	class ParamsVisitor extends AdviceAdapter{
		
		private Type p;
		
		protected ParamsVisitor(int api, MethodVisitor methodVisitor, int access, String name, String descriptor) {
			super(api, methodVisitor, access, name, descriptor);
			p = Type.getArgumentTypes(descriptor)[1];
		}
		
		@Override
		protected void onMethodEnter() {
			//index
			mv.visitVarInsn(Opcodes.ILOAD, 1);
			//System.out.println("p.getClassName(): "+p.getClassName());
			if("int".equals(p.getClassName())) {
				mv.visitVarInsn(Opcodes.ILOAD, 2);
				mv.visitMethodInsn(Opcodes.INVOKESTATIC,
						"com/github/rdagent/transformer/intercepter/SqlIntercepter",
						"recordParam",
						"(II)V", false);
			}else if("java.lang.String".equals(p.getClassName())){
				mv.visitVarInsn(Opcodes.ALOAD, 2);
				mv.visitMethodInsn(Opcodes.INVOKESTATIC,
						"com/github/rdagent/transformer/intercepter/SqlIntercepter",
						"recordParam",
						"(ILjava/lang/String;)V", false);
			}else if("long".equals(p.getClassName())){
				mv.visitVarInsn(Opcodes.LLOAD, 2);
				mv.visitMethodInsn(Opcodes.INVOKESTATIC,
						"com/github/rdagent/transformer/intercepter/SqlIntercepter",
						"recordParam",
						"(IJ)V", false);
			}else if("float".equals(p.getClassName())){
				mv.visitVarInsn(Opcodes.FLOAD, 2);
				mv.visitMethodInsn(Opcodes.INVOKESTATIC,
						"com/github/rdagent/transformer/intercepter/SqlIntercepter",
						"recordParam",
						"(IF)V", false);
			}else if("double".equals(p.getClassName())){
				mv.visitVarInsn(Opcodes.DLOAD, 2);
				mv.visitMethodInsn(Opcodes.INVOKESTATIC,
						"com/github/rdagent/transformer/intercepter/SqlIntercepter",
						"recordParam",
						"(ID)V", false);
			}else if("short".equals(p.getClassName())){
				mv.visitVarInsn(Opcodes.ILOAD, 2);
				mv.visitMethodInsn(Opcodes.INVOKESTATIC,
						"com/github/rdagent/transformer/intercepter/SqlIntercepter",
						"recordParam",
						"(IS)V", false);
			}else if("java.math.BigDecimal".equals(p.getClassName())){
				mv.visitVarInsn(Opcodes.ALOAD, 2);
				mv.visitMethodInsn(Opcodes.INVOKESTATIC,
						"com/github/rdagent/transformer/intercepter/SqlIntercepter",
						"recordParam",
						"(ILjava/math/BigDecimal;)V", false);
			}else if("java.sql.Date".equals(p.getClassName())){
				mv.visitVarInsn(Opcodes.ALOAD, 2);
				mv.visitMethodInsn(Opcodes.INVOKESTATIC,
						"com/github/rdagent/transformer/intercepter/SqlIntercepter",
						"recordParam",
						"(ILjava/sql/Date;)V", false);
			}else if("java.sql.Time".equals(p.getClassName())){
				mv.visitVarInsn(Opcodes.ALOAD, 2);
				mv.visitMethodInsn(Opcodes.INVOKESTATIC,
						"com/github/rdagent/transformer/intercepter/SqlIntercepter",
						"recordParam",
						"(ILjava/sql/Time;)V", false);
			}else if("java.sql.Timestamp".equals(p.getClassName())){
				mv.visitVarInsn(Opcodes.ALOAD, 2);
				mv.visitMethodInsn(Opcodes.INVOKESTATIC,
						"com/github/rdagent/transformer/intercepter/SqlIntercepter",
						"recordParam",
						"(ILjava/sql/Timestamp;)V", false);
			}
			
		}
	}

}
