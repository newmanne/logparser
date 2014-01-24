package chord.analyses.logsite;

import java.io.File;
import java.io.IOException;
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
import joeq.Compiler.Quad.Operator.Phi;
import joeq.Compiler.Quad.RegisterFactory.Register;
import joeq.Compiler.Quad.Quad;
import lombok.Data;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.analyses.JavaAnalysis;
import chord.project.analyses.ProgramRel;
import chord.util.FileUtils;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.io.Files;

/**
 * This implements a simple analysis that prints the call-site -> call target
 * information. The name of the analysis is "logsite-java".
 */
@Chord(name = "logsite-java", consumes = { "I", "V", "IinvkArg", "VT", "IM" })
public class LogsiteAnalysis extends JavaAnalysis {

	final List<String> logMethods = Lists.newArrayList("info", "debug", "warn", "error");

	public void run() {

		ProgramRel relIM = (ProgramRel) ClassicProject.g().getTrgt("IM");
		relIM.load(); // Load the IM relation.

		for (Object[] tuple : relIM.getAryNValTuples()) {

			Quad q = (Quad) tuple[0]; // the call-site, in quad format
			System.out.println("Call site @" + q.getMethod() + " in file " + q.getMethod().getDeclaringClass().getSourceFileName() + " on line " + q.getLineNumber() + " " +  q);
			jq_Method m_caller = q.getMethod(); // method enclosing the call
												// site
			String file = m_caller.getDeclaringClass().getSourceFileName(); // file
			jq_Method m_callee = (jq_Method) tuple[1]; // the callee method

			if (!file.startsWith("org/apache/hadoop") || !isLogMethod(m_callee)) {
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
			for (RegId root : roots) {
				final String regex = makeRegex(new StringBuilder(), root, multimap);
				final int line = q.getLineNumber(); // line number
				System.out.println("LogSiteAnalysis: call instruction at line: " + line + "@" + file + " is from: " + m_caller);
				System.out.println(regex);
			}
		}

	}

	private boolean isLogMethod(jq_Method m_callee) {
		return m_callee.getName().toString().contains("info") || m_callee.getName().toString().contains("debug")
				|| m_callee.getName().toString().contains("warn") || m_callee.getName().toString().contains("error");
	}

	private String makeRegex(StringBuilder sb, RegId curNode, ListMultimap<RegId, RegId> multimap) {
		final Quad definitionQuad = curNode.getDefinitionQuad();
		if (definitionQuad == null) {
			sb.append(".*");
		} else if (definitionQuad.getOp2() instanceof AConstOperand) {
			final String value = (String) ((AConstOperand) definitionQuad.getOp2()).getValue();
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