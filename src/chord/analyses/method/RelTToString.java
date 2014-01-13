package chord.analyses.method;

import joeq.Class.jq_Class;
import joeq.Class.jq_Method;
import chord.program.visitors.IMethodVisitor;
import chord.project.Chord;
import chord.project.analyses.ProgramRel;

@Chord(
		name = "TToString",
		sign = "T0,M0:M0_T0"
	)
public class RelTToString extends ProgramRel implements IMethodVisitor {
	private jq_Class ctnrClass;
	public void visit(jq_Class c) {
		ctnrClass = c;
	}
	public void visit(jq_Method m) {
		if (m.getName().toString().equals("toString")) {
			add(ctnrClass, m);	
		}
	}
}

