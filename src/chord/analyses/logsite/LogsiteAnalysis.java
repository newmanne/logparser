package chord.analyses.logsite;

import java.util.List;

import joeq.Class.jq_Method;
import joeq.Compiler.Quad.BasicBlock;
import joeq.Compiler.Quad.ControlFlowGraph;
import joeq.Compiler.Quad.Operand.AConstOperand;
import joeq.Compiler.Quad.Operand.RegisterOperand;
import joeq.Compiler.Quad.Quad;
import joeq.Compiler.Quad.RegisterFactory.Register;
import lombok.Data;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.analyses.JavaAnalysis;
import chord.project.analyses.ProgramRel;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;

/**
 * This implements a simple analysis that prints the call-site -> call target
 * information. The name of the analysis is "logsite-java".
 */
@Chord(name = "logsite-java", consumes = { "MLogInvkInst" })
public class LogsiteAnalysis extends JavaAnalysis {

	public void run() {
		
		ProgramRel relMLogInvkInst = (ProgramRel) ClassicProject.g().getTrgt("MLogInvkInst");
		relMLogInvkInst.load(); 

		for (Object[] tuple : relMLogInvkInst.getAryNValTuples()) {
			
			Quad q = (Quad) tuple[1]; // the call-site, in quad format
			jq_Method m_caller = q.getMethod(); // the callee method
			
			String file = m_caller.getDeclaringClass().getSourceFileName(); // file
//			System.out.println("Call site @" + q.getMethod() + " in file " + q.getMethod().getDeclaringClass().getSourceFileName() + " on line " + q.getLineNumber() + " " +  q);

			// TODO: better to filter with chord.ext.scope.exclude
			if (!file.startsWith("org/apache/hadoop")) {
				continue;
			}

			List<RegId> regIds = Lists.newArrayList();
			joeq.Util.Templates.List.RegisterOperand usedRegisters = q.getUsedRegisters();
			for (int i = 0; i < usedRegisters.size(); i++) {
				RegisterOperand reg = usedRegisters.getRegisterOperand(i);
				regIds.add(new RegId(reg.getRegister()));
			}
			final List<RegId> roots = Lists.newArrayList(regIds);

			ControlFlowGraph cfg = m_caller.getCFG();
			joeq.Util.Templates.ListIterator.BasicBlock reversePostOrderIterator = cfg.reversePostOrderIterator();
			List<BasicBlock> reverse = Lists.reverse(Lists.newArrayList(reversePostOrderIterator));
			ListMultimap<RegId, RegId> multimap = ArrayListMultimap.create();
			for (BasicBlock bb : reverse) {
				for (int i = bb.size() - 1; i >= 0; i--) {
					Quad quad = bb.getQuad(i);
					if (quad.toString().contains("PHI")) { // if its a phi function, just skip
						continue;
					}
					joeq.Util.Templates.List.RegisterOperand regs = quad.getDefinedRegisters();
					for (int j = 0; j < regs.size(); j++) {
						RegisterOperand reg = regs.getRegisterOperand(j);
						if (regIds.contains(new RegId(reg.getRegister()))) {
							final RegId regId = regIds.get(regIds.indexOf(new RegId(reg.getRegister())));

							regId.setDefinitionQuad(quad);
							regIds.remove(regId);
							joeq.Util.Templates.List.RegisterOperand usedRegs = quad.getUsedRegisters();
							for (int k = 0; k < usedRegs.size(); k++) {
								RegId newRegId = new RegId(usedRegs.getRegisterOperand(k).getRegister());
								multimap.put(regId, newRegId);
								regIds.add(newRegId);
							}
						}
					}
				}
			}
			
			final int line = q.getLineNumber(); // line number
			System.out.println("LogSiteAnalysis: call instruction at line: " + line + "@" + file + " is from: " + m_caller);

			for (RegId root : roots) {
				final String regex = makeRegex(new StringBuilder(), root, multimap);
				if (!regex.isEmpty()) {
					System.out.println(regex);
				}
			}
		}

	}

	private String makeRegex(StringBuilder sb, RegId curNode, ListMultimap<RegId, RegId> multimap) {
		final Quad definitionQuad = curNode.getDefinitionQuad();
		if (definitionQuad == null) {
			sb.append(".*");
		} else if (definitionQuad.getOp2() instanceof AConstOperand) {
			// TODO: fix for NPE on Server.java (ipc) line 979 - 0 arg functions 
			final String value = (((AConstOperand) definitionQuad.getOp2()).getValue()).toString();
			sb.append(value);
		}
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