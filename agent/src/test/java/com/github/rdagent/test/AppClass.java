package com.github.rdagent.test;

public class AppClass {

	public static void main(String[] args) {
		AppClass ac = new AppClass();
		double r = Math.random();
		System.out.println(ac.calc(5, r));
	}

	private double calc(int _a, double r) {
		int a = _a;
		double b;
		
		if(r>0.5) {
			b = a+r;
		}else {
			b = a*r;
		}
		return b;
	}

}
