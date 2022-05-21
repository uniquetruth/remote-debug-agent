package com.github.rdagent.transformer.handler;

import java.util.ArrayList;
import java.util.List;

/**
 * inject HttpServlet.service(HttpServletRequest, HttpServletResponse)
 * 
 * @author uniqueT
 *
 */
public class ServletHandler extends DefaultServletAdatper{
	
	//most frameworks and middlewares use standard java servlet
	private String javaServlet = "javax.servlet.http.HttpServlet";
	//weblogic may use this
	private String wlServlet = "weblogic.servlet.ServletServlet";
	
	public List<String> injectClassNameList(){
		List<String> nameList = new ArrayList<String>();
		nameList.add(javaServlet);
		nameList.add(wlServlet);
		return nameList;
	}
	
	public int getPriority() {
		return 11;
	}

}
