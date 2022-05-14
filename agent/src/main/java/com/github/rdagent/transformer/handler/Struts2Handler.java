package com.github.rdagent.transformer.handler;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import com.github.rdagent.Constants;
import com.github.rdagent.transformer.AbstractHandler;
import com.github.rdagent.transformer.bytecode.Struts2Visitor;

public class Struts2Handler extends AbstractHandler{
	
	//struts2 deals HttpServletRequest in filter
	private String struts2 = "org/apache/struts2/dispatcher/Dispatcher";

	@Override
	public boolean filterClassName(String className) {
		return struts2.equals(className);
	}

	@Override
	public byte[] process(String className, byte[] classfileBuffer) {
		ClassReader cr = new ClassReader(classfileBuffer);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        Struts2Visitor tv = new Struts2Visitor(Constants.asmApiVersion, cw);
        cr.accept(tv, ClassReader.EXPAND_FRAMES);
		return cw.toByteArray();
	}

}
