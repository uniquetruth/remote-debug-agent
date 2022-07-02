package com.github.rdagent.transformer.handler;

import java.util.ArrayList;
import java.util.List;

public class InternalJdbcHandler extends DefaultJdbcAdapter {
	
	protected List<String> injectPackageNameList(){
		List<String> nameList = new ArrayList<String>();
		nameList.add("oracle.jdbc");
		nameList.add("com.mysql");
		return nameList;
	}
	
	public int getPriority() {
		return 11;
	}

}
