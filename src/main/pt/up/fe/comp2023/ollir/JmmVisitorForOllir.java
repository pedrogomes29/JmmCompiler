package pt.up.fe.comp2023.ollir;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2023.symbolTable.JmmSymbolTable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;


public class JmmVisitorForOllir extends AJmmVisitor< String , String > {

    private JmmSymbolTable symbolTable;

    public JmmVisitorForOllir(JmmSymbolTable symbolTable){
        this.symbolTable = symbolTable;
        buildVisitor();
    }

    public JmmSymbolTable getSymbolTable() {
        return symbolTable;
    }

    @Override
    protected void buildVisitor() {
        addVisit ("Program", this::dealWithProgram );
        addVisit("NormalMethod", this::dealWithNormalMethod);
        addVisit("StaticMethod", this::dealWithStaticMethod);
        addVisit("Assignment",this::dealWithAssignment);
        addVisit("Integer",this::dealWithInteger);
        addVisit("Identifier",this::dealWithIdentifier);
        addVisit("BinaryOp",this::dealWithBinaryOp);
        addVisit("Grouping",this::dealWithGrouping);
        addVisit("Constructor",this::dealWithConstructor);
        addVisit("MethodCall",this::dealWithMethodCall);
        addVisit("Negation",this::dealWithNegation);
        addVisit("Boolean",this::dealWithBoolean);
        addVisit("ExpressionStatement",this::dealWithExpressionStatement);
        addVisit("This",this::dealWithThis);
        setDefaultVisit(this::dealWithDefaultVisit);
    }


    private Boolean isLiteralOrFunctionVariable(JmmNode jmmNode){
        return Objects.equals(jmmNode.getKind(), "Integer") || Objects.equals(jmmNode.getKind(), "Boolean") || Objects.equals(jmmNode.getKind(), "This") || (Objects.equals(jmmNode.getKind(), "Identifier") && Objects.equals(jmmNode.get("field"), "false"));
    }
    private Boolean isConstructor(JmmNode jmmNode){
        return Objects.equals(jmmNode.getKind(), "Constructor");
    }

    private String dealWithProgram (JmmNode jmmNode , String s) {

        for ( JmmNode child : jmmNode.getChildren ()){
            visit(child,"");
        }
        return "";
    }
    private  String dealWithThis (JmmNode jmmNode, String s) {
        jmmNode.putObject("var","this");
        return "";
    }

    private String dealWithNormalMethod(JmmNode jmmNode , String s) {
        String functionName = jmmNode.get("functionName");

        int nrArguments = ((List<String>)jmmNode.getObject("arguments")).size();
        StringBuilder methodCode = new StringBuilder();

        Type ollirReturnType = symbolTable.getReturnType(functionName);
        String returnType = JmmOptimizationImpl.typeToOllir(ollirReturnType);
        for (int index = 1 + nrArguments+symbolTable.getLocalVariables(functionName).size();index<jmmNode.getNumChildren();index++){
            JmmNode child = jmmNode.getJmmChild(index);
            String childCode = visit(child,"");
            if(index==jmmNode.getNumChildren()-1) {
                if(isLiteralOrFunctionVariable(child)) {
                    methodCode.append("\t\tret.").append(returnType)
                            .append(" ").append(childCode).append(";\n");
                }
                else
                    methodCode.append(childCode).append("\t\t").append("ret").append(".").append(returnType).append(" ").
                            append(child.get("var")).append(";\n");
            }
            else
                methodCode.append(childCode);
        }
        symbolTable.setMethodOllirCode(functionName,methodCode.toString());
        return "";
    }
    private String dealWithStaticMethod(JmmNode jmmNode , String s) {
        String functionName = jmmNode.get("functionName");

        StringBuilder methodCode = new StringBuilder();
        List<JmmNode> children = jmmNode.getChildren();
        for (JmmNode child:children.subList(symbolTable.getLocalVariables(functionName).size(),children.size())){
            String childCode = visit(child,"");
            methodCode.append(childCode);
        }

        symbolTable.setMethodOllirCode(functionName,methodCode.toString());
        return "";
    }
    private String dealWithMethodCall(JmmNode jmmNode, String s){
        List<JmmNode> children = jmmNode.getChildren();
        String methodName = jmmNode.get("methodName");
        JmmNode objectWithMethod = children.get(0);
        StringBuilder methodCallCode = new StringBuilder();
        List<String> arguments = new ArrayList<>();

        String objectWithMethodCode = visit(objectWithMethod);
        if(!isLiteralOrFunctionVariable(objectWithMethod))
            methodCallCode.append(objectWithMethodCode);


        boolean isStatic = Objects.equals(jmmNode.get("static"), "true");
        String returnType = JmmOptimizationImpl.typeToOllir((Type) jmmNode.getObject("type"));

        for(JmmNode argument:children.subList(1,children.size())) {
            String argumentCode = visit(argument);
            if(!isLiteralOrFunctionVariable(argument)) {
                methodCallCode.append(argumentCode);
            }
            arguments.add(argument.get("var"));

        }

        jmmNode.put("previousCode",methodCallCode.toString());



        String arguments_call = "";
        if(arguments.size()>0)
            arguments_call = ", " + (String)arguments.stream().map((dir) -> {
                return dir;
            }).collect(Collectors.joining(", "));


        if (Objects.equals(returnType, "V")) {
            if(isStatic)
                methodCallCode.append("\t\tinvokestatic(");
            else
                methodCallCode.append("\t\tinvokevirtual(");

            methodCallCode.append(objectWithMethod.get("var")).append(", \"").append(methodName).append("\"").
                    append(arguments_call).append(").V;\n");
            ;
        } else {
            String temp_var = symbolTable.getNewVariable();
            jmmNode.put("var", temp_var + "." + returnType);
            methodCallCode.append("\t\t").append(temp_var).append(".").append(returnType).append(" :=.").append(returnType).append(" ");
            StringBuilder rhsCode = new StringBuilder();

            if(isStatic)
                rhsCode.append("invokestatic(");
            else
                rhsCode.append("invokevirtual(");

            rhsCode.append(objectWithMethod.get("var")).append(", \"").append(methodName).append("\"").
                    append(arguments_call).append(").").append(returnType);

            jmmNode.put("rhsCode", rhsCode.toString());
            methodCallCode.append(rhsCode);
            methodCallCode.append(";\n");
        }


        return methodCallCode.toString();
    }




    private String dealWithIdentifier(JmmNode jmmNode, String s) {
        String varName = jmmNode.get("value");
        String varType = JmmOptimizationImpl.typeToOllir((Type)jmmNode.getObject("type"));
        String idCode = "";
        if(Objects.equals(jmmNode.get("field"), "false") && Objects.equals(jmmNode.get("import"), "false")) {
            StringBuilder code = new StringBuilder();
            if (Objects.equals(jmmNode.get("param"), "true"))
                code.append("$").append(jmmNode.get("offset")).append(".");

            code.append(varName).append(".").append(varType);
            idCode = code.toString();
            jmmNode.put("previousCode",idCode);
            jmmNode.put("var", idCode);
        }
        else {
            if (Objects.equals(jmmNode.get("field"), "true")) {
                String temp_var = symbolTable.getNewVariable();
                temp_var = String.format("%s.%s", temp_var, varType);
                jmmNode.put("var", temp_var);
                idCode =  "\t\t" + temp_var + " :=." + varType +
                        " getfield(this, " + varName + "." + varType + ")." + varType + ";\n";
                jmmNode.put("previousCode",idCode);
                jmmNode.put("var", temp_var);
            }
            else if(Objects.equals(jmmNode.get("import"), "true")){
                idCode = jmmNode.get("value");
                jmmNode.put("previousCode",idCode);
                jmmNode.put("var", idCode);
            }
        }


        return idCode;
    }

    private String dealWithAssignment(JmmNode jmmNode, String s){
        String varName = jmmNode.get("varName");
        String varType = JmmOptimizationImpl.typeToOllir((Type)jmmNode.getObject("type"));
        String idCode = varName+"."+varType;

        StringBuilder code = new StringBuilder();

        JmmNode child = jmmNode.getJmmChild(0);
        String childCode = visit(child);

        if(Objects.equals(jmmNode.get("field"), "true")){
            if (isLiteralOrFunctionVariable(child))
                code.append("\t\tputfield(this, ").append(idCode).append(", ").append(childCode).append(").V;\n");
            else
                code.append(childCode).append("\t\tputfield(this, ").append(idCode).append(", ").append(child.get("var")).append(").V;\n");

        }
        else {
            if (isLiteralOrFunctionVariable(child))
                code.append("\t\t").append(idCode).append(" :=.").append(varType).append(" ").append(childCode).append(";\n");
            else {
                symbolTable.decreaseVariable();
                code.append(child.get("previousCode")).append("\t\t").append(idCode).append(" :=.").append(varType).append(" ").
                        append(child.get("rhsCode")).append(";\n");
                if(isConstructor(child)){
                    code.append("\t\tinvokespecial(").append(idCode).append(",\"<init>\").V;\n");
                }
            }

        }


        return code.toString();

    }

    private String dealWithNegation (JmmNode jmmNode, String s){
        JmmNode child = jmmNode.getJmmChild(0);
        String childCode = visit(child);
        String temp_var = symbolTable.getNewVariable();
        jmmNode.put("var",temp_var+".bool");
        if(isLiteralOrFunctionVariable(child)) {
            jmmNode.put("previousCode", "");
            jmmNode.put("rhsCode", String.format("!.bool %s", childCode));
            return String.format("\t\t%s.bool :=.bool !.bool %s;\n", temp_var, childCode);
        }
        else {
            jmmNode.put("previousCode", childCode);
            jmmNode.put("rhsCode", String.format("!.bool %s", child.get("var")));
            return childCode + String.format("\t\t%s.bool :=.bool !.bool %s;\n", temp_var, child.get("var"));
        }
    }

    private String dealWithBinaryOp(JmmNode jmmNode,String s) {
        JmmNode leftNode = jmmNode.getJmmChild(0);
        JmmNode rightNode = jmmNode.getJmmChild(1);

        String temp_var;


        String type = JmmOptimizationImpl.typeToOllir((Type)jmmNode.getObject("type"));
        jmmNode.put("type",type);

        String leftOperandCode = visit(leftNode);
        String rightOperandCode = visit(rightNode);

        temp_var= symbolTable.getNewVariable();
        jmmNode.put("var", String.format("%s.%s", temp_var, type));


        if (isLiteralOrFunctionVariable(leftNode) && isLiteralOrFunctionVariable(rightNode)) {
            jmmNode.put("previousCode", "");
            jmmNode.put("rhsCode", String.format("%s %s.%s %s", leftOperandCode, jmmNode.get("op"), type, rightOperandCode));
            return String.format("\t\t%s.%s :=.%s %s %s.%s %s;\n", temp_var, type, type, leftOperandCode, jmmNode.get("op"), type, rightOperandCode);
        } else if (isLiteralOrFunctionVariable(leftNode)) {
            jmmNode.put("previousCode", rightOperandCode);
            jmmNode.put("rhsCode", String.format("%s %s.%s %s", leftOperandCode, jmmNode.get("op"), type, rightNode.get("var")));
            return rightOperandCode + String.format("\t\t%s.%s :=.%s %s %s.%s %s;\n", temp_var, type, type, leftOperandCode, jmmNode.get("op"), type, rightNode.get("var"));
        } else if (isLiteralOrFunctionVariable(rightNode)) {
            jmmNode.put("previousCode", leftOperandCode);
            jmmNode.put("rhsCode", String.format("%s %s.%s %s", leftNode.get("var"), jmmNode.get("op"), type, rightOperandCode));
            return leftOperandCode + String.format("\t\t%s.%s :=.%s %s %s.%s %s;\n", temp_var, type, type, leftNode.get("var"), jmmNode.get("op"), type, rightOperandCode);
        } else {
            jmmNode.put("previousCode", leftOperandCode+rightOperandCode);
            jmmNode.put("rhsCode", String.format("%s %s.%s %s",leftNode.get("var"), jmmNode.get("op"), type, rightNode.get("var")));
            return leftOperandCode + rightOperandCode + String.format("\t\t%s.%s :=.%s %s %s.%s %s;\n", temp_var, type, type, leftNode.get("var"), jmmNode.get("op"), type, rightNode.get("var"));

        }
    }

    private String dealWithGrouping(JmmNode jmmNode,String s){
        JmmNode child = jmmNode.getJmmChild(0);
        String code =  visit(child);
        for(String attribute: child.getAttributes())
            jmmNode.put(attribute,child.get(attribute));

        return code;
    }

    private String dealWithExpressionStatement(JmmNode jmmNode,String s){
        JmmNode child = jmmNode.getJmmChild(0);
        String code =  visit(child);

        for(String attribute: child.getAttributes())
            jmmNode.put(attribute,child.get(attribute));

        return code;
    }



    private String dealWithInteger (JmmNode jmmNode, String s){
        jmmNode.put("var",jmmNode.get("value")+".i32");
        return jmmNode.get("value")+".i32";
    }

    private String dealWithBoolean (JmmNode jmmNode, String s){
        jmmNode.put("var",jmmNode.get("value")+".bool");
        return jmmNode.get("value")+".bool";
    }



    private String dealWithConstructor (JmmNode jmmNode, String s){
        String className = jmmNode.get("className");
        String temp_var = symbolTable.getNewVariable();
        jmmNode.put("previousCode", "");
        jmmNode.put("rhsCode", "new("+className+")." +  className);
        jmmNode.put("var", temp_var+"."+className);

        return  "\t\t" + temp_var + "." + className + " :=." + className + " new(" + className + ")." + className + ";\n"
                + "\t\tinvokespecial(" + temp_var+ "." + className+",\"<init>\").V;\n";
    }

    private  String dealWithDefaultVisit (JmmNode jmmNode, String s){
        return visitAllChildren(jmmNode,s);
    }

}
