package test;
import java.util.ArrayList;
import java.util.List;

public class HelloWorld {
	public static void main(String[] args) {
		info("Hello, " + args[0] + "!");
	}
	
	public static class Hurps {
		@Override
		public String toString() {
			return "I'm a hurp!";
		}
	}
	
	public static void info(String s) {
		
	}
}
