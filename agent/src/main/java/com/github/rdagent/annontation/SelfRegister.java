package com.github.rdagent.annontation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * a tag used by Agent3rdPartyClassloader, to find classes that should be focused in registering to AsmTransformer.<br/>
 * classes which use this annotation must be abstract classes, and the real classes 
 * that are registered to AsmTransformer should be their sub class.<br/>
 * E.g. {@link com.github.rdagent.transformer.handler.DefaultServletAdatper DefaultServletAdatper} use this annotation, 
 * {@link com.github.rdagent.transformer.handler.ServletHandler ServletHandler} extends DefaultServletAdatper, 
 * so ServletHandler will be registered to AsmTransformer automatically
 * @author uniqueT
 *
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SelfRegister {

}
