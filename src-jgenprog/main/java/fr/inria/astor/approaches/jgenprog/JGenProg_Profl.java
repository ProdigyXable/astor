package fr.inria.astor.approaches.jgenprog;

import com.martiansoftware.jsap.JSAPException;
import fr.inria.astor.core.entities.OperatorInstance;
import fr.inria.astor.core.entities.ProgramVariant;
import fr.inria.astor.core.entities.SuspiciousModificationPoint;
import fr.inria.astor.core.entities.validation.VariantValidationResult;
import fr.inria.astor.core.manipulation.MutationSupporter;
import fr.inria.astor.core.setup.ConfigurationProperties;
import fr.inria.astor.core.setup.ProjectRepairFacade;
import fr.inria.astor.core.validation.junit.JUnitProcessValidator;
import fr.inria.astor.core.validation.results.TestCasesProgramValidationResult;
import fr.inria.astor.core.validation.results.TestResult;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;
import utdallas.edu.profl.replicate.patchcategory.DefaultPatchCategories;
import utdallas.edu.profl.replicate.util.ProflResultRanking;
import utdallas.edu.profl.replicate.util.XiaMethodLineCoverage;
import utdallas.edu.profl.replicate.util.XiaTestLineCoverage;
import utdallas.edu.profl.replicate.util.interfaces.MethodLineCoverageInterface;
import utdallas.edu.profl.replicate.util.interfaces.TestLineCoverageInterface;

/**
 * Core repair approach based on reuse of ingredients.
 *
 * @author Matias Martinez, matias.martinez@inria.fr
 *
 */
public class JGenProg_Profl extends JGenProg {

    int patchNum = 0;
    MethodLineCoverageInterface profl_method = null;
    TestLineCoverageInterface profl_test = null;
    ProflResultRanking profl = null;

    public JGenProg_Profl(MutationSupporter mutatorExecutor, ProjectRepairFacade projFacade) throws JSAPException, Exception {
        super(mutatorExecutor, projFacade);

        ConfigurationProperties.setProperty("testbystep", String.valueOf(true));
        ConfigurationProperties.setProperty("forceExecuteRegression", String.valueOf(true));
        ConfigurationProperties.setProperty("stopFirst", String.valueOf(false));

        String proflMethod = projFacade.getProperties().getProflMethodFilepath();
        String proflTest = projFacade.getProperties().getProflTestCoverageFilepath();
        String proflFailing = projFacade.getProperties().getProflFailingTestFilepath();

        profl_method = new XiaMethodLineCoverage(proflMethod);
        profl_test = new XiaTestLineCoverage(proflTest);
        profl = new ProflResultRanking(profl_method, profl_test, proflFailing);

        log.info(String.format("Creating Profl-based jGEnProg %s, %s, %s", proflMethod, proflTest, proflFailing));
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

                if (buggyMethod != null && results.individualTestsProcessed == true) {
                    if (results.failPass > 0 && results.passFail == 0) {
                        if (results.failFail == 0) {
                            log.info("Full CleanFix detected");
                            profl.addCategoryEntry(DefaultPatchCategories.CLEAN_FIX_FULL, m);
                        } else {
                            log.info("Partial CleanFix detected");
                            profl.addCategoryEntry(DefaultPatchCategories.CLEAN_FIX_PARTIAL, m);
                        }

                    } else if (results.failPass > 0 && results.passFail > 0) {
                        if (results.failFail == 0) {
                            log.info("Full NoisyFix detected");
                            profl.addCategoryEntry(DefaultPatchCategories.NOISY_FIX_FULL, m);
                        } else {
                            log.info("Partial NoisyFix detected");
                            profl.addCategoryEntry(DefaultPatchCategories.NOISY_FIX_PARTIAL, m);
                        }
                    } else if (results.failPass == 0 && results.passFail == 0) {
                        log.info("NoneFix detected");
                        profl.addCategoryEntry(DefaultPatchCategories.NONE_FIX, m);
                    } else {
                        log.info("NegFix detected");
                        profl.addCategoryEntry(DefaultPatchCategories.NEG_FIX, m);
                    }

                    String dir = this.getProjectFacade().getProperties().getWorkingDirRoot();
                    this.saveProflGeneralSus(dir);
                    this.saveProflAggregatedSus(dir);
                    this.saveProflInfo(dir);
                    this.savePatch(dir, buggyFile, buggyLocation, buggyMethod, variant, ++patchNum);

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
    }

    public void saveProflGeneralSus(String dir) {
        File output = new File(dir + File.separator + "generalSusInfo.profl");
        output.getParentFile().mkdirs();

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(output))) {
            log.info("Saving sbfl information to " + output.getAbsolutePath());
            for (String s : this.profl.outputSbflSus()) {
                bw.write(s);
                bw.newLine();
            }

        } catch (Exception e) {
            log.info("Failed to save information to " + output.getAbsolutePath());
            log.info(e.getMessage());
        }
    }

    public void saveProflAggregatedSus(String dir) {
        File output = new File(dir + File.separator + "aggregatedSusInfo.profl");
        output.getParentFile().mkdirs();

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(output))) {
            log.info("Saving profl information to " + output.getAbsolutePath());
            for (String s : this.profl.outputProflResults()) {
                bw.write(s);
                bw.newLine();
            }

        } catch (Exception e) {
            log.info("Failed to save information to " + output.getAbsolutePath());
            log.info(e.getMessage());
        }
    }

    public void saveProflInfo(String dir) {
        File output = new File(dir + File.separator + "category_information.profl");
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(output))) {

            for (String s : this.profl.outputProflCatInfo()) {
                bw.write(s);
                bw.newLine();
            }

        } catch (Exception e) {

        }
    }

    private void savePatch(String dir, String buggyFile, int buggyLocation, String buggyMethod, ProgramVariant variant, int patchNum) {
        JUnitProcessValidator.patchNum = this.patchNum;
        JUnitProcessValidator.dir = dir;

        File output = new File(String.format("%s/patches/%d.patch", dir, patchNum));
        output.getParentFile().mkdirs();
        LinkedList<ProgramVariant> v = new LinkedList();
        v.add(variant);

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(output))) {
            bw.write(String.format("File: %s%n", buggyFile));
            bw.write(String.format("Method: %s%n", buggyMethod));
            bw.write(String.format("Line: %d%n", buggyLocation));
            bw.write(String.format("Current Time: %d%n", System.currentTimeMillis()));
            bw.write(String.format("-------------%n"));
            bw.write(String.format("Patch id = %s%n", variant.currentMutatorIdentifier()));

            log.info("Saving patch information to " + output.getAbsolutePath());

        } catch (Exception e) {
            log.info("Failed to save information to " + output.getAbsolutePath());
            log.info(e.getMessage());
        }
    }
}
