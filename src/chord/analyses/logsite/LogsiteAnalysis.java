package chord.analyses.logsite;

import java.util.ArrayList;
import java.util.List;

import net.sf.bddbddb.Domain;

import com.google.common.collect.Lists;
import com.sun.xml.internal.bind.unmarshaller.DOMScanner;

import chord.analyses.invk.DomI;
import chord.analyses.var.DomV;
import chord.program.Program;
import chord.project.analyses.JavaAnalysis;
import joeq.Class.jq_Method;
import joeq.Class.jq_Type;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory.Register;
import chord.project.Chord;
import chord.project.Project;
import chord.project.analyses.ProgramRel;
import chord.project.ClassicProject;
import chord.util.tuple.integer.IntTrio;

/**
 * This implements a simple analysis that prints the call-site -> call target
 * information. The name of the analysis is "logsite-java". TODO: add a filter
 * to keep only LOG.info calls.
 */
@Chord(name = "logsite-java", consumes = { "I", "V", "IinvkArg", "VT" } // This
// analysis
// uses
// a
// relation
// IM:
// which
// is a
// tuple
// in the format of (call-site quad, target method), that contains
// all the call-site <-> target information. This is computed in
// alias/cipa_0cfa.dlog. This means that the underlying system will
// run "cipa-0cfa-dlog" analysis first!
)
public class LogsiteAnalysis extends JavaAnalysis {

	final List<String> logMethods = Lists.newArrayList("info", "debug", "warn", "error");

	public void run() {
		// // TODO: limit to our file, not libraries
		// // TODO: values of things, especially the static part
		// ArrayList<Integer> valid = new ArrayList<Integer>();
		// ArrayList<Register> validRegs = new ArrayList<Register>();
		//
		// DomI domI = (DomI) ClassicProject.g().getTrgt("I");
		// DomV domV = (DomV) ClassicProject.g().getTrgt("V");
		//
		// int numI = domI.size();
		// for (int iIdx = 0; iIdx < numI; iIdx++) {
		// Quad q = (Quad) domI.get(iIdx);
		// jq_Method method = q.getMethod();
		// if (method.getName().toString().equals("println") &&
		// method.getDeclaringClass().getSourceFileName().startsWith("java")) {
		// valid.add(iIdx);
		// }
		// }
		//
		// ProgramRel relIinvkArg = (ProgramRel)
		// ClassicProject.g().getTrgt("IinvkArg");
		// relIinvkArg.load(); // Load the IM relation.
		// for (IntTrio tuple : relIinvkArg.getAry3IntTuples()) {
		// if (valid.contains(tuple.idx0)) {
		// Register register = domV.get(tuple.idx2);
		// validRegs.add(register);
		// }
		// }
		//
		// ProgramRel relVT = (ProgramRel) ClassicProject.g().getTrgt("VT");
		// relVT.load(); // Load the IM relation.
		// for (Object[] tuple : relVT.getAryNValTuples()) {
		// Register r = (Register) tuple[0];
		// if (validRegs.contains(r)) {
		// jq_Type type = (jq_Type) tuple[1];
		// }
		// }

		ProgramRel relIM = (ProgramRel) ClassicProject.g().getTrgt("IM");
		relIM.load(); // Load the IM relation.
		for (Object[] tuple : relIM.getAryNValTuples()) {
			// iterate through every tuple in IM
			Quad q = (Quad) tuple[0]; // the call-site, in quad format
			jq_Method m_caller = q.getMethod(); // method enclosing the call
												// site
			String file = m_caller.getDeclaringClass().getSourceFileName(); // file
			jq_Method m_callee = (jq_Method) tuple[1]; // the callee method
			if (file.startsWith("java")) {
//			if (file.startsWith("java") || !logMethods.contains(m_callee.getName().toString())) {
				continue;
			}
			
			List<Integer> regNums = Lists.newArrayList();
			joeq.Util.Templates.List.RegisterOperand usedRegisters = q.getUsedRegisters();
			for (int i = 0; i < usedRegisters.size(); i++) {
				RegisterOperand reg = usedRegisters.getRegisterOperand(i);
				regNums.add(reg.getRegister().getNumber());
			}
			
			ControlFlowGraph cfg = m_caller.getCFG();
			joeq.Util.Templates.ListIterator.BasicBlock reversePostOrderIterator = cfg.reversePostOrderIterator();
			while (reversePostOrderIterator.hasNext()) {
				BasicBlock bb = reversePostOrderIterator.nextBasicBlock();
				for (int i = 0; i < bb.size(); i++) {
					Quad quad = bb.getQuad(i);
					joeq.Util.Templates.List.RegisterOperand regs = q.getUsedRegisters();
					for (int j = 0; j < regs.size(); j++) {
						RegisterOperand reg = regs.getRegisterOperand(j);
						if (regNums.contains(reg.getRegister().getNumber())) { // TODO: also T vs R - then need to do flow analysis 
							System.out.println(quad);
						}
					}
				}
			}

			int line = q.getLineNumber(); // line number
			System.out.println("LogSiteAnalysis: call instruction at line: " + line + "@" + file + " is to target: " + m_callee);
		}
	}
}