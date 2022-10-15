package com.github.rdagent.app.dubbo;

import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DubboProviderConfig {

    @Bean
    public ApplicationConfig myApplicationConfig(){
        ApplicationConfig ac = new ApplicationConfig();
        ac.setName("DubboTestProvider");
        return ac;
    }

    @Bean
    public ProtocolConfig myProtocolConfig(){
        ProtocolConfig pc = new ProtocolConfig();
        pc.setName("dubbo");
        pc.setPort(20888);
        return pc;
    }
    
}
