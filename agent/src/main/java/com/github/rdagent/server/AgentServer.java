package com.github.rdagent.server;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerCollection;

import com.github.rdagent.AgentOptions;

public class AgentServer {
	
	private static Server server = null;
	
	public AgentServer() {
		
	}
	
	public static void start(){
		int port = AgentOptions.getApiPort();
		if(port > 0) {
			server = new Server(port);
			server.setHandler(createHandlers());
			server.setStopAtShutdown(true);
			try {
				server.start();
			} catch (Exception e) {
				System.err.println("start agent server failed. "+e.getMessage());
				e.printStackTrace();
			}
		}
	}
	
	private static Handler createHandlers() {
		HandlerCollection collection = new HandlerCollection();
		collection.addHandler(TraceContext.getTraceHandlers());
		collection.addHandler(ManageContext.getManageHandlers());
		return collection;
	}
	
	public static void stop() {
		if(server!=null) {
			try {
				server.stop();
			}catch(Exception e) {
				System.err.println("stop agent server failed. "+e.getMessage());
			}
		}
	}

}
