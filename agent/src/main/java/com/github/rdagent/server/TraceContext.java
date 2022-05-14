package com.github.rdagent.server;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;

import com.github.rdagent.common.Util;
import com.github.rdagent.transformer.intercepter.IPmap;

public class TraceContext extends AbstractHandler{
	
	@Override
	public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
			throws IOException, ServletException {
		response.setContentType("text/html;charset=utf-8");
		response.setStatus(HttpServletResponse.SC_OK);
		baseRequest.setHandled(true);
		String allowMethod = "all allowed reqests are:\n"+
				"/trace/start : start tracing methods invoked by yourself\n"+
				"/trace/stop : stop tracing\n"+
				"/trace/list : get your tracing result. parameters/default value:[timecost/false, coverage/false]\n"+
				"/trace/clean : clean the method list\n";
		response.getWriter().println(allowMethod);
	}
	
	public static HandlerCollection getTraceHandlers() {
		HandlerCollection collection = new HandlerCollection();
		TraceContext context = new TraceContext();
		
		ContextHandler startContext = new ContextHandler();
		startContext.setContextPath("/trace/start");
		startContext.setHandler(context.new Start());
		collection.addHandler(startContext);
		
		ContextHandler stopContext = new ContextHandler();
		stopContext.setContextPath("/trace/stop");
		stopContext.setHandler(context.new Stop());
		collection.addHandler(stopContext);
		
		ContextHandler listContext = new ContextHandler();
		listContext.setContextPath("/trace/list");
		listContext.setHandler(context.new List());
		collection.addHandler(listContext);
		
		ContextHandler cleanContext = new ContextHandler();
		cleanContext.setContextPath("/trace/clean");
		cleanContext.setHandler(context.new Clean());
		collection.addHandler(cleanContext);
		
		ContextHandler defaultContext = new ContextHandler();
		defaultContext.setContextPath("/trace");
		defaultContext.setHandler(context);
		collection.addHandler(defaultContext);
		
		return collection;
	}
	
	private class Start extends AbstractHandler{
		@Override
		public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
				throws IOException, ServletException {
			response.setContentType("text/html;charset=utf-8");
			String ip = ServerUtil.getIpAddr(request);
			//System.out.println("trace start: "+ip);
			IPmap.startTracing(ip);
			response.setStatus(HttpServletResponse.SC_OK);
			baseRequest.setHandled(true);
			response.getWriter().println("tracing started");
		}
	}
	
	private class Stop extends AbstractHandler{
		@Override
		public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
				throws IOException, ServletException {
			response.setContentType("text/html;charset=utf-8");
			String ip = ServerUtil.getIpAddr(request);
			IPmap.stopTracing(ip);
			response.setStatus(HttpServletResponse.SC_OK);
			baseRequest.setHandled(true);
			response.getWriter().println("tracing stoped");
		}
	}
	
	private class List extends AbstractHandler{
		@Override
		public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
				throws IOException, ServletException {
			response.setContentType("text/html;charset=utf-8");
			String ip = ServerUtil.getIpAddr(request);
			System.out.println("trace list: "+ip);
			String timecost = ServerUtil.getQueryParam(request, "timecost");
			String coverage = ServerUtil.getQueryParam(request, "coverage");
			response.setStatus(HttpServletResponse.SC_OK);
			baseRequest.setHandled(true);
			response.getWriter().println(Util.getJsonTrace(ip, "true".equals(timecost), "true".equals(coverage)));
		}
	}
	
	private class Clean extends AbstractHandler{
		@Override
		public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
				throws IOException, ServletException {
			response.setContentType("text/html;charset=utf-8");
			String ip = ServerUtil.getIpAddr(request);
			IPmap.cleanTracing(ip);
			response.setStatus(HttpServletResponse.SC_OK);
			baseRequest.setHandled(true);
			response.getWriter().println("clean done");
		}
	}
}
