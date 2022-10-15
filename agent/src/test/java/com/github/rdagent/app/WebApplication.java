package com.github.rdagent.app;

import java.io.PrintStream;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.ComponentScan;

@ComponentScan(basePackages = { "com.github.rdagent.app.**" })
@SpringBootApplication
@EnableDubbo(scanBasePackages = {"com.github.rdagent.app.dubbo.**"})
public class WebApplication implements WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> {

	public static void main(String[] args) throws Exception {
		PrintStream ps = new PrintStream(args[0]);
		System.setOut(ps);
		System.setErr(ps);
		SpringApplication application = new SpringApplication(WebApplication.class);
		application.setBannerMode(Banner.Mode.OFF);
		application.run(args);
		System.out.println("web app started");
	}

	@Override
	public void customize(ConfigurableServletWebServerFactory factory) {
		factory.setPort(8080);
	}

}
