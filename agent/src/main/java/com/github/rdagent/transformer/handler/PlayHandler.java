package com.github.rdagent.transformer.handler;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import com.github.rdagent.Constants;
import com.github.rdagent.transformer.AbstractHandler;
import com.github.rdagent.transformer.bytecode.PlayVisitor;

/**
 * inject Play Frameworks Action.
 * it's above the Akka or Netty thing
 * @author uniqueT
 *
 */
public class PlayHandler extends AbstractHandler {

	@Override
	public boolean filterClassName(String className) {
		return "play/api/mvc/Action".equals(className);
	}

	@Override
	public byte[] process(String _className, byte[] classfileBuffer, ClassLoader loader) {
		ClassReader cr = new ClassReader(classfileBuffer);
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		PlayVisitor pv = new PlayVisitor(Constants.asmApiVersion, cw);
		cr.accept(pv, ClassReader.EXPAND_FRAMES);
		return cw.toByteArray();
	}

}
