package pt.up.fe.comp2023.ollir;

import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ollir.JmmOptimization;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp2023.symbolTable.JmmSymbolTable;

import java.util.List;
import java.util.stream.Collectors;


public class JmmOptimizationImpl implements JmmOptimization {

    public static String typeToOllir(Type type){
        String typeName = type.getName();
        switch(typeName){
            case "int":
                typeName="i32";
                break;
            case "boolean":
                typeName="bool";
                break;
            case "void":
                typeName="V";
                break;
            default:
                break; //already the right name (classname)
        }
        if(type.isArray()){
            return "array." + typeName;
        }
        else
            return typeName;
    }

    private String fieldsToOllir(List<Symbol> fields){
        StringBuilder fieldsCodeBuilder = new StringBuilder();
        for(Symbol field:fields){
            String accessModifierAndFieldName = String.format(".field %s %s","private",field.getName());
            String fieldType = String.format(".%s;\n",typeToOllir(field.getType()));
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
                return param.getName()+"."+typeToOllir(param.getType());
            }).collect(Collectors.joining(", "));
            methodsCodeBuilder.append(paramsString);

            methodsCodeBuilder.append(").").append(typeToOllir(symbolTable.getReturnType(method))).append(" {\n").
                    append(symbolTable.getMethodOllirCode(method)).append("\t}\n\n\t");
        }
        return methodsCodeBuilder.toString();
    }

    @Override
    public OllirResult toOllir(JmmSemanticsResult jmmSemanticsResult) {
        SymbolTable symbolTable = jmmSemanticsResult.getSymbolTable();
        String fields = fieldsToOllir(symbolTable.getFields());
        String methods = methodsToOllir((JmmSymbolTable) symbolTable);
        String ollirCode = String.format("""
                %s{
                    %s
                    .construct %s().V {
                        invokespecial(this, "<init>").V;
                    }
                    
                    %s
                }
                """,symbolTable.getClassName(), fields,symbolTable.getClassName(),methods);

        System.out.println(ollirCode);
        return new OllirResult(ollirCode,jmmSemanticsResult.getConfig());
    }
}
