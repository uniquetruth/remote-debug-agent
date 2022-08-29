package com.github.rdagent.loader;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Vector;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Use this classloader to load most agent's classes and third-party classes in this agent's lib directory.<br/>
 * It can isolate agent's third-party jars from your application third-party jars.<br/>
 * If a class used {@link com.github.rdagent.annontation.BothUsing} annotation, this classloader will ignore it,
 * Then system classloader will load this class so that application program can also use it
 * @author uniqueT
 *
 */
public class Agent3rdPartyClassloader extends ClassLoader{
	private String classDir;
	private String thisJar;
	private ClassLoader parent;
	private Map<File, HashSet<String>> jarEntryCache = new HashMap<File, HashSet<String>>();
	private Map<File, HashSet<String>> resourceEntryCache = new HashMap<File, HashSet<String>>();
	private Set<Class<?>> selfRegisterClasses = new HashSet<Class<?>>();
	private static Agent3rdPartyClassloader cl;
	
	/**
	 * Initialize a internal Agent3rdPartyClassloader instance with lib directory
	 * @param dir
	 * @param parent - should be SystemClassloader(e.g. sun.misc.Launcher$AppClassLoader)
	 */
	public static void init(String path, ClassLoader parent) {
		cl = new Agent3rdPartyClassloader(path, parent);
	}
	
	/**
	 * Get Agent3rdPartyClassloader instance
	 * @return
	 */
	public static Agent3rdPartyClassloader getClassloader() {
		return cl;
	}
	
	private Agent3rdPartyClassloader(String path, ClassLoader parent) {
		super(parent);
		this.parent = parent;
		int ladIndex = path.lastIndexOf("/");
		classDir = path.substring(0, ladIndex);
		thisJar = path.substring(ladIndex+1, path.length());
		initJarCache();
	}
	
	private void initJarCache() {
		File d = new File(classDir);
		if(d.listFiles()==null) {
			return;
		}
		//scan all jars in classDir, put every class entry into jarEntryCache
		for(File f : d.listFiles()) {
			//System.out.println("jar name : "+f.getName());
			if(f.getName().endsWith(".jar")) {
				HashSet<String>[] sets = scanPackages(f);
				if(sets[0].size() > 0) {
					jarEntryCache.put(f, sets[0]);
				}
				if(sets[1].size() > 0) {
					resourceEntryCache.put(f, sets[1]);
				}
			}
		}
		//find SelfRegister Handlers
		Set<Class<?>> selfSuperClassSet = findSelfRegister();
		//scan all jars in classDir again, put self register classes into selfRegisterClasses
		//exclude some 3rd party jars, increasing load speed
		Set<String> internal3rdPartyJar = new HashSet<String>();
		internal3rdPartyJar.add("org/objectweb/asm");
		internal3rdPartyJar.add("com/google/gson");
		internal3rdPartyJar.add("org/eclipse/jetty");
		internal3rdPartyJar.add("javax/servlet");
		for(File f : d.listFiles()) {
			if(f.getName().endsWith(".jar")) {
				findSelfRegisterClass(f, selfSuperClassSet, internal3rdPartyJar);
			}
		}
	}
	
	private boolean containInternalJar(Set<String> set, String str) {
		for(String s : set) {
			if(str.startsWith(s)){
				return true;
			}
		}
		return false;
	}
	
	private HashSet<String>[] scanPackages(File jarFile){
		@SuppressWarnings("unchecked")
		HashSet<String>[] result = new HashSet[2];
		JarFile jFile = null;
		HashSet<String> classResult= new HashSet<String>();
		HashSet<String> resourceResult= new HashSet<String>();
		try {
			jFile = new JarFile(jarFile);
			Enumeration<JarEntry> entries = jFile.entries();
			while(entries.hasMoreElements()) {
				JarEntry e = entries.nextElement();
				if(e.getName().endsWith(".class")) {
					classResult.add(e.getName());
				}else if(!e.getName().endsWith("/")){
					resourceResult.add(e.getName());
				}
			}
			result[0] = classResult;
			result[1] = resourceResult;
			return result;
		}catch(IOException e) {
			return null;
		}finally {
			try {
				if(jFile!=null) {
					jFile.close();
				}
			}catch(IOException e) {
				return null;
			}
		}
	}
	
	private File isInJarCache(String className) {
		String classPath = className.replace(".", "/")+".class";
		Iterator<Entry<File, HashSet<String>>> it = jarEntryCache.entrySet().iterator();
		while(it.hasNext()) {
			Entry<File, HashSet<String>> e = it.next();
			if(e.getValue().contains(classPath)) {
				return e.getKey();
			}
		}
		return null;
	}
	
	@SuppressWarnings("unchecked")
	private Set<Class<?>> findSelfRegister() {
		Set<Class<?>> superClassSet = new HashSet<Class<?>>();
		JarFile agentJar = null;
		try {
			agentJar = new JarFile(new File(classDir+"/"+thisJar));
			Enumeration<JarEntry> entries = agentJar.entries();
			while(entries.hasMoreElements()) {
				JarEntry e = entries.nextElement();
				if(e.getName().endsWith(".class") 
						&& !"com/github/rdagent/loader/Agent3rdPartyClassloader.class".equals(e.getName())) {
					if(checkUsingAnno(loadFromJarEntry(agentJar, e), 
							"Lcom/github/rdagent/annontation/SelfRegister;")) {
						Class<?> c = loadClass(e.getName().replace("/", ".").substring(0, e.getName().length()-6));
						// check annotation formally. can't use c.isAnnotationPresent(SelfRegister.class) here,
						// because this class's classloader isn't Agent3rdPartyClassloader
						Class<?> selfClass = loadClass("com.github.rdagent.annontation.SelfRegister");
						if(c.isAnnotationPresent((Class<? extends Annotation>)selfClass)) {
							// check if it was a TransformHandler
							Class<?> handlerClass = loadClass("com.github.rdagent.transformer.TransformHandler");
							if(handlerClass.isAssignableFrom(c)) {
								superClassSet.add(c);
							}
						}
					}
				}
			}
		} catch (IOException | ClassNotFoundException e) {
			e.printStackTrace();
		} finally {
			try {
				if(agentJar!=null) {
					agentJar.close();
				}
			}catch(IOException e) {
				e.printStackTrace();
			}
		}
		return superClassSet;
	}
	
	private void findSelfRegisterClass(File jarFile, Set<Class<?>> selfSuperClassSet, Set<String> internal3rdPartyJar) {
		Set<String> superClassNames = new HashSet<String>();
		for(Class<?> c : selfSuperClassSet) {
			superClassNames.add(c.getName().replace(".", "/")+".class");
		}
		JarFile jFile = null;
		try {
			jFile = new JarFile(jarFile);
			Enumeration<JarEntry> entries = jFile.entries();
			while(entries.hasMoreElements()) {
				JarEntry e = entries.nextElement();
				//don't check non-class files, super classes and classes that are used by this project
				if(!e.getName().endsWith(".class") || superClassNames.contains(e.getName())
						|| containInternalJar(internal3rdPartyJar, e.getName()) ) {
					continue;
				}
				if(containsU8l(superClassNames, loadFromJarEntry(jFile, e))) {
					Class<?> c = loadClass(e.getName().replace("/", ".").substring(0, e.getName().length()-6));
					// check if it was a TransformHandler
					Class<?> handlerClass = loadClass("com.github.rdagent.transformer.TransformHandler");
					if(handlerClass.isAssignableFrom(c)) {
						selfRegisterClasses.add(c);
					}
				}
			}
		}catch(IOException | ClassNotFoundException e) {
			e.printStackTrace();
		}finally {
			try {
				if(jFile!=null) {
					jFile.close();
				}
			}catch(IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	private boolean containsU8l(Set<String> superClassNames, byte[] classData) throws ClassNotFoundException {
		for(String superClassName : superClassNames) {
			String binaryName = superClassName.substring(0, superClassName.length()-6);
			if(checkUsingAnno(classData, binaryName)) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * break parent delegation, load from agent jar's directory firstly
	 */
	@Override
	protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException{
		synchronized (getClassLoadingLock(name)) {
            Class<?> c = findLoadedClass(name);
            if (c == null) {
            	if(Agent3rdPartyClassloader.class.getName().equals(name)) {
            		//never duplicate Agent3rdPartyClassloader
            		//make sure Agent3rdPartyClassloader itself is always loaded by SystemClassLoader
                	if(parent != null)
                		c = parent.loadClass(name); //use AppClassLoader
                	else
                		c = super.loadClass(name, resolve); //use JVM's BootClassLoader
                } else {
                	c = findClass(name);
                	/* Can't check BothUsing annotation here, because if a class has already been loaded above
                	 * and we find it contains BothUsing annotation here, then we try to use parent classloader
                	 * to load it again, JVM will throw a LinkageError telling something like you can't duplicate class definition.
                	 * The problem is in defineClass method, which is a native method we can't know what exactly happens in it.
                	 * Though we load a class by different loaders, maybe they invoke the same native method 
                	 * so JVM determines we try to duplicate one class's definition. 
                	 */
                	if(c == null) {
                		if(parent != null)
                			c = parent.loadClass(name);
                		else
                        	c = super.loadClass(name, resolve);
                	}
                }
            	if (resolve) {
            		resolveClass(c);
            	}
            }
            return c;
		}
	}
	
	/**
	 * 1. Check if className in jarEntryCache. If not, return null.<br/>
	 * 2. Load class byte[] data from jar file.<br/>
	 * 3. Check if the class used BothUsing annotation, if yes, return null.<br/>
	 * 4. Define class and return it.
	 */
	@Override
	protected Class<?> findClass(String className) throws ClassNotFoundException {
		File jar = isInJarCache(className);
		if(jar == null) {
			return null;
		}
		byte[] classData = null;
		try {
			classData = loadClassData(jar, className);
			if(classData!=null) {
				/* check if a class used BothUsing annotation.
				 * if true, it must be loaded by system classloader so that application program can also use it
				 * */
				if(checkUsingAnno(classData, "Lcom/github/rdagent/annontation/BothUsing;")) {
					return null;
				}
				return defineClass(className, classData, 0, classData.length);
			}
		}catch(IOException e) {
			throw new ClassNotFoundException("Exception occurs when finding "+className+" in "+classDir, e);
		}
		return null;
	}
	
	/**
	 * load class file data from jar file
	 * @param file
	 * @param className
	 * @return
	 * @throws IOException
	 */
	private byte[] loadClassData(File file, String className) throws IOException {
		JarFile jFile = null;
		try {
			jFile = new JarFile(file);
			String classPath = className.replace(".", "/")+".class";
			JarEntry entry = jFile.getJarEntry(classPath);
			if(entry!=null) {
				return loadFromJarEntry(jFile, entry);
			}
		}catch(IOException e) {
			throw e;
		}finally {
			if(jFile!=null) {
				jFile.close();
			}
		}
		return null;
	}
	
	private byte[] loadFromJarEntry(JarFile jFile, JarEntry entry) throws IOException {
		BufferedInputStream bis = null;
		ByteArrayOutputStream bos = null;
		int bufferSize = 1024;
		try {
			bis = new BufferedInputStream(jFile.getInputStream(entry), bufferSize*100);
			bos = new ByteArrayOutputStream(bufferSize);
			byte[] b = new byte[bufferSize];
			int n;
			while((n=bis.read(b))!=-1) {
				bos.write(b, 0, n);
			}
			return bos.toByteArray();
		}catch(IOException e) {
			throw e;
		}finally {
			if(bis!=null) {
				bis.close();
			}
			//no need to close ByteArrayOutputStream
		}
	}
	
	/**
	 * check if a class contains a specific annotation<br/><br/>
	 * We can't use asm or any other bytecode lib to check the annotation, because for isolating agent classes from application classes,
	 * all 3rd party classes should be loaded by this classloader. But if we try to load another class here, 
	 * JVM will throw a ClassCircularityError.<br/>
	 * So we have to check annotation directly with class byte[]. The form of class file is very strict. If we do a strict checking,
	 * there must be a lot of code to implement.<br/>
	 * So, for now I decided to just do a loose examination which is checking if the annotation's name was in CONSTANT_POOL. Maybe someday I can implement a
	 * fully responsible checking. 
	 * @param classData
	 * @param anno annotation's binary name. e.g. Lcom/github/rdagent/annontation/BothUsing;
	 * @return
	 * @throws ClassNotFoundException
	 */
	private boolean checkUsingAnno(byte[] classData, String anno) throws ClassNotFoundException {
		BufferedInputStream bis = null;
		byte[] u2 = new byte[2], u3 = new byte[3], u4 = new byte[4], u8 = new byte[8];
		try {
			bis = new BufferedInputStream(new ByteArrayInputStream(classData));
			//magic
			bis.read(u4);
			//minor_version
			bis.read(u2);
			//major_version
			bis.read(u2);
			//cp length
			bis.read(u2);
			int cpLength = byteArray2Int(u2);
			
			byte[] tag = new byte[1];
			for(int i=1;i<cpLength;i++) { //CONSTANT_POOL's index starts from 1
				
				bis.read(tag);
				switch(tag[0]) {
				case 1: //Utf8_info
					bis.read(u2);
					int u8l = byteArray2Int(u2);
					byte[] strByte = new byte[u8l];
					bis.read(strByte);
					String str = new String(strByte, "utf-8");
					//System.out.println(str);
					if(anno.equals(str)){
						return true;
					}
					break;
				case 3: //Integer_info
					bis.read(u4);
					break;
				case 4: //Float_info
					bis.read(u4);
					break;
				case 5: //Long_info
					bis.read(u8);
					i++; //phantom index
					break;
				case 6: //Double_info
					bis.read(u8);
					i++; //phantom index
					break;
				case 7: //Class_info
					bis.read(u2);
					break;
				case 8: //String_info
					bis.read(u2);
					break;
				case 9: //Fieldref_info
					bis.read(u4);
					break;
				case 10: //Methodref_info
					bis.read(u4);
					break;
				case 11: //InterfaceMethodref_info
					bis.read(u4);
					break;
				case 12: //NameAndType_info
					bis.read(u4);
					break;
				case 15: //MethodHandle_info
					bis.read(u3);
					break;
				case 16: //MethodType_info
					bis.read(u2);
					break;
				case 17: //	Dynamic_info
					bis.read(u4);
					break;
				case 18: //InvokeDynamic_info
					bis.read(u4);
					break;
				case 19: //Module_info
					bis.read(u2);
					break;
				case 20: //Package_info
					bis.read(u2);
					break;
				default:
					//throw new ClassNotFoundException("unsupported class file structure");
					//do nothing, don't interrupt loading progress
					return false;
				}
			}
		}catch(IOException e) {
			e.printStackTrace();
		}finally {
			if(bis!=null) {
				try {
					bis.close();
				} catch (IOException e) {}
			}
		}
		return false;
	}
	
	private int byteArray2Int(byte[] byteArray) {
		int r = 0;
		for(int i=byteArray.length;i>0;i--) {
			//System.out.println(byteArray[i-1]);
			r += (byteArray[i-1]&0xff)*Math.pow(256, byteArray.length-i);
		}
		return r;
	}

	public Set<Class<?>> getSelfRegisterClasses() {
		return selfRegisterClasses;
	}
	
	public URL getResource(String name) {
        URL url = findResource(name);
        if(url == null) {
        	if(parent != null) {
        		url = parent.getResource(name);
        	}else {
        		url = super.getResource(name);
        	}
        }
        return url;
    }
	
	public Enumeration<URL> getResources(String name) throws IOException {
        return findResources(name);
    }
	
	protected URL findResource(String name) {
		//System.out.println("find resource: "+name);
		File f = isInResourceCache(name);
		if(f != null) {
			try {
				return new URL("jar:file:"+f.getAbsolutePath()+"!/"+name);
			} catch (MalformedURLException e) {
				e.printStackTrace();
			}
		}
        return null;
    }
	
	private File isInResourceCache(String name) {
		Iterator<Entry<File, HashSet<String>>> it = resourceEntryCache.entrySet().iterator();
		while(it.hasNext()) {
			Entry<File, HashSet<String>> e = it.next();
			if(e.getValue().contains(name)) {
				return e.getKey();
			}
		}
		return null;
	}
	
	protected Enumeration<URL> findResources(String name) throws IOException {
		Vector<URL> resource = new Vector<URL>();
		resource.add(findResource(name));
		
		return resource.elements();
	}

}
