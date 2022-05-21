package com.github.rdagent.transformer;

/**
 * must have a non value constructor
 * @author uniqueT
 *
 */
public abstract class AbstractHandler implements TransformHandler{
	
	public AbstractHandler() {
		
	}
	
	/**
	 * the smaller the higher. default is 0
	 */
	public int getPriority() {
		return 0;
	}

}
