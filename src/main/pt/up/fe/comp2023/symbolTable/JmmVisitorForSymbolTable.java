package pt.up.fe.comp2023.symbolTable;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2023.ollir.JmmOptimizationImpl;
import pt.up.fe.comp2023.symbolTable.JmmSymbolTable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class JmmVisitorForSymbolTable extends AJmmVisitor< String , String >{

    private JmmSymbolTable symbolTable;

    public JmmVisitorForSymbolTable(JmmSymbolTable symbolTable){
        buildVisitor();
        this.symbolTable = symbolTable;
    }


    @Override
    protected void buildVisitor() {
        addVisit ("Program", this::dealWithProgram );
        addVisit("ImportDeclaration", this::dealWithImport);
        addVisit("ClassDeclaration", this::dealWithClassDeclaration);
        addVisit("VarDeclaration", this::dealWithVarDeclaration);
        addVisit("NormalMethod", this::dealWithNormalMethod);
        addVisit("StaticMethod", this::dealWithStaticMethod);
        addVisit("Type", this::dealWithType);
        addVisit("Assignment",this::dealWithAssignment);
        addVisit("Integer",this::dealWithInteger);
        addVisit("Identifier",this::dealWithIdentifier);
        addVisit("BinaryOp",this::dealWithBinaryOp);
        setDefaultVisit(this::dealWithDefaultVisit);
    }



    private String dealWithProgram (JmmNode jmmNode , String s) {

        for ( JmmNode child : jmmNode.getChildren ()){
            visit(child,"");
        }
        return "";
    }
    private String dealWithImport (JmmNode jmmNode , String s) {
        symbolTable.addImport(jmmNode.get("className"));
        return "";
    }

    private String dealWithClassDeclaration (JmmNode jmmNode , String s) {
        symbolTable.setClassName(jmmNode.get("className"));
        if(jmmNode.hasAttribute("classParentName"))
            symbolTable.setSuper(jmmNode.get("classParentName"));
        else
            symbolTable.setSuper(null);

        for ( JmmNode child : jmmNode.getChildren()){
            visit(child,"");
        }
        return "";
    }

    private String dealWithNormalMethod(JmmNode jmmNode , String s) {
        String functionName = jmmNode.get("functionName");
        symbolTable.addMethod(functionName);

        String type = visit(jmmNode.getJmmChild(0),"");
        Boolean isArray=false;
        if (type.endsWith("[]")) {
            isArray=true;
            type = type.substring(0, type.length() - 2);
        }


        symbolTable.setReturnType(functionName,new Type(type,isArray));
        symbolTable.setLocalVariables(functionName,new ArrayList<>());
        symbolTable.setParameters(functionName,new ArrayList<>());
        symbolTable.setIsStatic(functionName,false);


        int index=1;
        for(var argumentName: (List<String>)jmmNode.getObject("arguments")) {
            JmmNode typeNode = jmmNode.getJmmChild(index);
            type = typeNode.get("value");
            isArray = (Boolean) typeNode.getObject("isArray");
            symbolTable.addParameters(functionName,new Symbol(new Type(type,isArray),argumentName));
            index++;
        }
        StringBuilder methodCode = new StringBuilder();
        for (;index<jmmNode.getNumChildren();index++){
            JmmNode child = jmmNode.getJmmChild(index);
            String childCode = visit(child,"");
            if(index==jmmNode.getNumChildren()-1) {
                String returnType = JmmOptimizationImpl.typeToOllir(symbolTable.getReturnType(functionName));
                if(isLiteralOrID(child))
                    methodCode.append("\t\tret.").append(returnType)
                        .append(" ").append(childCode).append(";\n");
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

        symbolTable.addMethod(functionName);
        symbolTable.setReturnType(functionName,new Type("void",false));
        symbolTable.setLocalVariables(functionName,new ArrayList<>());
        symbolTable.setParameters(functionName,new ArrayList<>());

        String argumentName = jmmNode.get("argument");


        symbolTable.addParameters(functionName,new Symbol(new Type("String",true),argumentName));
        symbolTable.setIsStatic(functionName,true);

        StringBuilder methodCode = new StringBuilder();
        for (JmmNode child:jmmNode.getChildren()){
            String childCode = visit(child,"");
            methodCode.append(childCode);
        }

        symbolTable.setMethodOllirCode(functionName,methodCode.toString());
        return "";
    }
    private  String dealWithVarDeclaration (JmmNode jmmNode, String s){
        String type = visit(jmmNode.getJmmChild(0),"");
        Boolean isArray=false;
        if (type.endsWith("[]")) {
            isArray=true;
            type = type.substring(0, type.length() - 2);
        }

        JmmNode parent = jmmNode.getJmmParent();
        String parentKind = parent.getKind();
        String varName = jmmNode.get("varName");

        if(parentKind.equals("ClassDeclaration")){
            symbolTable.addField(new Symbol(new Type(type,isArray),jmmNode.get("varName")));
        }
        else if(parentKind.equals("NormalMethod") || parentKind.equals("StaticMethod")){
            symbolTable.addLocalVariables(parent.get("functionName"),new Symbol(new Type(type,isArray),varName));
        }


        return "";
    }

    private String dealWithType(JmmNode jmmNode,String s){
        if((Boolean) jmmNode.getObject("isArray"))
            return jmmNode.get("value")+"[]";
        else
            return jmmNode.get("value");
    }

    private String dealWithIdentifier(JmmNode jmmNode,String s){
        String varName = jmmNode.get("value");
        String idCode = dealWithId(jmmNode,varName);

        String[] splitedList = idCode.split("\\.");
        String varType = splitedList[splitedList.length-1];

        jmmNode.put("var",varName+"."+varType);
        return idCode;
    }
    private String dealWithId(JmmNode jmmNode,String varName){
        Optional<JmmNode> methodNode = jmmNode.getAncestor("NormalMethod");
        String methodName;
        String varType = null;
        Boolean isLocalVar = false;
        Integer param_offset=0;
        if(methodNode.isEmpty())
            methodNode = jmmNode.getAncestor("StaticMethod");

        if(methodNode.isPresent()){
            methodName = methodNode.get().get("functionName");
            List<Symbol> localVars = symbolTable.getLocalVariables(methodName);
            for (Symbol localVar : localVars) {
                String localVarName = localVar.getName();
                if (Objects.equals(localVarName, varName)) {
                    varType = JmmOptimizationImpl.typeToOllir(localVar.getType());
                    isLocalVar = true;
                }
            }

            if(!isLocalVar) {
                List<Symbol> parameters = symbolTable.getParameters(methodName);
                if(!symbolTable.methodIsStatic(methodName))
                    param_offset = 1;
                for (int i=0;i<parameters.size();i++) {
                    Symbol paramater = parameters.get(i);
                    String paramaterName = paramater.getName();
                    if (Objects.equals(paramaterName, varName)) {
                        varType = JmmOptimizationImpl.typeToOllir(paramater.getType());
                        param_offset += i;
                    }
                }
            }
        }
        StringBuilder code = new StringBuilder();
        if(!isLocalVar)
            code.append("$").append(param_offset).append(".");

        code.append(varName).append(".").append(varType);

        return code.toString();
    }
    private String dealWithAssignment(JmmNode jmmNode, String s){
        String varName = jmmNode.get("varName");
        String idCode = dealWithId(jmmNode,varName);

        String[] splitedList = idCode.split("\\.");
        String varType = splitedList[splitedList.length-1];


        StringBuilder code = new StringBuilder();

        JmmNode child = jmmNode.getJmmChild(0);
        String childCode = visit(child);
        if(isLiteralOrID(child))
            code.append("\t\t").append(idCode).append(" :=.").append(varType).append(" ").append(childCode).append(";\n");
        else
            code.append(child.get("operandsCode")).append("\t\t").append(idCode).append(" :=.").append(varType).append(" ").
                    append(child.get("rhsCode")).append(";\n");



        return code.toString();
    }

    private Boolean isLiteralOrID(JmmNode jmmNode){
     return Objects.equals(jmmNode.getKind(), "Integer") || Objects.equals(jmmNode.getKind(), "True") ||
             Objects.equals(jmmNode.getKind(), "False") || Objects.equals(jmmNode.getKind(), "Identifier");
    }



    private String dealWithBinaryOp(JmmNode jmmNode,String s) {
        JmmNode leftNode = jmmNode.getJmmChild(0);
        JmmNode rightNode = jmmNode.getJmmChild(1);

        String leftOperandCode = visit(leftNode);
        String rightOperandCode = visit(rightNode);

        String temp_var = symbolTable.getNewVariable();

        String[] splitedList = leftNode.get("var").split("\\.");
        String type = splitedList[splitedList.length - 1];
        jmmNode.put("var", String.format("%s.%s", temp_var, type));


        if (isLiteralOrID(leftNode) && isLiteralOrID(rightNode)) {
            jmmNode.put("operandsCode", "");
            jmmNode.put("rhsCode", String.format("%s %s.%s %s", leftOperandCode, jmmNode.get("op"), type, rightOperandCode));
            return String.format("\t\t%s.%s :=.%s %s %s.%s %s;\n", temp_var, type, type, leftOperandCode, jmmNode.get("op"), type, rightOperandCode);
        } else if (isLiteralOrID(leftNode)) {
            jmmNode.put("operandsCode", rightOperandCode);
            jmmNode.put("rhsCode", String.format("%s %s.%s %s", leftOperandCode, jmmNode.get("op"), type, rightNode.get("var")));
            return rightOperandCode + String.format("\t\t%s.%s :=.%s %s %s.%s %s;\n", temp_var, type, type, leftOperandCode, jmmNode.get("op"), type, rightNode.get("var"));
        } else if (isLiteralOrID(rightNode)) {
            jmmNode.put("operandsCode", leftOperandCode);
            jmmNode.put("rhsCode", String.format("%s %s.%s %s", leftNode.get("var"), jmmNode.get("op"), type, rightOperandCode));
            return leftOperandCode + String.format("\t\t%s.%s :=.%s %s %s.%s %s;\n", temp_var, type, type, leftNode.get("var"), jmmNode.get("op"), type, rightOperandCode);
        } else {
            jmmNode.put("operandsCode", leftOperandCode+rightOperandCode);
            jmmNode.put("rhsCode", String.format("%s %s.%s %s",leftNode.get("var"), jmmNode.get("op"), type, rightNode.get("var")));
            return leftOperandCode + rightOperandCode + String.format("\t\t%s.%s :=.%s %s %s.%s %s;\n", temp_var, type, type, leftNode.get("var"), jmmNode.get("op"), type, rightNode.get("var"));

        }
    }

    private String dealWithGrouping(JmmNode jmmNode,String s){
        return visit(jmmNode.getJmmChild(0));
    }
    private String dealWithInteger (JmmNode jmmNode, String s){
        jmmNode.put("var",jmmNode.get("value")+".i32");
        return jmmNode.get("value")+".i32";
    }
    private  String dealWithDefaultVisit (JmmNode jmmNode, String s){
        return "";
    }

}
