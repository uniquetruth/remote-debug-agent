package com.github.rdagent.annontation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * a tag means a class should be loaded by system classloader, 
 * because it will be used both in premain phase and application phase
 * @author uniqueT
 *
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface BothUsing {
	/*
	 * 
	 */
}
