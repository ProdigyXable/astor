package fr.inria.astor.approaches.jgenprog;

import com.martiansoftware.jsap.JSAPException;
import fr.inria.astor.core.entities.OperatorInstance;
import fr.inria.astor.core.entities.ProgramVariant;
import fr.inria.astor.core.entities.SuspiciousModificationPoint;
import fr.inria.astor.core.entities.validation.VariantValidationResult;
import fr.inria.astor.core.manipulation.MutationSupporter;
import fr.inria.astor.core.setup.ConfigurationProperties;
import fr.inria.astor.core.setup.ProjectRepairFacade;
import fr.inria.astor.core.validation.results.TestCasesProgramValidationResult;
import fr.inria.astor.core.validation.results.TestResult;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import utdallas.edu.profl.replicate.patchcategory.DefaultPatchCategories;
import utdallas.edu.profl.replicate.patchcategory.PatchCategory;
import utdallas.edu.profl.replicate.util.MethodLineCoverageInterface;
import utdallas.edu.profl.replicate.util.ProflResultRanking;
import utdallas.edu.profl.replicate.util.TestLineCoverageInterface;
import utdallas.edu.profl.replicate.util.XiaMethodLineCoverage;
import utdallas.edu.profl.replicate.util.XiaTestLineCoverage;

/**
 * Core repair approach based on reuse of ingredients.
 *
 * @author Matias Martinez, matias.martinez@inria.fr
 *
 */
public class JGenProg_Profl extends JGenProg {

    MethodLineCoverageInterface profl_method = null;
    TestLineCoverageInterface profl_test = null;
    ProflResultRanking profl = null;

    public JGenProg_Profl(MutationSupporter mutatorExecutor, ProjectRepairFacade projFacade) throws JSAPException, Exception {
        super(mutatorExecutor, projFacade);

        System.out.println("Creating Profl-based jGenProg");

        ConfigurationProperties.setProperty("testbystep", String.valueOf(true));
        ConfigurationProperties.setProperty("forceExecuteRegression", String.valueOf(true));
        ConfigurationProperties.setProperty("stopFirst", String.valueOf(false));

        profl_method = new XiaMethodLineCoverage(projFacade.getProperties().getProflMethodFilepath());
        profl_test = new XiaTestLineCoverage(projFacade.getProperties().getProflTestCoverageFilepath());
        profl = new ProflResultRanking(profl_method, profl_test, projFacade.getProperties().getProflFailingTestFilepath());

        this.saveProflGeneralSus();
    }

    @Override
    public VariantValidationResult validateInstance(ProgramVariant variant) {
        VariantValidationResult validationResult = super.validateInstance(variant);

        if (ConfigurationProperties.getPropertyBool("testbystep") && validationResult instanceof TestCasesProgramValidationResult) {
            TestCasesProgramValidationResult tcpvr = (TestCasesProgramValidationResult) validationResult;
            TestResult results = tcpvr.getTestResult();

            for (OperatorInstance lastOperator : variant.getAllOperations()) {
                Map m = new TreeMap();

                String buggyFile = lastOperator.getModificationPoint().getCtClass().getQualifiedName();
                int buggyLocation = ((SuspiciousModificationPoint) lastOperator.getModificationPoint()).getSuspicious().getLineNumber();

                String buggyMethod = profl.getMethodCoverage().lookup(buggyFile, buggyLocation);

                // Backup lookup strategy
                if (buggyMethod == null) {
                    try {
                        buggyMethod = profl.getMethodCoverage().getMethodFromPackageNumber(buggyFile, buggyLocation);
                    } catch (Exception e) {
                        System.out.println(e);
                    }
                }

                log.info(String.format("Buggy code located in %s at %d (%s)", buggyFile, buggyLocation, buggyMethod));
                Double susValue = profl.getGeneralMethodSusValues().get(buggyMethod);

                m.put(buggyMethod, susValue);

                if (results.individualTestsProcessed == true) {
                    if (results.failFail == 0 && results.passFail == 0) {
                        log.info("CleanFix detected");
                        profl.addCategoryEntry(DefaultPatchCategories.CLEAN_FIX, m);
                    } else if (results.failPass > 0) {
                        log.info("NoisyFix detected");
                        profl.addCategoryEntry(DefaultPatchCategories.NOISY_FIX, m);
                    } else if (results.failPass == 0 && results.passFail == 0) {
                        log.info("NoneFix detected");
                        profl.addCategoryEntry(DefaultPatchCategories.NONE_FIX, m);
                    } else {
                        log.info("NegFix detected");
                        profl.addCategoryEntry(DefaultPatchCategories.NEG_FIX, m);
                    }
                } else {
                    String message = "Mandatory profl-based individual processing failed!";
                    log.info(message);

                    try {
                        throw new Exception(message);
                    } catch (Exception ex) {
                        System.err.println(ex);
                    }
                }
            }
        }

        return validationResult;
    }

    @Override
    public void atEnd() {
        super.atEnd();

        System.out.println("Concluding repair atEnd() process");
        
        profl.outputProflResults();
        this.saveProflAggregatedSus();
    }

    public void saveProflGeneralSus() {
        FileWriter fw = null;
        try {
            String dir = this.getProjectFacade().getProperties().getWorkingDirRoot();
            File file = new File(dir + File.separator + "generalSusInfo.profl");
            fw = new FileWriter(file);

            int rank = 0;

            for (String methodSignature : profl.getGeneralMethodSusValues().keySet()) {
                if (profl.getGeneralMethodSusValues().get(methodSignature) > 0) {
                    fw.write(String.format("%03d|%.6f|%s%n", ++rank, profl.getGeneralMethodSusValues().get(methodSignature), methodSignature));
                }
            }

        } catch (IOException ex) {
            Logger.getLogger(JGenProg_Profl.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                fw.close();
            } catch (IOException ex) {
                Logger.getLogger(JGenProg_Profl.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public void saveProflAggregatedSus() {
        FileWriter fw = null;
        try {
            String dir = this.getProjectFacade().getProperties().getWorkingDirRoot();
            File file = new File(dir + File.separator + "aggregatedSusInfo.profl");
            fw = new FileWriter(file);

            int rank = 0;
            for (PatchCategory k : profl.getProflSusValues().keySet()) {
                if (!profl.getProflSusValues().get(k).isEmpty()) {
                    for (String s : profl.getProflSusValues().get(k).keySet()) {
                        fw.write(String.format("%03d|%.6f|%s|%s%n", ++rank, profl.getProflSusValues().get(k).get(s), k.getCategoryName(), s));
                    }
                }
            }

        } catch (IOException ex) {
            Logger.getLogger(JGenProg_Profl.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                fw.close();
            } catch (IOException ex) {
                Logger.getLogger(JGenProg_Profl.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

}
