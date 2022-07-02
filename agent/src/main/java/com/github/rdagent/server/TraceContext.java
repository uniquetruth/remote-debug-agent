package com.github.rdagent.server;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import com.github.rdagent.common.Util;
import com.github.rdagent.transformer.intercepter.IPmap;

public class TraceContext extends HttpServlet{
	
	private static final long serialVersionUID = 6886649308109133528L;

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws IOException, ServletException {
		response.setContentType("text/html;charset=utf-8");
		response.setStatus(HttpServletResponse.SC_OK);
		String allowMethod = "all allowed reqests are:\n"+
				"/trace/start : start tracing methods invoked by yourself\n"+
				"/trace/stop : stop tracing\n"+
				"/trace/list : get your tracing result. parameters/default value:[timecost/false, coverage/false]\n"+
				"/trace/clean : clean the method list\n";
		response.getWriter().println(allowMethod);
	}
	
	public static ContextHandler getTraceHandlers() {
		ServletContextHandler context = new ServletContextHandler();
		context.setContextPath("/trace");
		TraceContext root = new TraceContext();
		context.addServlet(new ServletHolder(root), "/");
		
		context.addServlet(new ServletHolder(root.new Start()), "/start");
		context.addServlet(new ServletHolder(root.new Stop()), "/stop");
		context.addServlet(new ServletHolder(root.new List()), "/list");
		context.addServlet(new ServletHolder(root.new Clean()), "/clean");
		
		return context;
	}
	
	private class Start extends HttpServlet {
		
		private static final long serialVersionUID = 5041423964138330918L;

		@Override
		protected void doGet(HttpServletRequest request, HttpServletResponse response)
				throws IOException, ServletException {
			response.setContentType("text/html;charset=utf-8");
			String ip = ServerUtil.getIpAddr(request);
			//System.out.println("trace start: "+ip);
			IPmap.startTracing(ip);
			response.setStatus(HttpServletResponse.SC_OK);
			//baseRequest.setHandled(true);
			response.getWriter().println("tracing started");
		}
	}
	
	private class Stop extends HttpServlet{
		
		private static final long serialVersionUID = 7396468202564792422L;

		@Override
		protected void doGet(HttpServletRequest request, HttpServletResponse response)
				throws IOException, ServletException {
			response.setContentType("text/html;charset=utf-8");
			String ip = ServerUtil.getIpAddr(request);
			IPmap.stopTracing(ip);
			response.setStatus(HttpServletResponse.SC_OK);
			response.getWriter().println("tracing stoped");
		}
	}
	
	private class List extends HttpServlet{
		
		private static final long serialVersionUID = 590326902299602880L;

		@Override
		protected void doGet(HttpServletRequest request, HttpServletResponse response)
				throws IOException, ServletException {
			response.setContentType("text/html;charset=utf-8");
			String ip = ServerUtil.getIpAddr(request);
			System.out.println("trace list: "+ip);
			String timecost = ServerUtil.getQueryParam(request, "timecost");
			String coverage = ServerUtil.getQueryParam(request, "coverage");
			String sql = ServerUtil.getQueryParam(request, "sql");
			response.setStatus(HttpServletResponse.SC_OK);
			response.getWriter().println(Util.getJsonTrace(ip, "true".equals(timecost), "true".equals(coverage), "true".equals(sql)));
		}
	}
	
	private class Clean extends HttpServlet{
		
		private static final long serialVersionUID = -7556777885231059208L;

		@Override
		protected void doGet(HttpServletRequest request, HttpServletResponse response)
				throws IOException, ServletException {
			response.setContentType("text/html;charset=utf-8");
			String ip = ServerUtil.getIpAddr(request);
			IPmap.cleanTracing(ip);
			response.setStatus(HttpServletResponse.SC_OK);
			response.getWriter().println("clean done");
		}
	}
}
