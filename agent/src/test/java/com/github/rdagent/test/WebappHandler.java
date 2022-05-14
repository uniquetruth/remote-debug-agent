package com.github.rdagent.test;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WebappHandler {
	
	@RequestMapping("/random")
	public String handle() {
		double r = Math.random();
		System.out.println(r);
		if(largeThanHalf(r)) {
			return "random number( "+r+" ) is large than half";
		}else {
			return "random number( "+r+" ) is little than half";
		}
	}
	
	private boolean largeThanHalf(double d) {
		if(d>0.5) {
			return true;
		}else {
			return false;
		}
	}
	
	@RequestMapping("/stopserver")
	public String stopServer() {
		Thread t = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					Thread.sleep(3000);
				} catch (InterruptedException e) {}
				System.exit(0);
			}
		});
		t.start();
		return "server closed";
	}

}
