package fr.inria.main;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Execution modes included in Astor framework.
 * @author Matias Martinez matias.martinez@inria.fr
 *
 */
public enum ExecutionMode {
	DeepRepair(Collections.singletonList("deeprepair")),
	CARDUMEN(Collections.singletonList("cardumen")),
        CARDUMEN_Profl(Collections.singletonList("cardumen_profl")),
	jGenProg(Collections.singletonList("jgenprog")),
        jGenProg_Profl(Collections.singletonList("jgenprog_profl")),
	jKali(Collections.singletonList("jkali")),
        jKali_Profl(Collections.singletonList("jkali_profl")),
	MutRepair(Arrays.asList("mutation","jmutrepair", "mutrepair")),
        MutRepair_Profl(Arrays.asList("mutation_profl","jmutrepair_profl", "mutrepair_profl")),
	EXASTOR(Arrays.asList("exhaustive", "exastor")),
	SCAFFOLD(Collections.singletonList("scaffold")),
	custom(Collections.singletonList("custom"));

	private List<String> acceptedNames;

	ExecutionMode(List<String> acceptedNames) {
		this.acceptedNames = acceptedNames;
	}

	public List<String> getAcceptedNames() {
		return acceptedNames;
	}
}
