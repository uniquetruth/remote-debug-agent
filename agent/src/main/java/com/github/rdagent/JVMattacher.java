package com.github.rdagent;

import java.util.List;

import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;

/**
 * This class is designed for hot-plugging agent
 * first parameter is progress id or progress name(depending on os)
 * second parameter is agent's parameter
 * @author uniqueT
 */
public class JVMattacher {

	public static void main(String[] args) throws Exception {
		if(args.length != 2) {
			System.out.println("2 parameters are needed!");
			System.exit(1);
		}
		int pid = 0;
		//if first paramter is a number, according to pid to attach
		try {
			pid = Integer.parseInt(args[0]);
		}catch(NumberFormatException e) {
			pid = 0;
		}
		
		System.out.println("JVM attach thread start!");
		ClassLoader cl = JVMattacher.class.getClassLoader();
		if(cl ==null) {
			cl = ClassLoader.getSystemClassLoader();
		}
		String path = cl.getResource("com/github/rdagent/JVMattacher.class").getPath();
		String jarFile = path.substring(5, path.indexOf(".jar!")+4);
		
		List<VirtualMachineDescriptor> list = VirtualMachine.list();
	    for (VirtualMachineDescriptor vmd : list) {
	    	//System.out.println("vmd.displayName = "+vmd.displayName());
	    	//System.out.println("vmd.id = "+vmd.id());
	    	if ((pid>0 && args[0].equals(vmd.id())) || vmd.displayName().contains(args[0])) {
	    		VirtualMachine virtualMachine = VirtualMachine.attach(vmd.id());
	    		virtualMachine.loadAgent(jarFile, args[1]);
	    		//System.out.println("ok");
	    		virtualMachine.detach();
	    	}
	    }
		System.out.println("attach done whith args ["+args[0] + "] [" + args[1] + "]");
	}

}
