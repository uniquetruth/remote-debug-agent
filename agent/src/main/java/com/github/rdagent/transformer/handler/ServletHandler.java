package com.github.rdagent.transformer.handler;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import com.github.rdagent.Constants;
import com.github.rdagent.transformer.AbstractHandler;
import com.github.rdagent.transformer.bytecode.ServletVisitor;

/**
 * inject HttpServlet.service(HttpServletRequest, HttpServletResponse)
 * 
 * @author uniqueT
 *
 */
public class ServletHandler extends AbstractHandler{
	
	//most framework and middleware use standard java servlet
	private String javaServlet = "javax/servlet/http/HttpServlet";
	//weblogic may use this
	private String wlServlet = "weblogic/servlet/ServletServlet";

	@Override
	public boolean filterClassName(String className) {
		return javaServlet.equals(className) || wlServlet.equals(className);
	}

	@Override
	public byte[] process(String className, byte[] classfileBuffer) {
		ClassReader cr = new ClassReader(classfileBuffer);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        ServletVisitor tv = new ServletVisitor(Constants.asmApiVersion, cw);
        cr.accept(tv, ClassReader.EXPAND_FRAMES);
		return cw.toByteArray();
	}

}
