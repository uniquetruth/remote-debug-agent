package com.github.rdagent.transformer.handler;

import java.util.ArrayList;
import java.util.List;

public class Struts2Handler extends DefaultServletAdatper{
	
	//struts2 deals HttpServletRequest in filter
	private String struts2 = "org/apache/struts2/dispatcher/Dispatcher";
	
	public List<String> injectClassNameList(){
		List<String> nameList = new ArrayList<String>();
		nameList.add(struts2);
		return nameList;
	}
	
	protected String getMethodName() {
		return "serviceAction";
	}
	
	public int getPriority() {
		return 11;
	}

}
