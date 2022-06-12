package com.github.rdagent;

import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class RemoteTest {
	
	private static OkHttpClient client;
	
	@BeforeClass
	public static void prepareClient() throws InterruptedException{
		client = new OkHttpClient();
		//wait for server's port is ready
		Request request = new Request.Builder()
			      .url("http://127.0.0.1:8080/")
			      .build();
		Response response = null;
		do {
			try {
				response = client.newCall(request).execute();
			}catch(IOException e) {
				Thread.sleep(1000);
			}
		}while(response == null);
	}
	
	@Test
	public void run() throws IOException {
		//start trace
		Request request = new Request.Builder()
			      .url("http://127.0.0.1:8098/trace/start")
			      .build();
		client.newCall(request).execute();
		//invoke app interface
		request = new Request.Builder()
			      .url("http://127.0.0.1:8080/random")
			      .build();
		Response response = client.newCall(request).execute();
		System.out.println("response from app server: "+response.body().string());
		//trace list
		request = new Request.Builder()
			      .url("http://127.0.0.1:8098/trace/list")
			      .build();
		response = client.newCall(request).execute();
		String responseBody = response.body().string();
		System.out.println(responseBody);
		assertTrue("trace list failed",
				responseBody.contains("com.github.rdagent.app.WebappHandler"));
	}
	
	@AfterClass
	public static void closeServer() throws IOException {
		Request request = new Request.Builder()
			      .url("http://127.0.0.1:8080/stopserver")
			      .build();
		client.newCall(request).execute();
	}

}
