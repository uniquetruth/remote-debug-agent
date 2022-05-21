# remote-debug-agent
 
A java agent which lets you debug your web application remotely. It's very useful in integration tests.  
## How to use:  
Add this agent to your application's JAVA_OPTS, do testing actions(click a button or invoke an interface) normally, send a request to the custom port, and get debug information of the program that just ran. Here are the details:  

**From source code**: cd ${project dir}, type command `gradle agentTest`, you will find all binary files in ${project dir}/agent/build/libs.  
**From binary files**: put all jar files to any directory you want, add `-javaagent:${directory above}/remote-debug-agent.jar=includes=com.foo.bar,apiport=8098` to your web application's JAVA_OPTS. (e.g. if you use tomcat as middleware, you can add this option in catalina.sh) Then start your web application, the agent will provide service in port 8098. This is your debugging interface.  

Now you can debug your program remotely. For example, a button in GUI sends a request and invokes a method in com.foo.bar.SomeClass. Before clicking the button, send another request with url `http://ip:8098/trace/start`. Then click the button. Last, send a `http://ip:8098/trace/list` request, and the agent will return you debug information that contains what methods you invoked just now, what are the parameters and return value, cost time, even covered code lines.  
A specific example likes: 
```json
[{
	"coverage": "[11,13][16,16]",
	"cost time": 0,
	"method": "java.lang.String com.github.rdagent.test.WebappHandler.handle()",
	"calls": [{
		"coverage": "[21,21][24,24]",
		"cost time": 0,
		"method": "boolean com.github.rdagent.test.WebappHandler.largeThanHalf(double)",
		"parameters": ["0.24444334899195885"],
		"return value": "0"
	}],
	"return value": "random number( 0.24444334899195885 ) is little than half"
}]
```
The example is in the test directory of this project.  

For now, this agent supports some popular frameworks:  
+ Frameworks and middlewares that use standard java servlet, like Spring, Tomcat and so on.
+ Struts2
+ Dubbo
+ Rabbitmq  

Of course, you can use this agent in your local java program. It collects debug information and dumps them into a file after the main thread stoped.  

## Advance Usage
**How to make this agent be compatible with more framworks**  
I also have provided an easy way to support more frameworks(based on HttpServlet). For example, you have a web application that uses com.foo.bar.MyServlet as core frame. To use this agent in your application, just write a class extends com.github.rdagent.transformer.handler.DefaultServletAdatper, override injectClassNameList() method, make it return a List contains String value "com.foo.bar.MyServlet". Pack this class into a jar file, put it beside remote-debug-agent.jar, and done. You have already been able to debug your application remotely.  
  
**How to use it in the internet environment**  
By default, this agent uses the client's IP address as threads' identification to distinguish different invokers, so that many testers can debug remotely in the integration environment at the same time. But if you work in the internet environment or can't fix your IP address for any reason, this default way may not be enough.
Like **How to make this agent be compatible with more framworks** chapter, you can easily solve this problem by extending DefaultServletAdatper too. Override extractIdentity() method and use getHttpXXX(e.g. getHttpHeader()) method to get other information in the request, you can use any String as the thread's identification.  
