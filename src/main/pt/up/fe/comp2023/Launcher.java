package pt.up.fe.comp2023;

import java.io.File;
import java.sql.SQLOutput;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.specs.comp.ollir.OllirErrorException;
import pt.up.fe.comp.TestUtils;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.jasmin.JasminResult;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.parser.JmmParserResult;
import pt.up.fe.comp2023.analysis.JmmAnalysisImpl;
import pt.up.fe.comp2023.jasmin.JasminBackend;
import pt.up.fe.comp2023.ollir.JmmOptimizationImpl;
import pt.up.fe.specs.util.SpecsIo;
import pt.up.fe.specs.util.SpecsLogs;
import pt.up.fe.specs.util.SpecsSystem;

public class Launcher {

    public static void main(String[] args) throws OllirErrorException {
        // Setups console logging and other things
        SpecsSystem.programStandardInit();

        // Parse arguments as a map with predefined options
        var config = parseArgs(args);

        // Get input file
        File inputFile = new File(config.get("inputFile"));

        // Check if file exists
        if (!inputFile.isFile()) {
            throw new RuntimeException("Expected a path to an existing input file, got '" + inputFile + "'.");
        }

        // Read contents of input file
        String code = SpecsIo.read(inputFile);

        // Instantiate JmmParser
        SimpleParser parser = new SimpleParser();

        // Parse stage
        JmmParserResult parserResult = parser.parse(code, config);

        if(parserResult.getRootNode()!=null)
            System.out.println(parserResult.getRootNode().toTree());

        // Check if there are parsing errors
        TestUtils.noErrors(parserResult.getReports());


        JmmAnalysisImpl jmmAnalysisImpl = new JmmAnalysisImpl();
        JmmSemanticsResult jmmSemanticsResult = jmmAnalysisImpl.semanticAnalysis(parserResult);
        System.out.println(jmmSemanticsResult.getRootNode().toTree());
        JmmOptimizationImpl jmmOptimizationImpl = new JmmOptimizationImpl();
        OllirResult ollirResult = jmmOptimizationImpl.toOllir(jmmSemanticsResult);
        ollirResult = jmmOptimizationImpl.optimize(ollirResult);
        JasminBackend backend = new JasminBackend();
        JasminResult result = backend.toJasmin(ollirResult);
        System.out.println(result.getJasminCode());
        /*
        JasminBackend jasminBackend = new JasminBackend();
        JasminResult jasminResult = jasminBackend.toJasmin(ollirResult);

        System.out.println(jasminResult.getJasminCode());
        */
    }


    private static Map<String, String> parseArgs(String[] args) {
        SpecsLogs.info("Executing with args: " + Arrays.toString(args));
        Map<String, String> config = new HashMap<>();
        switch (args.length) {
            case 1 -> {
                config.put("inputFile", args[0]);
                config.put("optimize", "false");
                config.put("registerAllocation", "-1");
                config.put("debug", "false");
                return config;
            }
            case 2 -> {
                if (args[1].equals("-o")) {
                    config.put("inputFile", args[0]);
                    config.put("optimize", "true");
                    config.put("registerAllocation", "-1");
                    config.put("debug", "false");

                    return config;
                } else if (args[1].startsWith("-r")) {
                    config.put("inputFile", args[0]);
                    config.put("optimize", "false");
                    config.put("registerAllocation", args[1].substring(3));
                    config.put("debug", "false");

                    return config;
                }
            }
            case 3 -> {
                if (args[1].equals("-o") && args[2].startsWith("-r")) {
                    config.put("inputFile", args[0]);
                    config.put("optimize", "true");
                    config.put("registerAllocation", args[2].substring(3));
                    config.put("debug", "false");

                    return config;
                }
                else if (args[2].equals("-o") && args[1].startsWith("-r")) {
                    config.put("inputFile", args[0]);
                    config.put("optimize", "true");
                    config.put("registerAllocation", args[1].substring(3));
                    config.put("debug", "false");

                    return config;
                }
            }
        }
        throw new RuntimeException("Provided arguments are not valid.");
    }
}
