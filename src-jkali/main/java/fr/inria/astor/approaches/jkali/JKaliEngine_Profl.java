package fr.inria.astor.approaches.jkali;

import com.martiansoftware.jsap.JSAPException;
import fr.inria.astor.core.entities.ProgramVariant;
import fr.inria.astor.core.entities.validation.VariantValidationResult;
import fr.inria.astor.core.manipulation.MutationSupporter;
import fr.inria.astor.core.setup.ProjectRepairFacade;
import fr.inria.astor.core.stats.PatchHunkStats;
import fr.inria.astor.core.stats.PatchStat;
import fr.inria.astor.core.validation.results.TestCasesProgramValidationResult;
import fr.inria.astor.core.validation.results.TestResult;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import utdallas.edu.profl.replicate.patchcategory.DefaultPatchCategories;
import utdallas.edu.profl.replicate.util.MethodLineCoverageInterface;
import utdallas.edu.profl.replicate.util.ProflResultRanking;
import utdallas.edu.profl.replicate.util.TestLineCoverageInterface;
import utdallas.edu.profl.replicate.util.XiaMethodLineCoverage;
import utdallas.edu.profl.replicate.util.XiaTestLineCoverage;

/**
 *
 * @author Matias Martinez
 *
 */
public class JKaliEngine_Profl extends JKaliEngine {

    MethodLineCoverageInterface profl_method = null;
    TestLineCoverageInterface profl_test = null;
    ProflResultRanking profl = null;

    public JKaliEngine_Profl(MutationSupporter mutatorExecutor, ProjectRepairFacade projFacade) throws JSAPException, Exception {
        super(mutatorExecutor, projFacade);

        System.out.println("Creating Profl-based jKali");

        profl_method = new XiaMethodLineCoverage(projFacade.getProperties().getProflMethodFilepath());
        profl_test = new XiaTestLineCoverage(projFacade.getProperties().getProflTestCoverageFilepath());
        profl = new ProflResultRanking(profl_method, profl_test, projFacade.getProperties().getProflFailingTestFilepath());

        profl.outputSbflSus();
    }

    @Override
    public VariantValidationResult validateInstance(ProgramVariant variant) {
        VariantValidationResult validationResult = super.validateInstance(variant);

        if (validationResult instanceof TestCasesProgramValidationResult) {
            TestCasesProgramValidationResult tcpvr = (TestCasesProgramValidationResult) validationResult;
            TestResult results = tcpvr.getTestResult();

            List<PatchHunkStats> hunkList = (List<PatchHunkStats>) variant.getPatchInfo().getStats().get(PatchStat.PatchStatEnum.HUNKS);
            Map m = new TreeMap();
            int hunkIndex = 0;

            for (PatchHunkStats hunk : hunkList) {
                String buggyFile = (String) hunk.getStats().get(PatchStat.HunkStatEnum.LOCATION);
                int buggyLocation = (int) hunk.getStats().get(PatchStat.HunkStatEnum.LINE);

                System.out.println(String.format("[%n/%n] Buggy code located in %s at %n", hunkIndex++, hunkList.size(), buggyFile, buggyLocation));

                String buggyMethod = profl.getMethodCoverage().lookup(buggyFile, buggyLocation);
                Double susValue = profl.getGeneralMethodSusValues().get(buggyMethod);

                m.put(buggyMethod, susValue);
            }

            if (results.individualTestsProcessed == true) {
                if (results.failFail == 0 && results.passFail == 0) {
                    System.out.println("CleanFix detected");
                    profl.addCategoryEntry(DefaultPatchCategories.CLEAN_FIX, m);
                } else if (results.failPass > 0) {
                    System.out.println("NoisyFix detected");
                    profl.addCategoryEntry(DefaultPatchCategories.NOISY_FIX, m);
                } else if (results.failPass == 0 && results.passFail == 0) {
                    System.out.println("NoneFix detected");
                    profl.addCategoryEntry(DefaultPatchCategories.NONE_FIX, m);
                } else {
                    System.out.println("NegFix detected");
                    profl.addCategoryEntry(DefaultPatchCategories.NEG_FIX, m);
                }
            } else {
                String message = "Mandatory profl-based individual processing failed!";
                System.out.println(message);

                try {
                    throw new Exception(message);
                } catch (Exception ex) {
                    System.err.println(ex);
                }
            }

        }

        return validationResult;
    }

    @Override
    public void atEnd(){
        super.atEnd();
        profl.outputSbflSus();
    }
}
