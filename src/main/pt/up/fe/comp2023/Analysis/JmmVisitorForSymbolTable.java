package pt.up.fe.comp2023.Analysis;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;

import java.util.ArrayList;
import java.util.List;

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

        int index=1;
        for(var argumentName: (List<String>)jmmNode.getObject("arguments")) {
            JmmNode typeNode = jmmNode.getJmmChild(index);
            type = typeNode.get("value");
            isArray = (Boolean) typeNode.getObject("isArray");
            symbolTable.addParameters(functionName,new Symbol(new Type(type,isArray),argumentName));
            index++;
        }
        for (;index<jmmNode.getNumChildren();index++){
            JmmNode child = jmmNode.getJmmChild(index);
            visit(child,"");
        }
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

        for ( JmmNode child : jmmNode.getChildren ()){
            visit(child,"");
        }
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

    private  String dealWithDefaultVisit (JmmNode jmmNode, String s){
        return "";
    }

}
