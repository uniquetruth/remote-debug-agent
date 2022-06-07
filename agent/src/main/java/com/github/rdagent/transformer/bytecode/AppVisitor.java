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
			//don't transform method without line number table
			if(at.isMethodNoLines) {
				return mv;
			}
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
		private Label lastLineLabel = null;
		private Map<Label, Label> stackMap = new HashMap<Label, Label>();
		
		boolean isMethodNoLines = false;

		public MethodEnhanceVisitor(int api, int access, String name, String desc, MethodVisitor methodVisitor) {
			super(api, methodVisitor);
			methodName = name;
			isStatic = (access & Opcodes.ACC_STATIC) != 0;
			parameters = Type.getArgumentTypes(desc);
			String returnType = (Type.getReturnType(desc)).getClassName();
			fullMethodName = returnType + " " +owner.replaceAll("/", ".") + "." + methodName + getStringParams(parameters);
			offsetList = methodOffsetMap.get(fullMethodName);
			methodLines = methodLineMap.get(fullMethodName);
			//method without line number table
			if(methodLines.size()==1 && methodLines.get(0)==0) {
				isMethodNoLines = true;
			}
		}

		public void visitCode() {
			//System.out.println("fullMethodName = "+fullMethodName);
			mv.visitCode();
			// ==============at the beginning of this method, invoke AppInterceptor.onMethodIn to record==========
			// new Object[parameters.length]
			mv.visitIntInsn(Opcodes.BIPUSH, parameters.length);
			mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");

			oarrayIndex = lvs.newLocal(Type.getObjectType("[Ljava/lang/Object;"));
			//System.out.println("varNumber = " + oarrayIndex);
			mv.visitVarInsn(Opcodes.ASTORE, oarrayIndex);

			// put parameters into Object[]
			installParams(oarrayIndex);

			mv.visitLdcInsn(fullMethodName);
			mv.visitVarInsn(Opcodes.ALOAD, oarrayIndex);
			// invoke AppInterceptor.onMethodIn
			mv.visitMethodInsn(Opcodes.INVOKESTATIC,
					"com/github/rdagent/transformer/intercepter/AppIntercepter",
					"onMethodIn",
					"(Ljava/lang/String;[Ljava/lang/Object;)V", false);
			// ==============AppInterceptor.onMethodIn over=======stack height is 4===================
			
			// =============begin to create probe, in visitCode method, we have to initialize boolean[]
			//new boolean[], offsetList.size() is code line count in this method
			if(offsetList.size() > Byte.MAX_VALUE/2) {
				mv.visitIntInsn(Opcodes.SIPUSH, offsetList.size()*2);
			}else {
				mv.visitIntInsn(Opcodes.BIPUSH, offsetList.size()*2);
			}
	        mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_BOOLEAN);
	        barrayIndex = lvs.newLocal(Type.getObjectType("[Z"));
	        mv.visitVarInsn(Opcodes.ASTORE, barrayIndex);
	        //For the line that only has one instruction, need to set probe[endPointer]=true
	        for(int i=0;i<offsetList.size();i++) {
	        	if(offsetList.get(i)==1 && methodLines.get(i)>0) { //ignore sequential block
	        		int endPointer = i * 2 + 1;
	        		mv.visitVarInsn(Opcodes.ALOAD, barrayIndex);
	        		if(endPointer > Byte.MAX_VALUE) {
	        			mv.visitIntInsn(Opcodes.SIPUSH, endPointer);
	        		}else {
	        			mv.visitIntInsn(Opcodes.BIPUSH, endPointer);
	        		}
	        		mv.visitInsn(Opcodes.ICONST_1); //boolean true equals int 1 in bytecode
	        		mv.visitInsn(Opcodes.BASTORE);
	        	}
	        }
			// ==============probe createing over===========max stack = 4=====================
	        
	        //==============add try-catch to the whole method. here add try, in visitMaxs add catch======================
	        //construct method is a little different, there is a default super instruction, can't be surrounded by try-catch
	        //though we can add try after super instruction, it's quite a complicate job. So I decide to ignore <init> for now
	        if(!methodName.equals("<init>")) {
	        	mv.visitLabel(from);
	        }
	        //==========try over===============it's not a instruction, no effect to stack height==========================
	        
	        //maxStack = 4;
		}
		
		//write value to probe in following 13 visit(X)insn methods
		public void visitInsn(int opcode) {
			//some of classes that is be created by dynamic framework may have a little "extraordinary" structure.
			//For these classes, asm may call visitInsn before visitLineNumber, so we have to check lineIndex here
			if(lineIndex == -1) {
				mv.visitInsn(opcode);
				return;
			}
			//condition of line start probe. It's the first instruction of a line
			if(insnOffset==0) {
				if(methodLines.get(lineIndex)>0 //1. this line contains "jump" instruction
					|| lineIndex==0 //2. this is the first line of a method
					|| methodLines.get(lineIndex-1)>0 //3. this is the first line of a sequential block
					|| jumpDestination) { //4. this line is a jumping destination
					writeStartProbe(opcode);
				}
			//condition of line end probe
			}else if((insnOffset+1)==offsetList.get(lineIndex) //1. It's the last instruction of a line
					&& methodLines.get(lineIndex)>0){ //2. this line contains "jump" instruction
				writeEndProbe(opcode);
			}
	    	
	    	insnOffset++;
	    	//before returning (don't process Opcodes.ATHROW, use try-catch block to do it)
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
	    	//if this method be called, means a new code line begins
	    	//System.out.println("visitLineNumber line= "+line);
	    	//if a line doesn't contain insn(no visitInsn between two visitLineNumber), 
	    	//AppHandler doen't count methodLines and offsetList
	    	if(!start.equals(lastLineLabel)) {
	    		lineIndex++;
	    	}
	    	insnOffset = 0;
	    	jumpDestination = false;
	    	lastLineLabel = start;
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
    		//if a new instruction follows a probe, the uninitialized var in stack map table entry may not match to the right label
    		//so we need to put a new label here, and remember the old one, when we encounter the relative frame, we can replace old label by new's
    		if(opcode == Opcodes.NEW || opcode == Opcodes.NEWARRAY 
	    			|| opcode == Opcodes.ANEWARRAY || opcode == Opcodes.MULTIANEWARRAY) {
    			Label newLabel = new Label();
    			stackMap.put(lastLabel, newLabel);
    			//System.out.println("debug +++ replace "+lastLabel+" to "+newLabel);
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
    			//System.out.println("uniqueT debug +++ replace "+lastLabel+" to "+newLabel);
	    		mv.visitLabel(newLabel);
	    	}
		}
		
		// put parameter into Object[]
		private void installParams(int oarrayIndex) {
			int j = 0;
			// deal every parameter orderly
			for (int i = 0; i < parameters.length; i++) {
				// array pointer into stack
				mv.visitVarInsn(Opcodes.ALOAD, oarrayIndex);
				// index into stack
				mv.visitIntInsn(Opcodes.BIPUSH, i);

				Type t = parameters[i];
				String typeName = t.getClassName();
				// value into stack, for primitive type, we have to convert it to Reference Type
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
					// be careful, for long and double type, class use 2 slots to store them
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
					// Reference Type
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
				// end of try clock
				mv.visitLabel(to);
				// exception table
				mv.visitTryCatchBlock(from, to, target, "java/lang/Exception");

				// beginning of catch block
				mv.visitLabel(target);
				Object[] locals = new Object[barrayIndex + 1];
				for (int i = 0; i < barrayIndex; i++) {
					// I'm not very ensure here...
					locals[i] = Opcodes.TOP;
				}
				locals[barrayIndex] = "[Z";
				mv.visitFrame(Opcodes.F_NEW, barrayIndex + 1, locals, 1, new Object[] { "java/lang/Exception" });

				// insertPrintInsn(fullMethodName);

				//transfer exception to our Intercepter
				mv.visitInsn(Opcodes.DUP);
				//mv.visitInsn(Opcodes.ACONST_NULL);
				mv.visitLdcInsn(fullMethodName);
				mv.visitVarInsn(Opcodes.ALOAD, barrayIndex);
				mv.visitMethodInsn(Opcodes.INVOKESTATIC,
								"com/github/rdagent/transformer/intercepter/AppIntercepter",
								"onMethodOut",
								"(Ljava/lang/Object;Ljava/lang/String;[Z)V",
								false);

				// throw exception out
				mv.visitInsn(Opcodes.ATHROW);
			}
		    //we use COMPUTE_MAXS in ClassWriter, so the parameter values are not important,
			//we can use any value here, just remember to invoke visitMaxs method in order to trigger computing
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
			//process operant stack problem. in stack map table entry,the uninitialized label should be replace by the new one
			Object[] newStack = new Object[stack.length];
			for(int i=0;i<stack.length;i++) {
				if(stack[i] instanceof Label) {
					Label uninitVarLabel = (Label)stack[i];
					//System.out.println("debug +++ old label : "+uninitVarLabel);
					if(stackMap.containsKey(uninitVarLabel)) {
						newStack[i] = stackMap.get(uninitVarLabel);
						//System.out.println("debug +++ new label " + newStack[i]);
						continue;
					}
				}
				newStack[i] = stack[i];
			}
			//a frame node implicit a jumping destination
			jumpDestination = true;
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
