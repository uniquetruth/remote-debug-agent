package com.github.rdagent.transformer.intercepter;

import java.lang.reflect.Method;

import com.github.rdagent.Constants;

public class DubboClientIntercepter {

    public static void bindIP(Object request) {
        System.out.println("luanfei +++ debug request is: " + request.getClass());
        try {
            ClassLoader threadCl = Thread.currentThread().getContextClassLoader();
            Class<?> rpcInvocationClass = null;
            try {
                // dubbo 3.X
                rpcInvocationClass = Class.forName("org.apache.dubbo.rpc.RpcInvocation", true, threadCl);
            } catch (ClassNotFoundException e) {
                // dubbo 2.X
                rpcInvocationClass = Class.forName("com.alibaba.dubbo.rpc.RpcInvocation", true, threadCl);
            }
            Method method = rpcInvocationClass.getMethod("setAttachment", String.class, String.class);
            method.invoke(request, Constants.customIpHeader, "abc");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
