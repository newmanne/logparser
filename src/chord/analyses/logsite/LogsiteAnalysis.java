package chord.analyses.logsite;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.Operand;
import joeq.Compiler.Quad.Operand.AConstOperand;
import joeq.Compiler.Quad.Operand.MethodOperand;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.RegisterFactory.Register;
import joeq.Compiler.Quad.Quad;
import lombok.Data;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.analyses.JavaAnalysis;
import chord.project.analyses.ProgramRel;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;

/**
 * This implements a simple analysis that prints the call-site -> call target
 * information. The name of the analysis is "logsite-java". TODO: add a filter
 * to keep only LOG.info calls.
 */
@Chord(name = "logsite-java", consumes = { "I", "V", "IinvkArg", "VT", "IM" } // This
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

	final List<String> logMethods = Lists.newArrayList("info", "debug", "warn", "error", "println");

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
			if (file.startsWith("java") || !logMethods.contains(m_callee.getName().toString())) {
				continue;
			}

			List<RegId> regIds = Lists.newArrayList();
			joeq.Util.Templates.List.RegisterOperand usedRegisters = q.getUsedRegisters();
			for (int i = 0; i < usedRegisters.size(); i++) {
				RegisterOperand reg = usedRegisters.getRegisterOperand(i);
				regIds.add(new RegId(reg.getRegister()));
			}
			RegId root = regIds.get(0);
			// System.out.println("Starting search from the following regs" +
			// regIds);

			ControlFlowGraph cfg = m_caller.getCFG();
			joeq.Util.Templates.ListIterator.BasicBlock reversePostOrderIterator = cfg.reversePostOrderIterator();
			List<BasicBlock> reverse = Lists.reverse(Lists.newArrayList(reversePostOrderIterator));
			ListMultimap<RegId, RegId> multimap = ArrayListMultimap.create();
			for (BasicBlock bb : reverse) {
				for (int i = bb.size() - 1; i >= 0; i--) {
					Quad quad = bb.getQuad(i);
					joeq.Util.Templates.List.RegisterOperand regs = quad.getDefinedRegisters();
					for (int j = 0; j < regs.size(); j++) {
						RegisterOperand reg = regs.getRegisterOperand(j);
						if (regIds.contains(new RegId(reg.getRegister()))) {
							final RegId regId = regIds.get(regIds.indexOf(new RegId(reg.getRegister())));
							// System.out.println(quad);
							// System.out.println("found the definition of " +
							// regId);
							if (quad.getOp2() instanceof AConstOperand) {
								// System.out.println(((AConstOperand)
								// quad.getOp2()).getValue());
							}
							regId.setDefinitionQuad(quad);
							regIds.remove(regId);
							joeq.Util.Templates.List.RegisterOperand usedRegs = quad.getUsedRegisters();
							for (int k = 0; k < usedRegs.size(); k++) {
								RegId newRegId = new RegId(usedRegs.getRegisterOperand(k).getRegister());
								multimap.put(regId, newRegId);
								// System.out.println("now looking for definition of "
								// + newRegId);
								regIds.add(newRegId);
							}
						}
					}
				}
			}
			String regex = makeRegex(new StringBuilder(), root, multimap);
			System.out.println(regex);
			// int line = q.getLineNumber(); // line number
			// System.out.println("LogSiteAnalysis: call instruction at line: "
			// + line + "@" + file + " is to target: " + m_callee);
		}
	}

	private String makeRegex(StringBuilder sb, RegId curNode, ListMultimap<RegId, RegId> multimap) {
		Quad definitionQuad = curNode.getDefinitionQuad();
		if (definitionQuad.getOp2() instanceof AConstOperand) {
			final String value = (String) ((AConstOperand) definitionQuad.getOp2()).getValue();
			sb.append(value);
		}
		// MethodOperand methodOperand = (MethodOperand)
		// definitionQuad.getOp2();
		// if
		// (methodOperand.toString().equals("append:(Ljava/lang/String;)Ljava/lang/StringBuilder;@java.lang.StringBuilder"))
		// {
		// for (RegId reg : multimap.get(curNode)) {
		// System.out.println(curNode.getDefinitionQuad());
		// }
		// } else {
		for (RegId reg : multimap.get(curNode)) {
			sb.append(makeRegex(new StringBuilder(), reg, multimap));
		}
		return sb.toString();
	}

	@Data
	public static class RegId {
		final int number;
		final boolean isTemp;
		Quad definitionQuad;

		public RegId(Register r) {
			this.number = r.getNumber();
			this.isTemp = r.isTemp();
		}

		// @Override
		// public String toString() {
		// return (isTemp ? "T" : "R") + number;
		// }
	}

}