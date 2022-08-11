package com.github.rdagent.transformer.handler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
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
	private List<Scope> includeScopes = new ArrayList<Scope>();
	private List<Scope> excludeScopes = new ArrayList<Scope>();

	public AppHandler() {
		for (String s : AgentOptions.getScopes()) {
			// can not use annotation scope in the includes
			if (s.startsWith("@")) {
				continue;
			}
			includeScopes.add(new Scope(s));
		}
		for (String s : AgentOptions.getExScopes()) {
			excludeScopes.add(new Scope(s));
		}
	}

	private boolean analyseClass(byte[] classfileBuffer, String className) {
		try {
			ClassReader cr = new ClassReader(classfileBuffer);
			ClassNode cn = new ClassNode();
			cr.accept(cn, 0);

			if (cn.visibleAnnotations != null) {
				for (AnnotationNode an : cn.visibleAnnotations) {
					if (exAnnoMatcher(an.desc)) {
						return false;
					}
				}
			}
			if (cn.invisibleAnnotations != null) {
				for (AnnotationNode an : cn.invisibleAnnotations) {
					if (exAnnoMatcher(an.desc)) {
						return false;
					}
				}
			}

			List<MethodNode> mns = cn.methods;
			for (MethodNode mn : mns) {
				analyseMethod(mn, className.replaceAll("/", "."));
			}
			IPmap.recordMethodLine(methodLineMap);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	private void analyseMethod(MethodNode mn, String className) {
		String returnType = (Type.getReturnType(mn.desc)).getClassName();
		String methodName = returnType + " " + className + "." + mn.name
				+ getStringParams(Type.getArgumentTypes(mn.desc));
		// System.out.println(methodName);

		ArrayList<Integer> lineNumberList = new ArrayList<Integer>();
		ArrayList<Integer> insnOffsetList = new ArrayList<Integer>();
		int offset = 0, lineNum = 0;
		boolean jumpFlag = false;
		Iterator<AbstractInsnNode> i = mn.instructions.iterator();
		while (i.hasNext()) {
			AbstractInsnNode inode = i.next();
			// System.out.println(inode.getClass().getName());
			if (inode instanceof LineNumberNode) {
				// store counting of last line's instructions
				if (offset > 0 && lineNum > 0) {
					insnOffsetList.add(offset);
					// store last line number. If it contains jumping instructions, store as
					// positive number
					// if not, store as negative number
					lineNumberList.add(jumpFlag ? lineNum : -lineNum);
				}
				LineNumberNode node = (LineNumberNode) inode;
				// System.out.println(node.line+ " "+node.start.getLabel().info);
				lineNum = node.line;
				offset = 0;
				jumpFlag = false;
			} else if (inode.getClass().getName().endsWith("InsnNode")) {
				// if true means it's one of the 13 instructions defined by asm
				// count how many instructions in this line
				offset++;
				// do this line contains any instruction which may cause JUMPING
				if (inode instanceof JumpInsnNode || inode instanceof LookupSwitchInsnNode
						|| inode instanceof TableSwitchInsnNode) {
					jumpFlag = true;
				} else if (inode instanceof InsnNode) {
					int opcode = inode.getOpcode();
					if ((opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) || opcode == Opcodes.ATHROW) {
						jumpFlag = true;
					}
				}
			}
		}
		insnOffsetList.add(offset);
		lineNumberList.add(jumpFlag ? lineNum : -lineNum);
		// for abstract method and interface method, lineNumberList=[0]
		// insnOffsetList=[0]
		// for method without LineNumberTable lineNumberList=[0] insnOffsetList=[n]
		methodLineMap.put(methodName, lineNumberList);
		methodOffsetMap.put(methodName, insnOffsetList);
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

	@Override
	public boolean filterClassName(String className) {
		return scopeMatcher(className) && !exscopeMatcher(className);
	}

	@Override
	public byte[] process(String className, byte[] classfileBuffer, ClassLoader loader) {
		// analyse class, build methodLineMap and methodOffsetMap
		if (!analyseClass(classfileBuffer, className)) {
			return null;
		}
		ClassReader cr = new ClassReader(classfileBuffer);
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		AppVisitor appVisitor = new AppVisitor(Constants.asmApiVersion, cw, methodOffsetMap, methodLineMap);

		cr.accept(appVisitor, ClassReader.EXPAND_FRAMES);
		byte[] data = cw.toByteArray();
		/*
		 * try (FileOutputStream fos = new FileOutputStream("./test.class")) {
		 * fos.write(data); //fos.close // no need, try-with-resources auto close }
		 * catch (Exception e) { e.printStackTrace(); }
		 */
		return data;
	}

	private boolean scopeMatcher(String className) {
		className = className.replaceAll("/", ".");
		// System.out.println("class name : "+className);
		for (Scope scope : includeScopes) {
			if (scope.match(className)) {
				return true;
			}
		}
		return false;
	}

	private boolean exscopeMatcher(String className) {
		className = className.replaceAll("/", ".");
		for (Scope scope : excludeScopes) {
			if (scope.match(className)) {
				return true;
			}
		}
		return false;
	}

	private boolean exAnnoMatcher(String className) {
		for (Scope scope : excludeScopes) {
			if (scope.matchAnno(className)) {
				return true;
			}
		}
		return false;
	}

	public int getPriority() {
		return 99;
	}

	// can both use String or regexString as scope
	private class Scope {
		// ignore . and $ , because they are legal characters in Class name
		private Pattern isRegex = Pattern.compile("[\\^*?+\\-{}\\[\\]\\|\\\\]");

		private Pattern regex = null;
		private String annoScope = null;
		private String nomalString = null;

		private Scope(String s) {
			Matcher m = isRegex.matcher(s);
			if (m.find()) {
				// s = s.replace(".", "\\."); //treat . as normal . in regex and nomal string both
				s = s.replace("$", "\\$");
				regex = Pattern.compile(s);
			} else if (s.startsWith("@")) {
				annoScope = s.substring(1);
			} else {
				nomalString = s;
			}
		}

		private boolean match(String className) {
			if (regex != null) {
				Matcher m = regex.matcher(className);
				return m.find();
			} else if (annoScope != null) {
				return false;
			} else {
				return className.startsWith(nomalString);
			}
		}

		private boolean matchAnno(String annoInClass) {
			if (annoScope == null) {
				return false;
			} else {
				if (annoScope.contains(".")) { // full name
					return annoInClass.substring(1, annoInClass.length() - 1).replace("/", ".").equals(annoScope);
				} else { // simple name
					return annoInClass.substring(annoInClass.lastIndexOf("/") + 1, annoInClass.length() - 1)
							.replace("/", ".").equals(annoScope);
				}
			}
		}

	}
}
