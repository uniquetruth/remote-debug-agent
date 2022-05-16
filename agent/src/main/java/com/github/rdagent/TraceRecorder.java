package com.github.rdagent;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.github.rdagent.common.CoverageSnapshot;
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
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS");
		String filename = sdf.format(new Date());
		if(AgentOptions.getProcTrace()) {
			dumpTrace(filename);
		}
		if(AgentOptions.getProcCoverage()) {
			dumpCoverage(filename);
		}
		AgentServer.stop();
	}
	
	
	/**
	 * dump trace file
	 */
	private void dumpTrace(String filename) {
		File f = new File(outputDir + File.separator + filename + ".log");
		FileWriter writer = null;
		try {
			String message = Util.getJsonTrace(Constants.virtualIp, AgentOptions.getProcTraceTime(), 
					AgentOptions.getProcTraceLines());
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
	
	private void dumpCoverage(String filename) {
		File f = new File(outputDir + File.separator + filename +".data");
		
		CoverageSnapshot ca = new CoverageSnapshot();
		ObjectOutputStream bos = null;
		try {
			bos = new ObjectOutputStream(new FileOutputStream(f));
			bos.writeObject(ca.getIpMethodMap());
			bos.writeObject(ca.getCoverageMap());
			bos.writeObject(ca.getRecordLineMap());
			
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
	}

}
