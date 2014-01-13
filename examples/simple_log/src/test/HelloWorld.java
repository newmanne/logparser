package test;
import java.util.ArrayList;
import java.util.List;

public class HelloWorld {
	public static void main(String[] args) {
		List<Hurps> x = new ArrayList<Hurps>();
		x.add(new Hurps());
		System.out.println("Hello, " + args[0] + "!" + x);
	}
	
	public static class Hurps {
		@Override
		public String toString() {
			return "I'm a hurp!";
		}
	}
}
