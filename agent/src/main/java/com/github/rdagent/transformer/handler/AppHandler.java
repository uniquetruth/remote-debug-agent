package com.github.rdagent.transformer.handler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;

import com.github.rdagent.AgentOptions;
import com.github.rdagent.Constants;
import com.github.rdagent.transformer.AbstractHandler;
import com.github.rdagent.transformer.bytecode.AppVisitor;
import com.github.rdagent.transformer.intercepter.IPmap;

public class AppHandler extends AbstractHandler {
	
	private Map<String, ArrayList<Integer>> methodLineMap = new HashMap<String, ArrayList<Integer>>();
	private Map<String, ArrayList<Integer>> methodOffsetMap = new HashMap<String, ArrayList<Integer>>();
	
	public AppHandler() {
		//System.out.println("cl in AppHandler : "+AppHandler.class.getClassLoader());
	}
    
    private boolean analyseClass(byte[] classfileBuffer, String className) {
    	try {
    		ClassReader cr = new ClassReader(classfileBuffer);
    		ClassNode cn = new ClassNode();
    		cr.accept(cn, 0);
    		List<MethodNode> mns = cn.methods;
			for(MethodNode mn : mns ) {
				analyseMethod(mn, className.replaceAll("/", "."));
			}
			IPmap.recordMethodLine(methodLineMap);
			return true;
    	}catch(Exception e) {
    		return false;
    	}
    }
    
    private void analyseMethod(MethodNode mn, String className) {
    	String returnType = (Type.getReturnType(mn.desc)).getClassName();
    	String methodName = returnType + " " + className+"."+mn.name+getStringParams(Type.getArgumentTypes(mn.desc));	
    	//System.out.println(methodName);
    	
    	ArrayList<Integer> lineNumberList = new ArrayList<Integer>();
    	ArrayList<Integer> insnOffsetList = new ArrayList<Integer>();
    	int offset=0, lineNum = 0;
    	boolean jumpFlag = false;
		Iterator<AbstractInsnNode> i = mn.instructions.iterator();
		while (i.hasNext()) {
			AbstractInsnNode inode = i.next();
			//System.out.println(inode.getClass().getName());
			if(inode instanceof LineNumberNode) {
				//store counting of last line's instructions
				if(offset>0 && lineNum>0) {
					insnOffsetList.add(offset);
					//store last line number. If it contains jumping instructions, store as positive number
					//if not, store as negative number
					lineNumberList.add(jumpFlag ? lineNum : -lineNum);
				}
				LineNumberNode node = (LineNumberNode)inode;
				//System.out.println(node.line+ " "+node.start.getLabel().info);				
				lineNum = node.line;
				offset = 0;
				jumpFlag = false;
			}else if(inode.getClass().getName().endsWith("InsnNode")) {
				//if true means it's one of the 13 instructions defined by asm
				//count how many instructions in this line
				offset++;
				//do this line contains any instruction which may cause JUMPING
				if(inode instanceof JumpInsnNode || inode instanceof LookupSwitchInsnNode
						|| inode instanceof TableSwitchInsnNode) {
					jumpFlag = true;
				}else if(inode instanceof InsnNode) {
					int opcode = inode.getOpcode();
					if((opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) || opcode == Opcodes.ATHROW) {
						jumpFlag = true;
					}
				}
			}
		}
		insnOffsetList.add(offset);
		lineNumberList.add(jumpFlag ? lineNum : -lineNum);
		//for abstract method and interface method, lineNumberList=[0]
		methodLineMap.put(methodName, lineNumberList);
		methodOffsetMap.put(methodName, insnOffsetList);
    }

	private String getStringParams(Type[] types) {
		StringBuilder sb = new StringBuilder("(");
		for(Type type : types) {
			sb.append(type.getClassName()).append(", ");
		}
		if(sb.length()>2)
			sb.setLength(sb.length()-2);
		sb.append(")");
		return sb.toString();
	}

	@Override
	public boolean filterClassName(String className) {
		return scopeMatcher(className) && !exscopeMatcher(className);
	}

	@Override
	public byte[] process(String className, byte[] classfileBuffer) {
		//analyse class, build methodLineMap and methodOffsetMap
    	if(!analyseClass(classfileBuffer, className)) {
    		return null;
    	}
    	ClassReader cr = new ClassReader(classfileBuffer);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        AppVisitor appVisitor = new AppVisitor(Constants.asmApiVersion, cw, methodOffsetMap, methodLineMap);

        cr.accept(appVisitor, ClassReader.EXPAND_FRAMES);
        return cw.toByteArray();
	}
	
	private boolean scopeMatcher(String className) {
		className = className.replaceAll("/", ".");
		//System.out.println("class name : "+className);
		List<String> sl = AgentOptions.getScopes();
		for(String scope : sl) {
			if(className.startsWith(scope)) {
				return true;
			}
		}
		return false;
	}
	
	private boolean exscopeMatcher(String className) {
		className = className.replaceAll("/", ".");
		List<String> esl = AgentOptions.getExScopes();
		for(String scope : esl) {
			if(className.startsWith(scope)) {
				return true;
			}
		}
		return false;
	}
	
	public int getPriority() {
		return 99;
	}
}
