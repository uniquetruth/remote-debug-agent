package com.github.rdagent.loader;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
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
	private ClassLoader parent;
	private Map<File, HashSet<String>> jarEntryCache = new HashMap<File, HashSet<String>>();
	private static Agent3rdPartyClassloader cl;
	
	/**
	 * Initialize a internal Agent3rdPartyClassloader instance with lib directory
	 * @param dir
	 * @param parent - should be SystemClassloader(e.g. sun.misc.Launcher$AppClassLoader)
	 */
	public static void init(String dir, ClassLoader parent) {
		cl = new Agent3rdPartyClassloader(dir, parent);
	}
	
	/**
	 * Get Agent3rdPartyClassloader instance
	 * @return
	 */
	public static ClassLoader getClassloader() {
		return cl;
	}
	
	private Agent3rdPartyClassloader(String dir, ClassLoader parent) {
		super(parent);
		this.parent = parent;
		classDir = dir;
		initJarCache();
	}
	
	private void initJarCache() {
		File d = new File(classDir);
		if(d.listFiles()==null) {
			return;
		}
		for(File f : d.listFiles()) {
			//System.out.println("jar name : "+f.getName());
			if(f.getName().endsWith(".jar")) {
				HashSet<String> set = scanPackages(f);
				if(set != null) {
					jarEntryCache.put(f, scanPackages(f));
				}
			}
		}
	}
	
	private HashSet<String> scanPackages(File jarFile){
		BufferedInputStream bis = null;
		JarFile jFile = null;
		HashSet<String> result= new HashSet<String>();
		try {
			jFile = new JarFile(jarFile);
			Enumeration<JarEntry> entries = jFile.entries();
			while(entries.hasMoreElements()) {
				JarEntry e = entries.nextElement();
				if(e.getName().endsWith(".class")) {
					result.add(e.getName());
				}
			}
			return result;
		}catch(IOException e) {
			return null;
		}finally {
			try {
				if(bis!=null) {
					bis.close();
				}
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
	
	/**
	 * break parent delegation, load from agent jar's directory firstly
	 */
	@Override
	protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException{
		synchronized (getClassLoadingLock(name)) {
            Class<?> c = findLoadedClass(name);
            if (c == null) {
            	c = findClass(name);
            	/* Can't check BothUsing annotation here, because if a class has already been loaded above
            	 * and we find it contains BothUsing annotation here, then we try to use parent classloader
            	 * to load it again, JVM will throw a LinkageError telling something like you can't duplicate class definition.
            	 * The problem is in defineClass method, which is a native method we can't know what exactly happens in it.
            	 * Though we load a class by different loaders, maybe they invoke the same native method 
            	 * so JVM determines we try to duplicate one class's definition. 
            	 */
            	if(c == null) {
            		c = parent.loadClass(name);
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
				if(checkBothUsingAnno(classData)) {
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
		BufferedInputStream bis = null;
		ByteArrayOutputStream bos = null;
		JarFile jFile = null;
		int bufferSize = 1024;
		try {
			jFile = new JarFile(file);
			String classPath = className.replace(".", "/")+".class";
			JarEntry entry = jFile.getJarEntry(classPath);
			if(entry!=null) {
				bis = new BufferedInputStream(jFile.getInputStream(entry), bufferSize*100);
				bos = new ByteArrayOutputStream(bufferSize);
				byte[] b = new byte[bufferSize];
				int n;
				while((n=bis.read(b))!=-1) {
					bos.write(b, 0, n);
				}
				return bos.toByteArray();
			}
		}catch(IOException e) {
			throw e;
		}finally {
			if(bis!=null) {
				bis.close();
			}
			if(jFile!=null) {
				jFile.close();
			}
		}
		return null;
	}	
	
	/**
	 * check if a class contains BothUsing annotation<br/><br/>
	 * We can't use asm or any other bytecode lib to check the annotation, because for isolating agent classes from application classes,
	 * all 3rd party class should be loaded by this classloader. But if we try to load another class here, 
	 * JVM will throw a ClassCircularityError.<br/>
	 * So we have to check annotation directly with class byte[]. The form of class file is very strict. If we do a strictly checking,
	 * there must be a lot of code to implement.<br/>
	 * So, for now I decided to just do a loose examination which is checking if it was in CONSTANT_POOL. Maybe someday I can implement a
	 * fully responsible checking. 
	 * @param classData
	 * @return
	 * @throws ClassNotFoundException
	 */
	private boolean checkBothUsingAnno(byte[] classData) throws ClassNotFoundException {
		BufferedInputStream bis = null;
		byte[] u2 = new byte[2], u3 = new byte[3], u4 = new byte[4], u8 = new byte[8];
		try {
			bis = new BufferedInputStream(new ByteArrayInputStream(classData));
			//magic
			bis.read(u4);
			//minor_version major_version
			bis.read(u4);
			//cp length
			bis.read(u2);
			int cpLength = byteArray2Int(u2);
			
			byte[] tag = new byte[1];
			for(int i=0;i<cpLength;i++) {
				
				bis.read(tag);
				if(tag[0]==1) { //Utf8_info
					bis.read(u2);
					int u8l = byteArray2Int(u2);
					byte[] strByte = new byte[u8l];
					bis.read(strByte);
					String str = new String(strByte, "utf-8");
					//System.out.println(str);
					if("Lcom/github/rdagent/annontation/BothUsing;".equals(str)){
						return true;
					}
				}else if(tag[0]==3) {
					bis.read(u4);
				}else if(tag[0]==4) {
					bis.read(u4);
				}else if(tag[0]==5) {
					bis.read(u8);
				}else if(tag[0]==6) {
					bis.read(u8);
				}else if(tag[0]==7) {
					bis.read(u2);
				}else if(tag[0]==8) {
					bis.read(u2);
				}else if(tag[0]==9) {
					bis.read(u4);
				}else if(tag[0]==10) {
					bis.read(u4);
				}else if(tag[0]==11) {
					bis.read(u4);
				}else if(tag[0]==12) {
					bis.read(u4);
				}else if(tag[0]==15) {
					bis.read(u3);
				}else if(tag[0]==16) {
					bis.read(u2);
				}else if(tag[0]==18) {
					bis.read(u4);
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
	
	/*public static void main(String[] args) throws Exception {
		File f = new File("D:\\work\\work_new\\remote-debug-agent\\build\\classes\\java\\main\\com\\github\\rdagent\\TraceRecorder.class");
		readClassData(f);
	}
	
	private static boolean readClassData(File f) throws IOException {
		BufferedInputStream bis = null;
		byte[] u2 = new byte[2], u3 = new byte[3], u4 = new byte[4], u8 = new byte[8];
		try {
			bis = new BufferedInputStream(new FileInputStream(f), 1024);
			//magic
			bis.read(u4);
			//minor_version major_version
			bis.read(u4);
			//cp length
			bis.read(u2);
			int cpLength = byteArray2Int(u2);
			
			byte[] tag = new byte[1];
			for(int i=0;i<cpLength;i++) {
				bis.read(tag);
				//System.out.println("tag="+tag[0]);
				if(tag[0]==1) {
					bis.read(u2);
					int u8l = byteArray2Int(u2);
					//System.out.println("u8l="+u8l);
					byte[] strByte = new byte[u8l];
					bis.read(strByte);
					String str = new String(strByte, "utf-8");
					//System.out.println("str="+str);
					if("Lcom/github/rdagent/annontation/BothUsing;".equals(str)){
						return true;
					}
				}else if(tag[0]==3) {
					bis.read(u4);
				}else if(tag[0]==4) {
					bis.read(u4);
				}else if(tag[0]==5) {
					bis.read(u8);
				}else if(tag[0]==6) {
					bis.read(u8);
				}else if(tag[0]==7) {
					bis.read(u2);
				}else if(tag[0]==8) {
					bis.read(u2);
				}else if(tag[0]==9) {
					bis.read(u4);
				}else if(tag[0]==10) {
					bis.read(u4);
				}else if(tag[0]==11) {
					bis.read(u4);
				}else if(tag[0]==12) {
					bis.read(u4);
				}else if(tag[0]==15) {
					bis.read(u3);
				}else if(tag[0]==16) {
					bis.read(u2);
				}else if(tag[0]==18) {
					bis.read(u4);
				}
			}
			//System.out.println(cpLength);
			
		}catch(IOException e) {
			throw e;
		}finally {
			if(bis!=null) {
				bis.close();
			}
		}
		return false;
	}*/
	
	private int byteArray2Int(byte[] byteArray) {
		int r = 0;
		for(int i=byteArray.length;i>0;i--) {
			//System.out.println(byteArray[i-1]);
			r += (byteArray[i-1]&0xff)*Math.pow(256, byteArray.length-i);
		}
		return r;
	}

}
