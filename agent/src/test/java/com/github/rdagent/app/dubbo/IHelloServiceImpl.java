package com.github.rdagent.app.dubbo;

import org.apache.dubbo.config.annotation.DubboService;

@DubboService(register=false, interfaceClass=IHelloService.class)
public class IHelloServiceImpl implements IHelloService {

    @Override
    public String sayHello(String name) {
        return "Hello, " + name + " (from Dubbo with Spring Boot)";
    }
    
}
