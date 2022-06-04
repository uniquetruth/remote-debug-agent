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
			//use daemon thread to run jetty server
			/*Thread asThread = new Thread(new Runnable() {
				@Override
				public void run() {
					server = new Server(port);
					//System.out.println("agent server start at port : "+AgentOptions.getApiPort());
					server.setHandler(createHandlers());
					server.setStopAtShutdown(true);
					//server.setAttribute("org.eclipse.jetty.util.log.class", "org.eclipse.jetty.util.log.StdErrLog");
					//server.setAttribute("org.eclipse.jetty.LEVEL", "INFO");
			        // start server
			        try {
						server.start();
						server.join();
					} catch (Exception e) {
						System.err.println("start agent server failed. "+e.getMessage());
					}
			        System.out.println("agent server start at port : "+AgentOptions.getApiPort());
				}
			});
			//asThread.setDaemon(true);
			asThread.start();*/
			server = new Server(port);
			server.setHandler(createHandlers());
			server.setStopAtShutdown(true);
			try {
				System.out.println("agent server start at port : "+AgentOptions.getApiPort());
				server.start();
				System.out.println("agent server before join ");
				//server.join();
				System.out.println("agent server after join ");
			} catch (Exception e) {
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
