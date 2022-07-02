package com.github.rdagent.transformer.handler;

import java.util.List;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import com.github.rdagent.Constants;
import com.github.rdagent.annontation.SelfRegister;
import com.github.rdagent.transformer.AbstractHandler;
import com.github.rdagent.transformer.bytecode.JdbcVisitor;

/**
 * As an adapter interface to support all jdbc drivers
 * users can extend this class to apply sql function to any custom RDBMS
 * @author uniqueT
 *
 */
@SelfRegister
public abstract class DefaultJdbcAdapter extends AbstractHandler {

	/**
	 * please override this method, return the prefix of your jdbc driver package like "com.mysql", "oracle.jdbc"
	 */
	@Override
	public final boolean filterClassName(String className) {
		List<String> nameList = injectPackageNameList();
		for(String name : nameList) {
			if(className.startsWith(name.replace(".", "/"))) {
				return true;
			}
		}
		return false;
	}

	@Override
	public final byte[] process(String _className, byte[] classfileBuffer, ClassLoader loader) {
		ClassReader cr = new ClassReader(classfileBuffer);
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		JdbcVisitor jv = new JdbcVisitor(Constants.asmApiVersion, cw, loader);
		cr.accept(jv, ClassReader.EXPAND_FRAMES);
		return cw.toByteArray();
	}
	
	protected abstract List<String> injectPackageNameList();
	
	public int getPriority() {
		return 10;
	}

}
