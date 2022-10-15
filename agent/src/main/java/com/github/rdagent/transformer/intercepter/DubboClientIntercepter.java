package com.github.rdagent.transformer.intercepter;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;

import com.github.rdagent.AgentOptions;
import com.github.rdagent.Constants;

public class DubboClientIntercepter {

    private static String localIp = null;

    public static void bindIP(Object request, Object channel, int dubboVersion) {
        //System.out.println("uniqueT +++ debug request is: " + request.getClass());
        
        try {
            String customIP = getBindingIp(request, channel, dubboVersion);
            System.out.println("uniqueT +++ debug dubbo client ip is: " + customIP);
            if(customIP!=null && !"".equals(customIP)){
                ClassLoader threadCl = Thread.currentThread().getContextClassLoader();
                Class<?> rpcInvocationClass = null;
                if(dubboVersion==3){
                    rpcInvocationClass = Class.forName("org.apache.dubbo.rpc.RpcInvocation", true, threadCl);
                }else{
                    rpcInvocationClass = Class.forName("com.alibaba.dubbo.rpc.RpcInvocation", true, threadCl);
                }
                Method method = rpcInvocationClass.getMethod("setAttachment", String.class, String.class);
                method.invoke(request, Constants.customIpHeader, customIP);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String getBindingIp(Object request, Object channel, int dubboVersion){
        if(IPmap.hasAddress()) {
            return IPmap.getIpMap().get(Thread.currentThread());
        }else if(AgentOptions.isDubboLocalIp()){
            if(localIp == null){
                //try to get local ip just once
                try{
                    ClassLoader threadCl = Thread.currentThread().getContextClassLoader();
                    Class<?> endPointClass = null;
                    if(dubboVersion==3){
                        endPointClass = Class.forName("org.apache.dubbo.remoting.Endpoint", true, threadCl);
                    }else{
                        endPointClass = Class.forName("com.alibaba.dubbo.remoting.Endpoint", true, threadCl);
                    }
                    Method m = endPointClass.getMethod("getLocalAddress", new Class[0]);
                    InetSocketAddress inetAddr = (InetSocketAddress)m.invoke(channel, new Object[0]);
                    localIp = inetAddr.getAddress().getHostAddress();
                }catch(Exception e){
                    e.printStackTrace();
                    localIp = "";
                }
            }
            return localIp;
        }
        return null;
    }

}
