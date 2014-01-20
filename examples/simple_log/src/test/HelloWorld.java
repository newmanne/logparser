package test;
import java.util.ArrayList;
import java.util.List;

public class HelloWorld {
	
	public static void main(String[] args) {
		String x = "ehll";
		String y = "chili";
		if (x.startsWith(y)) {
			x += "goodbye";
		}
		info("Hello, " + x);
		info("Person" + "Place" + "Thing");
	}
	
	public static void func(String s) {
		info("pizza" + s);
	}
	
	public static void info(String s) {
		
	}
}

