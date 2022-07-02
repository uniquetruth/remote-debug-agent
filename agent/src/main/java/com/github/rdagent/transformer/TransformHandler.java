package com.github.rdagent.transformer;

public interface TransformHandler {
	
	/**
	 * determine should a class be transformed by this handler
	 * @param className name of the class being loaded. E.g. java/lang/String
	 * @return
	 */
	public boolean filterClassName(String className);
	
	/**
	 * the smaller the higher
	 * @return
	 */
	public int getPriority();
	
	/**
	 * transform the class bytes.<br/>
	 * see also {@link java.lang.instrument.ClassFileTransformer#transform}
	 * @param _className
	 * @param _classfileBuffer
	 * @param loader
	 * @return
	 */
	public byte[] process(String _className, byte[] _classfileBuffer, ClassLoader loader);

}
