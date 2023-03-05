package pt.up.fe.comp2023;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class JmmSymbolTable extends AJmmVisitor< String , String > implements SymbolTable{
    private List<String> imports;
    private String className;
    private String superName;
    private List<Symbol> fields;
    private List<String> methods;
    private HashMap<String,Type> methodToReturnType;
    private HashMap<String,List<Symbol>> methodToParamaters;
    private HashMap<String,List<Symbol>> methodToLocalVars;

    public JmmSymbolTable(JmmNode node){
        imports= new ArrayList<>();
        fields = new ArrayList<>();
        methods = new ArrayList<>();;
        methodToReturnType = new HashMap<>();
        methodToParamaters = new HashMap<>();
        methodToLocalVars = new HashMap<>();
        buildVisitor();
        this.visit(node);
        System.out.println(print());
    }

    @Override
    public List<String> getImports() {
        return imports;
    }

    @Override
    public String getClassName() {
        return className;
    }

    @Override
    public String getSuper() {
        return superName;
    }

    @Override
    public List<Symbol> getFields() {
        return fields;
    }

    @Override
    public List<String> getMethods() {
        return methods;
    }

    @Override
    public Type getReturnType(String s) {
        return methodToReturnType.get(s);
    }

    @Override
    public List<Symbol> getParameters(String s) {
        return methodToParamaters.get(s);
    }

    @Override
    public List<Symbol> getLocalVariables(String s) {
        return methodToLocalVars.get(s);
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
        imports.add(jmmNode.get("className"));
        return "";
    }

    private String dealWithClassDeclaration (JmmNode jmmNode , String s) {
        className = jmmNode.get("className");
        if(jmmNode.hasAttribute("classParentName"))
            superName = jmmNode.get("classParentName");
        else
            superName = "No super";

        for ( JmmNode child : jmmNode.getChildren ()){
            visit(child,"");
        }
        return "";
    }

    private String dealWithNormalMethod(JmmNode jmmNode , String s) {
        methods.add(jmmNode.get("functionName"));

        String type = visit(jmmNode.getJmmChild(0),"");
        Boolean isArray=false;
        if (type.endsWith("[]")) {
            isArray=true;
            type = type.substring(0, type.length() - 2);
        }
        methodToReturnType.put(jmmNode.get("functionName"),new Type(type,isArray));
        methodToLocalVars.put(jmmNode.get("functionName"),new ArrayList<>());
        methodToParamaters.put(jmmNode.get("functionName"),new ArrayList<>());
        for ( JmmNode child : jmmNode.getChildren ()){
            visit(child,"");
        }
        return "";
    }

    private String dealWithStaticMethod(JmmNode jmmNode , String s) {
        methods.add(jmmNode.get("functionName"));
        methodToReturnType.put(jmmNode.get("functionName"),new Type("void",false));
        methodToLocalVars.put(jmmNode.get("functionName"),new ArrayList<>());
        methodToParamaters.put(jmmNode.get("functionName"),new ArrayList<>());
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


        if(jmmNode.getJmmParent().getKind().equals("ClassDeclaration")){
            fields.add(new Symbol(new Type(type,isArray),jmmNode.get("varName")));
        }
        else if(jmmNode.getJmmParent().getKind().equals("NormalMethod") || jmmNode.getJmmParent().getKind().equals("StaticMethod")){
            methodToLocalVars.get(jmmNode.getJmmParent().get("functionName")).add(new Symbol(new Type(type,isArray),jmmNode.get("varName")));
        }


        return "";
    }

    private String dealWithType(JmmNode jmmNode,String s){
        if(jmmNode.hasAttribute("array"))
            return jmmNode.get("value")+"[]";
        else
            return jmmNode.get("value");
    }

    private  String dealWithDefaultVisit (JmmNode jmmNode, String s){
        return "";
    }

}
