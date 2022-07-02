package com.github.rdagent.transformer.intercepter;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.rdagent.AgentOptions;

public class SqlIntercepter {
	
	private static final String StringSlot = "__rdagent_stringslot"; 
	private static SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	
	private static ThreadLocal<String> rawSql = new ThreadLocal<String>();
	private static ThreadLocal<List<String>> tempStrings = new ThreadLocal<List<String>>() {
		protected List<String> initialValue(){
			return new ArrayList<String>();
		}
	};
	private static ThreadLocal<List<String>> params = new ThreadLocal<List<String>>() {
		protected List<String> initialValue(){
			return new ArrayList<String>();
		}
	};
	
	private static ThreadLocal<Integer> deep = new ThreadLocal<Integer>() {
		protected Integer initialValue() {
			return 0;
		}
	};
	
	public static void recordSql(String sql) {
		try {
			int d = deep.get();
			if (d == 0) {
				if (IPmap.hasAddress() || !AgentOptions.isDependIP()) {
					if (IPmap.canTrace()) {
						recordRawSql(sql);
						params.remove();
						IPmap.traceSql(sql);
					}
				}
			}
			deep.set(d + 1);
		} catch (Exception e) {
			System.err.println("SqlInterceptor Exception : " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	public static void endSql() {
		deep.set(deep.get()-1);
	}
	
	private static void recordRawSql(String sql) {
		//replace all sql strings, in case of there is any ? in string
		Pattern p = Pattern.compile("'.*?'");
		Matcher m = p.matcher(sql);
		while(m.find()) {
			tempStrings.get().add(m.group());
			sql = sql.replaceFirst("'.*?'", StringSlot);
		}
		rawSql.set(sql);
	}
	
	public static void recordParam(int index, int value) {
		try {
			if (IPmap.hasAddress() || !AgentOptions.isDependIP()) {
				if (IPmap.canTrace()) {
					setParam(index, String.valueOf(value));
				}
			}
		} catch (Exception e) {
			System.err.println("SqlInterceptor Exception : " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	public static void recordParam(int index, String value) {
		try {
			if (IPmap.hasAddress() || !AgentOptions.isDependIP()) {
				if (IPmap.canTrace()) {
					setParam(index, "'"+value+"'");
				}
			}
		} catch (Exception e) {
			System.err.println("SqlInterceptor Exception : " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	public static void recordParam(int index, long value) {
		try {
			if (IPmap.hasAddress() || !AgentOptions.isDependIP()) {
				if (IPmap.canTrace()) {
					setParam(index, String.valueOf(value));
				}
			}
		} catch (Exception e) {
			System.err.println("SqlInterceptor Exception : " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	public static void recordParam(int index, float value) {
		try {
			if (IPmap.hasAddress() || !AgentOptions.isDependIP()) {
				if (IPmap.canTrace()) {
					setParam(index, String.valueOf(value));
				}
			}
		} catch (Exception e) {
			System.err.println("SqlInterceptor Exception : " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	public static void recordParam(int index, double value) {
		try {
			if (IPmap.hasAddress() || !AgentOptions.isDependIP()) {
				if (IPmap.canTrace()) {
					setParam(index, String.valueOf(value));
				}
			}
		} catch (Exception e) {
			System.err.println("SqlInterceptor Exception : " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	public static void recordParam(int index, short value) {
		try {
			if (IPmap.hasAddress() || !AgentOptions.isDependIP()) {
				if (IPmap.canTrace()) {
					setParam(index, String.valueOf(value));
				}
			}
		} catch (Exception e) {
			System.err.println("SqlInterceptor Exception : " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	public static void recordParam(int index, BigDecimal value) {
		try {
			if (IPmap.hasAddress() || !AgentOptions.isDependIP()) {
				if (IPmap.canTrace()) {
					setParam(index, value.toString());
				}
			}
		} catch (Exception e) {
			System.err.println("SqlInterceptor Exception : " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	public static void recordParam(int index, Date value) {
		try {
			if (IPmap.hasAddress() || !AgentOptions.isDependIP()) {
				if (IPmap.canTrace()) {
					setParam(index, "date'"+dateFormat.format(value)+"'");
				}
			}
		} catch (Exception e) {
			System.err.println("SqlInterceptor Exception : " + e.getMessage());
			e.printStackTrace();
		}
	}
	public static void recordParam(int index, Time value) {
		try {
			if (IPmap.hasAddress() || !AgentOptions.isDependIP()) {
				if (IPmap.canTrace()) {
					setParam(index, "date'"+dateFormat.format(value)+"'");
				}
			}
		} catch (Exception e) {
			System.err.println("SqlInterceptor Exception : " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	public static void recordParam(int index, Timestamp value) {
		try {
			if (IPmap.hasAddress() || !AgentOptions.isDependIP()) {
				if (IPmap.canTrace()) {
					setParam(index, "date'"+dateFormat.format(value)+"'");
				}
			}
		} catch (Exception e) {
			System.err.println("SqlInterceptor Exception : " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	private static void setParam(int index, String value) {
		List<String> plist = params.get();
		if(plist.size() < index-1) {
			for(int i=plist.size()+1;i<index;i++) {
				plist.add(null);
			}
			plist.add(value);
		}else if(plist.size() == index-1) {
			plist.add(value);
		}else {
			plist.set(index-1, value);
		}
		IPmap.updateSql(getSql());
	}
	
	private static String getSql() {
		List<String> plist = params.get();
		String sql = rawSql.get();
		for(int i=0;i<plist.size();i++) {
			if(plist.get(i)==null) {
				sql = sql.replaceFirst("\\?", "__rdagent_##");
			}else {
				sql = sql.replaceFirst("\\?", plist.get(i));
			}
		}
		sql = sql.replace("__rdagent_##", "?");
		for(String realStr : tempStrings.get()) {
			sql = sql.replaceFirst(StringSlot, realStr);
		}
		return sql;
	}

}
