package com.github.rdagent.transformer;

public interface TransformHandler {
	
	/**
	 * determine should a class be transformed by this handler
	 * @param className
	 * @return
	 */
	public boolean filterClassName(String className);
	
	/**
	 * the smaller the heigher
	 * @return
	 */
	public int getPriority();
	
	public byte[] process(String _className, byte[] _classfileBuffer);

}
