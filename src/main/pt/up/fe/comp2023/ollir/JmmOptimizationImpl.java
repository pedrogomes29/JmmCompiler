package pt.up.fe.comp2023.ollir;

import org.specs.comp.ollir.ClassUnit;
import org.specs.comp.ollir.Method;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.ollir.JmmOptimization;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2023.constFolding.JmmVisitorForConstFolding;
import pt.up.fe.comp2023.constPropagation.JmmVisitorForConstPropagation;
import pt.up.fe.comp2023.registerAllocation.MethodVisitor;
import pt.up.fe.comp2023.symbolTable.JmmSymbolTable;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;


public class JmmOptimizationImpl implements JmmOptimization {

    private String fieldsToOllir(List<Symbol> fields){
        StringBuilder fieldsCodeBuilder = new StringBuilder();
        for(Symbol field:fields){
            String accessModifierAndFieldName = String.format("\t.field %s %s","private",field.getName());
            String fieldType = String.format(".%s;\n",OllirUtils.typeToOllir(field.getType()));
            fieldsCodeBuilder.append(accessModifierAndFieldName).append(fieldType);
        }
        return fieldsCodeBuilder.toString();
    }

    private String methodsToOllir(JmmSymbolTable symbolTable){
        StringBuilder methodsCodeBuilder = new StringBuilder();
        for(String method:symbolTable.getMethods()){
            methodsCodeBuilder.append(".method public ");
            if(symbolTable.methodIsStatic(method))
                methodsCodeBuilder.append("static ");

            methodsCodeBuilder.append(method).append("(");

            List<Symbol> params = symbolTable.getParameters(method);
            String paramsString = (String)params.stream().map((param) -> {
                return param.getName()+"."+OllirUtils.typeToOllir(param.getType());
            }).collect(Collectors.joining(", "));
            methodsCodeBuilder.append(paramsString);

            methodsCodeBuilder.append(").").append(OllirUtils.typeToOllir(symbolTable.getReturnType(method))).append(" {\n").
                    append(symbolTable.getMethodOllirCode(method)).append("\t}\n\n\t");
        }
        return methodsCodeBuilder.toString();
    }

    private String importsToOllir(JmmSymbolTable symbolTable){
        StringBuilder importsCodeBuilder = new StringBuilder("");
        for(String an_import:symbolTable.getImports()){
            importsCodeBuilder.append("import ");
            List<String> package_ = symbolTable.getImportPackage(an_import);
            if(package_.size()>0){
                String package_path = (String)package_.stream().map((dir) -> {
                    return dir;
                }).collect(Collectors.joining("."));
                importsCodeBuilder.append(package_path).append(".");
            }
            importsCodeBuilder.append(an_import).append(";\n");
        }
        return importsCodeBuilder.toString();
    }

    private String superToOllir(String superName){
        if(superName==null)
            return "";
        else
            return " extends " + superName;
    }


    @Override
    public OllirResult toOllir(JmmSemanticsResult jmmSemanticsResult) {
        if(jmmSemanticsResult.getReports().size()>0) {
            for (Report report:jmmSemanticsResult.getReports())
                System.out.println(report.toString());
            return new OllirResult(jmmSemanticsResult, "", jmmSemanticsResult.getReports());

        }
        JmmSymbolTable symbolTable = (JmmSymbolTable) jmmSemanticsResult.getSymbolTable();
        JmmVisitorForOllir gen = new JmmVisitorForOllir(symbolTable);
        gen.visit(jmmSemanticsResult.getRootNode());
        symbolTable = gen.getSymbolTable();
        String imports = importsToOllir(symbolTable);
        String fields = fieldsToOllir(symbolTable.getFields());
        String methods = methodsToOllir(symbolTable);
        String superName = superToOllir(symbolTable.getSuper());
        String ollirCode = String.format("""
                %s
                %s%s{
                
                %s
                    .construct %s().V {
                        invokespecial(this, "<init>").V;
                    }
                    
                    %s
                }
                """,imports,symbolTable.getClassName(),superName, fields,symbolTable.getClassName(),methods);

        System.out.println(ollirCode);
        return new OllirResult(ollirCode,jmmSemanticsResult.getConfig());
    }

    @Override
    public JmmSemanticsResult optimize(JmmSemanticsResult semanticsResult){
        boolean continueOptimizing = semanticsResult.getConfig().containsKey("optimize") && Objects.equals(semanticsResult.getConfig().get("optimize"), "true");
        while(continueOptimizing) {
            JmmVisitorForConstFolding jmmVisitorForConstFolding = new JmmVisitorForConstFolding();
            jmmVisitorForConstFolding.visit(semanticsResult.getRootNode());
            continueOptimizing = jmmVisitorForConstFolding.hasOptimized();
            JmmVisitorForConstPropagation jmmVisitorForConstPropagation = new JmmVisitorForConstPropagation(semanticsResult.getSymbolTable());
            jmmVisitorForConstPropagation.visit(semanticsResult.getRootNode());
            continueOptimizing = continueOptimizing || jmmVisitorForConstPropagation.hasOptimized();
        }
        return semanticsResult;
    }
    @Override
    public OllirResult optimize(OllirResult ollirResult){
        if(ollirResult.getConfig().containsKey("registerAllocation")){
            int nrRegisters = Integer.parseInt(ollirResult.getConfig().get("registerAllocation"));
            if(nrRegisters>=0) {
                ClassUnit ollirClass = ollirResult.getOllirClass();
                ollirClass.buildCFGs();

                for (Method method : ollirClass.getMethods()) {
                    MethodVisitor visitor = new MethodVisitor(method,nrRegisters);
                    visitor.visit();
                    if (visitor.insufficientRegisters()) {
                        ollirResult.getReports().add(new Report(ReportType.ERROR, Stage.OPTIMIZATION, -1, -1, "Not enough registers"));
                        break;
                    }
                }
            }
        }

       return ollirResult;
    }
}
