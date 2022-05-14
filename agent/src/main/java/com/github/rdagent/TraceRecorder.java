package com.github.rdagent;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import com.github.rdagent.common.Util;
import com.github.rdagent.server.AgentServer;

/**
 * JVM shutdown thread
 * @author uniqueT
 *
 */
public class TraceRecorder extends Thread {
	
	private String outputDir;
	
	/**
	 * specify an output directory
	 * @param _outputDir
	 */
	public TraceRecorder(String _outputDir) {
		outputDir = _outputDir;
		File f = new File(outputDir);
		if(!f.exists()) {
			f.mkdirs();
		}
	}

	/**
	 * For java local program, if -Drdagent.trace=true, dump the trace info into disk.
	 */
	@Override
	public void run() {
		if(AgentOptions.getProcTrace()) {
			dumpLog();
		}
		AgentServer.stop();
	}
	
	
	/**
	 * dump log file
	 */
	private void dumpLog() {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS");
		File f = new File(outputDir + File.separator + sdf.format(new Date()) + ".log");
		FileWriter writer = null;
		try {
			String message = Util.getJsonTrace(Constants.virtualIp, AgentOptions.getProcTraceTime(), 
					AgentOptions.getProcTraceCover());
			writer = new FileWriter(f, false);
			if (message != null) {
				writer.write(message);
				writer.flush();
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (writer != null) {
				try {
					writer.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

}
