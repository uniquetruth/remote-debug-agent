package com.github.rdagent.server;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import com.github.rdagent.AgentOptions;
import com.github.rdagent.common.CoverageSnapshot;
import com.github.rdagent.transformer.AsmTransformer;
import com.github.rdagent.transformer.intercepter.IPmap;

public class ManageContext extends HttpServlet{
	
	private static final long serialVersionUID = -7774566608957738333L;

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws IOException, ServletException {
		response.setContentType("text/html;charset=utf-8");
		response.setStatus(HttpServletResponse.SC_OK);
		String allowMethod = "all allowed reqests are:\n"+
				"/manage/dump : dump all coverage data into tracelog file\n"+
				"/manage/clean : clean?ip=ip1&ip=ip2 : remove coverage data invoked by specific IP. (BE CAREFUL, if no ip parameter, all records will be removed)\n"+
				"/manage/detach : detach the agent and shutdown api interface";
		response.getWriter().println(allowMethod);
	}
	
	public static ContextHandler getManageHandlers() {
		ServletContextHandler context = new ServletContextHandler();
		context.setContextPath("/manage");
		ManageContext root = new ManageContext();
		context.addServlet(new ServletHolder(root), "/");
		
		context.addServlet(new ServletHolder(root.new Dump()),"/dump");
		context.addServlet(new ServletHolder(root.new Clean()),"/clean");
		context.addServlet(new ServletHolder(root.new Detach()),"/detach");
		
		return context;
	}
	
	private class Dump extends HttpServlet{
		
		private static final long serialVersionUID = -6797416643857568314L;

		@Override
		protected void doGet(HttpServletRequest request, HttpServletResponse response)
				throws IOException, ServletException {
			System.out.println("manage handler log : "+request.getRemoteAddr()+" begin to dump coverage file");
			response.setContentType("text/html;charset=utf-8");
			
			String logfile = ServerUtil.getQueryParam(request, "logfile");
			StringBuilder filename = new StringBuilder().append(AgentOptions.getOutputDir())
					.append(File.separator);
			if(logfile!=null && !"".equals(logfile)) {
				filename.append(filename);
			}else {
				SimpleDateFormat sdf = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS");
				filename.append(sdf.format(new Date()));
			}
			filename.append(".cover");
			File f = new File(filename.toString());
			
			CoverageSnapshot cs = new CoverageSnapshot();
			ObjectOutputStream bos = null;
			try {
				bos = new ObjectOutputStream(new FileOutputStream(f));
				bos.writeObject(cs.getIpMethodMap());
				bos.writeObject(cs.getCoverageMap());
				bos.writeObject(cs.getRecordLineMap());
				
			}catch(IOException e) {
				e.printStackTrace();
			}finally {
				if(bos!=null) {
					try {
						bos.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
			response.getWriter().println("coverage data dump success in:"+f.getAbsolutePath());
			System.out.println("manage handler log : dump success.");
		}
	}
	
	private class Clean extends HttpServlet{
		
		private static final long serialVersionUID = -743409024040693996L;

		@Override
		protected void doGet(HttpServletRequest request, HttpServletResponse response)
				throws IOException, ServletException {
			response.setContentType("text/html;charset=utf-8");
			List<String> ipList = ServerUtil.getQueryParamList(request, "ip");
			if(ipList.size()==0) {
				IPmap.clean();
				response.getWriter().println("all coverage datas are removed");
				System.out.println("manage handler log : "+request.getRemoteAddr()+" cleaned all coverage datas");
				return;
			}
			
			StringBuilder sb = new StringBuilder("[");
			for(String ip : ipList) {
				sb.append(ip).append(",");
				IPmap.clean(ip);
			}
			sb.setLength(sb.length()-1);
			sb.append("]");
			
			response.getWriter().println(sb.toString() + "'s coverage datas are removed");
			System.out.println("manage handler log : "+request.getRemoteAddr()+" cleaned some datas");
		}
	}
	
	private class Detach extends HttpServlet{
		
		private static final long serialVersionUID = -1769900441937239415L;

		@Override
		protected void doGet(HttpServletRequest request, HttpServletResponse response)
				throws IOException, ServletException {
			System.out.println("begin to detach needle");
			AsmTransformer atf = (AsmTransformer)AgentOptions.getTransformer();
			Instrumentation inst = AgentOptions.getInstrumentation();
			inst.removeTransformer(atf);
			try {
				inst.retransformClasses(atf.retransFilter(inst.getAllLoadedClasses()));
			} catch (UnmodifiableClassException e) {
				System.err.println("detach agent error: "+e.getMessage());
			}
			Thread shutdownThread = new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						Thread.sleep(3000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					System.out.println("real agent detached");
					AgentServer.stop();
				}
			});
			shutdownThread.start();
			Runtime.getRuntime().removeShutdownHook(AgentOptions.getSDHook());
			
			response.getWriter().println("agent detached");
			System.out.println("agent detached");
		}
	}
}
