package com.github.rdagent.app;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DatabaseBuziHandler {
	
	@RequestMapping("/dbquery")
	public String handle() {
		Connection con = getConnection();
		PreparedStatement pst = null;
		ResultSet rs = null;
		String result = "init";
		try {
			String sql = "select rand()+?, ? from dual";
			pst = con.prepareStatement(sql);
			pst.setLong(1, 2);
			pst.setString(2, "test");
			
			rs = pst.executeQuery();
			if(rs.next()) {
				result = rs.getString(1) + ", " + rs.getString(2);
			}
		}catch(SQLException e) {
			e.printStackTrace();
		}finally {
			clean(rs, pst, con);
		}
		
		return "jdbc: "+result;
		
	}
	
	private Connection getConnection() {
		Connection con = null;
		try {
			Class.forName("com.mysql.jdbc.Driver");
			con = DriverManager.getConnection("jdbc:mysql://127.0.0.1:3306/mydatabase?useSSL=false",
					"username", "password");
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("get database connection error");
			System.exit(1);
		}
		return con;
	}
	
	private void clean(ResultSet rs, Statement st, Connection con) {
		try {
			if(rs!=null) {
				rs.close();
			}
			if(st!=null) {
				st.close();
			}
			if(con!=null) {
				con.close();
			}
		}catch(SQLException e) {
			e.printStackTrace();
		}
	}

}
