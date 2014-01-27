package chord.analyses.inst;

import java.util.List;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import joeq.Compiler.Quad.Operator.Invoke;
import joeq.Compiler.Quad.Quad;
import chord.program.visitors.IInvokeInstVisitor;
import chord.project.Chord;
import chord.project.analyses.ProgramRel;

import com.google.common.collect.Lists;

// TODO: could probably make this a domain since can get everythign we need form the q
@Chord(name = "MLogInvkInst", sign = "M0,I0:M0xI0")
public class RelMLogInvkInst extends ProgramRel implements IInvokeInstVisitor {

	final List<String> logMethods = Lists.newArrayList("info", "debug", "warn", "error");
	
	public void visit(jq_Method m) {
	}

	@Override
	public void visit(jq_Class c) {
	}

	@Override
	public void visitInvokeInst(Quad q) {
		jq_Method invokedMethod = Invoke.getMethod(q).getMethod();
		jq_Method callerMethod = q.getMethod();
		if (logMethods.contains(invokedMethod.getName().toString()) && invokedMethod.getDeclaringClass().toString().contains("commons.logging")) {
//			System.out.println("Call site @" + q.getMethod() + " in file " + q.getMethod().getDeclaringClass().getSourceFileName() + " on line " + q.getLineNumber() + " " +  q);
			add(callerMethod, q);
		}
	}

}
