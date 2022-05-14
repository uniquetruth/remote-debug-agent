package com.github.rdagent.transformer.bytecode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.LocalVariablesSorter;

public class AppVisitor extends ClassVisitor {

	private String owner;
	private int api;
	private Map<String, ArrayList<Integer>> methodOffsetMap;
	private Map<String, ArrayList<Integer>> methodLineMap;

	public AppVisitor(int api, ClassVisitor classVisitor, Map<String, ArrayList<Integer>> _methodOffsetMap,
			Map<String, ArrayList<Integer>> _methodLineMap) {
		super(api, classVisitor);
		this.api = api;
		methodOffsetMap = _methodOffsetMap;
		methodLineMap = _methodLineMap;
		//System.out.println("cl in AppVisitor : "+AppVisitor.class.getClassLoader());
	}

	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		super.visit(version, access, name, signature, superName, interfaces);
		owner = name;
		// className = name.replaceAll("/", ".");
		//isInterface = (access & Opcodes.ACC_INTERFACE) != 0;
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
			String[] exceptions) {
		MethodVisitor mv = cv.visitMethod(access, name, descriptor, signature, exceptions);
		
		if (mv != null) {
			MethodEnhanceVisitor at = new MethodEnhanceVisitor(api, access, name, descriptor, mv);
			//at.aa = new AnalyzerAdapter(owner, access, name, descriptor, at);
			at.lvs = new LocalVariablesSorter(access, descriptor, at);

			return at.lvs;
		}
		return mv;
	}

	@Override
	public void visitEnd() {
		cv.visitEnd();
	}
	
	private String getStringParams(Type[] types) {
		StringBuilder sb = new StringBuilder("(");
		for (Type type : types) {
			sb.append(type.getClassName()).append(", ");
		}
		if (sb.length() > 2)
			sb.setLength(sb.length() - 2);
		sb.append(")");
		return sb.toString();
	}

	class MethodEnhanceVisitor extends MethodVisitor {

		public LocalVariablesSorter lvs;
		//public AnalyzerAdapter aa;
		private Label from = new Label(),
		        to = new Label(),
		        target = new Label();


		private boolean isStatic;
		private boolean jumpDestination = false;
		private String methodName;
		private String fullMethodName;
		private List<Integer> offsetList;
		private List<Integer> methodLines;
		private Type[] parameters;
		//private int maxStack;
		private int oarrayIndex;
		private int barrayIndex;
		private int insnOffset = 0;
		private int lineIndex = -1;
		private Label lastLabel = null;
		private Map<Label, Label> stackMap = new HashMap<Label, Label>();

		public MethodEnhanceVisitor(int api, int access, String name, String desc, MethodVisitor methodVisitor) {
			super(api, methodVisitor);
			methodName = name;
			isStatic = (access & Opcodes.ACC_STATIC) != 0;
			parameters = Type.getArgumentTypes(desc);
			String returnType = (Type.getReturnType(desc)).getClassName();
			fullMethodName = returnType + " " +owner.replaceAll("/", ".") + "." + methodName + getStringParams(parameters);
			offsetList = methodOffsetMap.get(fullMethodName);
			methodLines = methodLineMap.get(fullMethodName);
		}

		public void visitCode() {
			//System.out.println(fullMethodName);
			/*if(fullMethodName.contains("getCbsUser")) {
				System.out.print("luanfei debug +++ lineNumberList : [");
				for(int x=0;x< methodLines.size();x++) {
					String ln = x>0 ? ", "+methodLines.get(x) : String.valueOf(methodLines.get(x));
					System.out.print(ln);
				}
				System.out.println("]");
				System.out.print("luanfei debug +++ insnOffsetList : [");
				for(int x=0;x< offsetList.size();x++) {
					String lo = x>0 ? ", "+offsetList.get(x) : String.valueOf(offsetList.get(x));
					System.out.print(lo);
				}
				System.out.println("]");
			}*/
			mv.visitCode();
			// ==============这里在方法开始前，调用AppInterceptor.onMethodIn进行方法级的调用记录，必要时绑定ip==========
			// 创建Object[]类型的变量os
			mv.visitIntInsn(Opcodes.BIPUSH, parameters.length);
			mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");

			oarrayIndex = lvs.newLocal(Type.getObjectType("[Ljava/lang/Object;"));
			//System.out.println("varNumber = " + oarrayIndex);
			mv.visitVarInsn(Opcodes.ASTORE, oarrayIndex);

			// 填装参数
			installParams(oarrayIndex);

			mv.visitLdcInsn(fullMethodName);
			mv.visitVarInsn(Opcodes.ALOAD, oarrayIndex);
			// 调用AppInterceptor.onMethodIn
			mv.visitMethodInsn(Opcodes.INVOKESTATIC,
					"com/github/rdagent/transformer/intercepter/AppIntercepter",
					"onMethodIn",
					"(Ljava/lang/String;[Ljava/lang/Object;)V", false);
			// ==============AppInterceptor.onMethodIn调用完毕=======这部分栈高度为4===================
			
			// =============这里开始代码行级探针的建立，visitCode方法内进行boolean数组的初始化，后续的13个visit(X)insn方法中写入每一行的探针
			//创建boolean[]类型的变量
			if(offsetList.size() > Byte.MAX_VALUE/2) {
				mv.visitIntInsn(Opcodes.SIPUSH, offsetList.size()*2);
			}else {
				mv.visitIntInsn(Opcodes.BIPUSH, offsetList.size()*2);
			}
	        mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_BOOLEAN);
	        barrayIndex = lvs.newLocal(Type.getObjectType("[Z"));
	        mv.visitVarInsn(Opcodes.ASTORE, barrayIndex);
	        //对于只有一个指令的行，需要事先把行尾探针置为true
	        for(int i=0;i<offsetList.size();i++) {
	        	if(offsetList.get(i)==1 && methodLines.get(i)>0) { //顺序执行代码块的单指令行不处理
	        		int endPointer = i * 2 + 1;
	        		mv.visitVarInsn(Opcodes.ALOAD, barrayIndex);
	        		if(endPointer > Byte.MAX_VALUE) {
	        			mv.visitIntInsn(Opcodes.SIPUSH, endPointer);
	        		}else {
	        			mv.visitIntInsn(Opcodes.BIPUSH, endPointer);
	        		}
	        		mv.visitInsn(Opcodes.ICONST_1); //赋值true等同于int 1入栈
	        		mv.visitInsn(Opcodes.BASTORE);
	        	}
	        }
			// ==============探针初始化完毕===========这部分栈高度为4=====================
	        
	        //==============给整个方法套上try-catch，方法开始前加入try，visitMaxs是方法（文本上的）最后，在那里加catch======================
	        //构造方法因为有默认的super指令在里面，没法整体try-catch，虽然有办法在super后面加，但是可能比较麻烦，考虑到构造方法抛异常的可能性较低，先暂时忽略这种情况
	        if(!methodName.equals("<init>")) {
	        	mv.visitLabel(from);
	        }
	        //==========try语句块编写完毕===============不是指令块，不占用栈高度==========================
	        
	        //整体栈高度用以上3块的最大值
	        //maxStack = 4;
		}
		
		public void visitInsn(int opcode) {
			//可能有其它动态框架插入的指令，造成了“异常”结构的class，这些class具有javac正常编译下不可能生成的结构，但可以正常运行
			//asm在处理正常javac编译的class时，总是先访问visitLineNumber，后访问visitInsn，但asm官方文档也不保证总是这个顺序，所以不要在这些指令位置附加探针
			if(lineIndex == -1) {
				mv.visitInsn(opcode);
				return;
			}
			//该指令是否是某行的第一个指令：
			if(insnOffset==0) {
				if(methodLines.get(lineIndex)>0 //1、该行有可能造成非顺序执行的指令
					|| lineIndex==0 //2、该行是该方法的第一行
					|| methodLines.get(lineIndex-1)>0 //3、该行是顺序执行代码块的第一行
					|| jumpDestination) { //4、该行是一个跳转目的地
					writeStartProbe(opcode);
				}
			//在行尾写入探针的条件：
			}else if((insnOffset+1)==offsetList.get(lineIndex) //1、该指令是某行的最后一个指令
					&& methodLines.get(lineIndex)>0){ //2、该行有可能造成非顺序执行的指令
				writeEndProbe(opcode);
			}
	    	insnOffset++;
	    	//方法退出前的注入(Opcodes.ATHROW前不拦截了，交给每个方法统一的try-catch块拦截)
	    	//if ((opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) || opcode == Opcodes.ATHROW) {
	    	if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) {
	    		if(opcode == Opcodes.RETURN) {
					mv.visitInsn(Opcodes.ACONST_NULL);
				}else if(opcode == Opcodes.IRETURN){
					mv.visitInsn(Opcodes.DUP);
					mv.visitMethodInsn(Opcodes.INVOKESTATIC, 
							"java/lang/Integer", 
							"valueOf", 
							"(I)Ljava/lang/Integer;",
							false);
				}else if(opcode == Opcodes.FRETURN){
					mv.visitInsn(Opcodes.DUP);
					mv.visitMethodInsn(Opcodes.INVOKESTATIC, 
							"java/lang/Float", 
							"valueOf", 
							"(F)Ljava/lang/Float;",
							false);
				}else if(opcode == Opcodes.LRETURN){
					mv.visitInsn(Opcodes.DUP2);
					mv.visitMethodInsn(Opcodes.INVOKESTATIC, 
							"java/lang/Long", 
							"valueOf", 
							"(J)Ljava/lang/Long;",
							false);
				}else if(opcode == Opcodes.DRETURN){
					mv.visitInsn(Opcodes.DUP2);
					mv.visitMethodInsn(Opcodes.INVOKESTATIC, 
							"java/lang/Double", 
							"valueOf", 
							"(D)Ljava/lang/Double;",
							false);
				}else {
					mv.visitInsn(Opcodes.DUP);
				}
				mv.visitLdcInsn(fullMethodName);
				mv.visitVarInsn(Opcodes.ALOAD, barrayIndex);
				mv.visitMethodInsn(Opcodes.INVOKESTATIC,
						"com/github/rdagent/transformer/intercepter/AppIntercepter",
						"onMethodOut",
						"(Ljava/lang/Object;Ljava/lang/String;[Z)V",
						false);
				//有一些动态生成的类不支持compute_frame
				/*if(aa.stack==null) {
					maxStack = Math.max(4, maxStack);
				}else {
					maxStack = Math.max(aa.stack.size() + 4, maxStack);
				}*/
	    	}
	    	//System.out.println("visitInsn : opcode="+opcode);
	    	mv.visitInsn(opcode);
	    }

		public void visitIincInsn(int var, int increment) {
			if(lineIndex == -1) {
				mv.visitIincInsn(var, increment);
				return;
			}
			if(insnOffset==0) {
				if(methodLines.get(lineIndex)>0 || lineIndex==0 || methodLines.get(lineIndex-1)>0
						|| jumpDestination) {
					writeStartProbe(Opcodes.IINC);
				}
			}else if((insnOffset+1)==offsetList.get(lineIndex) && methodLines.get(lineIndex)>0){
				writeEndProbe(Opcodes.IINC);
			}
	    	insnOffset++;
	    	//System.out.println("visitIincInsn : var = "+var+" increment = "+increment);
	    	mv.visitIincInsn(var, increment);
	    }
	    
	    public void visitIntInsn(int opcode, int operand) {
	    	if(lineIndex == -1) {
				mv.visitIntInsn(opcode, operand);
				return;
			}
	    	if(insnOffset==0) {
	    		if(methodLines.get(lineIndex)>0 || lineIndex==0 || methodLines.get(lineIndex-1)>0
	    				|| jumpDestination) {
					writeStartProbe(opcode);
				}
			}else if((insnOffset+1)==offsetList.get(lineIndex) && methodLines.get(lineIndex)>0){
				writeEndProbe(opcode);
			}
	    	insnOffset++;
	    	//System.out.println("visitIntInsn : opcode = "+opcode+" operand = "+operand);
	    	mv.visitIntInsn(opcode, operand);
	    }
	    
	    public void visitJumpInsn(int opcode, Label label) {
	    	if(lineIndex == -1) {
				mv.visitJumpInsn(opcode, label);
				return;
			}
	    	if(insnOffset==0) {
	    		if(methodLines.get(lineIndex)>0 || lineIndex==0 || methodLines.get(lineIndex-1)>0
	    				|| jumpDestination) {
					writeStartProbe(opcode);
				}
			}else if((insnOffset+1)==offsetList.get(lineIndex) && methodLines.get(lineIndex)>0){
				writeEndProbe(opcode);
			}
	    	insnOffset++;
	    	//System.out.println("visitJumpInsn : opcode="+opcode + " label="+label);
	    	mv.visitJumpInsn(opcode, label);
	    }
	    
	    public void visitVarInsn(int opcode, int var) {
	    	if(lineIndex == -1) {
				mv.visitVarInsn(opcode, var);
				return;
			}
	    	if(insnOffset==0) {
	    		if(methodLines.get(lineIndex)>0 || lineIndex==0 || methodLines.get(lineIndex-1)>0
	    				|| jumpDestination) {
					writeStartProbe(opcode);
				}
			}else if((insnOffset+1)==offsetList.get(lineIndex) && methodLines.get(lineIndex)>0){
				writeEndProbe(opcode);
			}
	    	insnOffset++;
	    	//System.out.println("visitVarInsn : opcode="+opcode+" operand = "+var);
	    	mv.visitVarInsn(opcode, var);
	    }
	    
	    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
	    	if(lineIndex == -1) {
				mv.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
				return;
			}
	    	if(insnOffset==0) {
	    		if(methodLines.get(lineIndex)>0 || lineIndex==0 || methodLines.get(lineIndex-1)>0
	    				|| jumpDestination) {
					writeStartProbe(opcode);
				}
			}else if((insnOffset+1)==offsetList.get(lineIndex) && methodLines.get(lineIndex)>0){
				writeEndProbe(opcode);
			}
	    	insnOffset++;
	    	//System.out.println("visitMethodInsn : opcode="+opcode+" owner = "+owner+" name = "+name);
	    	mv.visitMethodInsn(opcode, owner, name, descriptor,isInterface);
	    }
	    
	    public void visitLdcInsn(Object value) {
	    	if(lineIndex == -1) {
				mv.visitLdcInsn(value);
				return;
			}
	    	if(insnOffset==0) {
	    		if(methodLines.get(lineIndex)>0 || lineIndex==0 || methodLines.get(lineIndex-1)>0
	    				|| jumpDestination) {
					writeStartProbe(Opcodes.LDC);
				}
			}else if((insnOffset+1)==offsetList.get(lineIndex) && methodLines.get(lineIndex)>0){
				writeEndProbe(Opcodes.LDC);
			}
	    	insnOffset++;
	    	//System.out.println("visitLdcInsn : value = "+value);
	    	mv.visitLdcInsn(value);
	    }
	    
	    public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
	    	if(lineIndex == -1) {
				mv.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
				return;
			}
	    	if(insnOffset==0) {
	    		if(methodLines.get(lineIndex)>0 || lineIndex==0 || methodLines.get(lineIndex-1)>0
	    				|| jumpDestination) {
					writeStartProbe(Opcodes.INVOKEDYNAMIC);
				}
			}else if((insnOffset+1)==offsetList.get(lineIndex) && methodLines.get(lineIndex)>0){
				writeEndProbe(Opcodes.INVOKEDYNAMIC);
			}
	    	insnOffset++;
	    	//System.out.println("visitInvokeDynamicInsn : descriptor = "+descriptor+" name = "+name);
	    	mv.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
	    }
	    
	    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
	    	if(lineIndex == -1) {
				mv.visitFieldInsn(opcode, owner, name, descriptor);
				return;
			}
	    	if(insnOffset==0) {
	    		if(methodLines.get(lineIndex)>0 || lineIndex==0 || methodLines.get(lineIndex-1)>0
	    				|| jumpDestination) {
					writeStartProbe(opcode);
				}
			}else if((insnOffset+1)==offsetList.get(lineIndex) && methodLines.get(lineIndex)>0){
				writeEndProbe(opcode);
			}
	    	insnOffset++;
	    	//System.out.println("visitFieldInsn : opcode = "+opcode+" owner = "+owner+" name = "+name);
	    	mv.visitFieldInsn(opcode, owner, name, descriptor);
	    }
	    
	    public void visitTypeInsn(int opcode, String type) {
	    	if(lineIndex == -1) {
				mv.visitTypeInsn(opcode, type);
				return;
			}
	    	if(insnOffset==0) {
	    		if(methodLines.get(lineIndex)>0 || lineIndex==0 || methodLines.get(lineIndex-1)>0
	    				|| jumpDestination) {
					writeStartProbe(opcode);
				}
			}else if((insnOffset+1)==offsetList.get(lineIndex) && methodLines.get(lineIndex)>0){
				writeEndProbe(opcode);
			}
	    	insnOffset++;
	    	//System.out.println("visitTypeInsn : opcode = "+opcode+" type = "+type);
	    	mv.visitTypeInsn(opcode, type);
	    }
	    
	    public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
	    	if(lineIndex == -1) {
				mv.visitTableSwitchInsn(min, max, dflt, labels);
				return;
			}
	    	if(insnOffset==0) {
	    		if(methodLines.get(lineIndex)>0 || lineIndex==0 || methodLines.get(lineIndex-1)>0
	    				|| jumpDestination) {
					writeStartProbe(Opcodes.TABLESWITCH);
				}
			}else if((insnOffset+1)==offsetList.get(lineIndex) && methodLines.get(lineIndex)>0){
				writeEndProbe(Opcodes.TABLESWITCH);
			}
	    	insnOffset++;
	    	//System.out.println("visitTableSwitchInsn : min = "+min+" max = "+max);
	    	mv.visitTableSwitchInsn(min, max, dflt, labels);
	    }
	    
	    public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
	    	if(lineIndex == -1) {
				mv.visitLookupSwitchInsn(dflt, keys, labels);
				return;
			}
	    	if(insnOffset==0) {
	    		if(methodLines.get(lineIndex)>0 || lineIndex==0 || methodLines.get(lineIndex-1)>0
	    				|| jumpDestination) {
					writeStartProbe(Opcodes.LOOKUPSWITCH);
				}
			}else if((insnOffset+1)==offsetList.get(lineIndex) && methodLines.get(lineIndex)>0){
				writeEndProbe(Opcodes.LOOKUPSWITCH);
			}
	    	insnOffset++;
	    	//System.out.println("visitLookupSwitchInsn : Label = "+dflt.getOffset());
	    	mv.visitLookupSwitchInsn(dflt, keys, labels);
	    }
	    
	    public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
	    	if(lineIndex == -1) {
				mv.visitMultiANewArrayInsn(descriptor, numDimensions);
				return;
			}
	    	if(insnOffset==0) {
	    		if(methodLines.get(lineIndex)>0 || lineIndex==0 || methodLines.get(lineIndex-1)>0
	    				|| jumpDestination) {
					writeStartProbe(Opcodes.MULTIANEWARRAY);
				}
			}else if((insnOffset+1)==offsetList.get(lineIndex) && methodLines.get(lineIndex)>0){
				writeEndProbe(Opcodes.MULTIANEWARRAY);
			}
	    	insnOffset++;
	    	//System.out.println("visitMultiANewArrayInsn : descriptor = "+descriptor+" numDimensions = "+numDimensions);
	    	mv.visitMultiANewArrayInsn(descriptor, numDimensions);
	    }
	    
	    public void visitLineNumber(int line, Label start) {
	    	//每访问一次这个方法，表示一行新的代码开始
	    	insnOffset = 0;
	    	lineIndex++;
	    	jumpDestination = false;
			mv.visitLineNumber(line, start);
			//System.out.println("line = "+line+" ; label = "+start + " "+start.getOffset());
		}
	    
	    private void writeStartProbe(int opcode) {
	    	mv.visitVarInsn(Opcodes.ALOAD, barrayIndex);
	    	if(lineIndex > Byte.MAX_VALUE/2) {
	    		mv.visitIntInsn(Opcodes.SIPUSH, lineIndex*2);
	    	}else {
	    		mv.visitIntInsn(Opcodes.BIPUSH, lineIndex*2);
	    	}
    		mv.visitInsn(Opcodes.ICONST_1);
    		mv.visitInsn(Opcodes.BASTORE);
    		//如果探针后面跟的是new指令，可能会导致stack map table entry里的未初始化变量label不匹配
    		//所以这里需要插入一个新label，并把原label记录下来，后面访问到相应的frame时，用新label替换旧的
    		if(opcode == Opcodes.NEW || opcode == Opcodes.NEWARRAY 
	    			|| opcode == Opcodes.ANEWARRAY || opcode == Opcodes.MULTIANEWARRAY) {
    			Label newLabel = new Label();
    			stackMap.put(lastLabel, newLabel);
    			//System.out.println("luanfei debug +++ replace "+lastLabel+" to "+newLabel);
	    		mv.visitLabel(newLabel);
	    	}
		}
	    
	    private void writeEndProbe(int opcode) {
	    	mv.visitVarInsn(Opcodes.ALOAD, barrayIndex);
	    	if(lineIndex > Byte.MAX_VALUE/2) {
	    		mv.visitIntInsn(Opcodes.SIPUSH, lineIndex*2+1);
	    	}else {
	    		mv.visitIntInsn(Opcodes.BIPUSH, lineIndex*2+1);
	    	}
    		mv.visitInsn(Opcodes.ICONST_1);
    		mv.visitInsn(Opcodes.BASTORE);

    		if(opcode == Opcodes.NEW || opcode == Opcodes.NEWARRAY 
	    			|| opcode == Opcodes.ANEWARRAY || opcode == Opcodes.MULTIANEWARRAY) {
    			Label newLabel = new Label();
    			stackMap.put(lastLabel, newLabel);
    			//System.out.println("luanfei debug +++ replace "+lastLabel+" to "+newLabel);
	    		mv.visitLabel(newLabel);
	    	}
		}
		
		// 构造参数值数组Object[]
		private void installParams(int oarrayIndex) {
			int j = 0;
			// 逐个将方法实参放入object数组中
			for (int i = 0; i < parameters.length; i++) {
				// 数组变量入栈
				mv.visitVarInsn(Opcodes.ALOAD, oarrayIndex);
				// 下标入栈
				mv.visitIntInsn(Opcodes.BIPUSH, i);

				Type t = parameters[i];
				String typeName = t.getClassName();
				// 参数值入栈，java基本类型要转换为引用类型才能赋值给object类型的变量，所以需要根据不同的基本类型做不同的处理
				if ("int".equals(typeName)) {
					mv.visitVarInsn(Opcodes.ILOAD, j + (isStatic ? 0 : 1));
					mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;",
							false);
				} else if ("boolean".equals(typeName)) {
					mv.visitVarInsn(Opcodes.ILOAD, j + (isStatic ? 0 : 1));
					mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;",
							false);
				} else if ("byte".equals(typeName)) {
					mv.visitVarInsn(Opcodes.ILOAD, j + (isStatic ? 0 : 1));
					mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false);
				} else if ("short".equals(typeName)) {
					mv.visitVarInsn(Opcodes.ILOAD, j + (isStatic ? 0 : 1));
					mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;",
							false);
				} else if ("char".equals(typeName)) {
					mv.visitVarInsn(Opcodes.ILOAD, j + (isStatic ? 0 : 1));
					mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Character", "valueOf",
							"(C)Ljava/lang/Character;", false);
				} else if ("long".equals(typeName)) {
					mv.visitVarInsn(Opcodes.LLOAD, j + (isStatic ? 0 : 1));
					mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
					// long和double基本类型的变量，在局部变量表中会花2个slot储存参数值，所以顺序访问参数时要跳一个
					j++;
				} else if ("float".equals(typeName)) {
					mv.visitVarInsn(Opcodes.FLOAD, j + (isStatic ? 0 : 1));
					mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;",
							false);
				} else if ("double".equals(typeName)) {
					mv.visitVarInsn(Opcodes.DLOAD, j + (isStatic ? 0 : 1));
					mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;",
							false);
					j++;
				} else {
					// 基本类型之外的引用类型可直接入栈（直接赋值给object变量）
					mv.visitVarInsn(Opcodes.ALOAD, j + (isStatic ? 0 : 1));
				}
				mv.visitInsn(Opcodes.AASTORE);
				j++;
			}
		}
		
		@Override 
	    public void visitMaxs(int maxStack, int maxLocals) {
	    	//System.out.println("maxStack = " +maxStack+ " maxLocals = "+maxLocals);
	    	//System.out.println("this.maxStack = "+this.maxStack);
			if (!methodName.equals("<init>")) {
				// 标志：try块结束
				mv.visitLabel(to);
				// 异常表要加到最后，保证我们添加的异常是优先级最低的
				mv.visitTryCatchBlock(from, to, target, "java/lang/Exception");

				// 标志：catch块开始位置
				mv.visitLabel(target);
				Object[] locals = new Object[barrayIndex + 1];
				for (int i = 0; i < barrayIndex; i++) {
					// 占位符，不确定用什么值，姑且用top，反正不会报错就行
					locals[i] = Opcodes.TOP;
				}
				locals[barrayIndex] = "[Z";
				mv.visitFrame(Opcodes.F_NEW, barrayIndex + 1, locals, 1, new Object[] { "java/lang/Exception" });

				// insertPrintInsn(fullMethodName);

				//把异常传递出去
				mv.visitInsn(Opcodes.DUP);
				//mv.visitInsn(Opcodes.ACONST_NULL);
				mv.visitLdcInsn(fullMethodName);
				mv.visitVarInsn(Opcodes.ALOAD, barrayIndex);
				mv.visitMethodInsn(Opcodes.INVOKESTATIC,
								"com/github/rdagent/transformer/intercepter/AppIntercepter",
								"onMethodOut",
								"(Ljava/lang/Object;Ljava/lang/String;[Z)V",
								false);

				// 抛出异常
				mv.visitInsn(Opcodes.ATHROW);
			}
		    //ClassWriter使用了COMPUTE_MAXS，这里参数值会忽略，可以填任意值，但一定要调一下visitMaxs方法，否则不会自动计算
		    mv.visitMaxs(maxStack, maxLocals);
			/*if(aa.stack==null) {
				maxStack = Math.max(4, maxStack);
				mv.visitMaxs(maxStack+this.maxStack, maxLocals);
			}else {
				maxStack = Math.max(aa.stack.size() + 4, maxStack);
				mv.visitMaxs(Math.max(maxStack, this.maxStack), maxLocals);
			}*/
	    }
		
		@Override
		public void visitLabel(Label label) {
			lastLabel = label;
			//System.out.println(label.toString());
			mv.visitLabel(label);
		}
		
		@Override
		public void visitFrame(int type, int numLocal, Object[] local, int numStack, Object[] stack) {
			//处理操作数栈frame声明问题，stack map table entry， uninitialized类型的栈变量label可能在添加探针指令时发生变化，需要写入新的label
			Object[] newStack = new Object[stack.length];
			for(int i=0;i<stack.length;i++) {
				if(stack[i] instanceof Label) {
					Label uninitVarLabel = (Label)stack[i];
					//System.out.println("luanfei debug +++ old label : "+uninitVarLabel);
					if(stackMap.containsKey(uninitVarLabel)) {
						newStack[i] = stackMap.get(uninitVarLabel);
						//System.out.println("luanfei debug +++ new label " + newStack[i]);
						continue;
					}
				}
				newStack[i] = stack[i];
			}
			//一个frame node代表一个跳转的目的地，以下变量表示当前行是一个跳转目的地
			jumpDestination = true;
			/*for(Object o : newStack) {
				System.out.println("luanfei debug +++ new stack : "+o);
			}*/
			//System.out.println("numLocal = "+numLocal+" numStack = "+numStack);
			mv.visitFrame(type, numLocal, local, numStack, newStack);
		}
		
		/*private void insertPrintInsn(String message) {
			mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
			mv.visitLdcInsn(message);
			mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, 
					"java/io/PrintStream", 
					"println", 
					"(Ljava/lang/String;)V",
					false);
		}*/

	}

}
