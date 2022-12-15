package com.github.rdagent;

import java.io.IOException;

import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.utils.SimpleReferenceCache;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.github.rdagent.app.dubbo.IHelloService;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class DubboTest {

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
    public void consumerTest(){
        ReferenceConfig<IHelloService> reference = new ReferenceConfig<IHelloService>();
        reference.setApplication(new ApplicationConfig("letmetest"));
        reference.setUrl("dubbo://127.0.0.1:20888");
        reference.setInterface(IHelloService.class);
        
        SimpleReferenceCache cache;
        cache = SimpleReferenceCache.getCache();
        IHelloService helloService = cache.get(reference);
        System.out.println(helloService.sayHello("rdagent"));
    }
    
    @AfterClass
	public static void closeServer() throws IOException {
		Request request = new Request.Builder()
			      .url("http://127.0.0.1:8080/stopserver")
			      .build();
		client.newCall(request).execute();
		System.out.println("server closed");
	}
}
