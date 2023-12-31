package pt.up.fe.comp2023.symbolTable;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class JmmSymbolTable implements SymbolTable{
    private List<String> imports;
    private String className;
    private String superName;
    private List<Symbol> fields;
    private List<String> methods;
    private HashMap<String,Type> methodToReturnType;
    private HashMap<String,List<Symbol>> methodToParamaters;
    private HashMap<String,List<Symbol>> methodToLocalVars;
    private HashMap<String,Boolean> methodIsStatic;
    private HashMap<String,String> methodToOllirCode;

    private HashMap<String, JmmNode> methodToNode;

    private HashMap<String,List<String>> importToPackage;

    private Integer lastUsedVariable;

    private Integer lastUsedLabel;

    public JmmSymbolTable(JmmNode node){
        imports = new ArrayList<>();
        fields = new ArrayList<>();
        methods = new ArrayList<>();;
        methodToReturnType = new HashMap<>();
        methodToParamaters = new HashMap<>();
        methodToLocalVars = new HashMap<>();
        methodIsStatic = new HashMap<>();
        methodToOllirCode = new HashMap<>();
        importToPackage = new HashMap<>();
        methodToNode = new HashMap<>();
        lastUsedVariable = -1;
        lastUsedLabel = -1;
        JmmVisitorForSymbolTable gen = new JmmVisitorForSymbolTable(this);
        gen.visit(node);
    }

    @Override
    public List<String> getImports() {
        return imports;
    }

    public void addImport(String anImport) {
        this.imports.add(anImport);
    }


    @Override
    public String getClassName() {
        return className;
    }

    public void setClassName (String className) {
        this.className = className;
    }

    @Override
    public String getSuper() {
        return superName;
    }

    public void setSuper (String superName) {
        this.superName = superName;
    }

    @Override
    public List<Symbol> getFields() {
        return fields;
    }

    public void addField(Symbol field) {
        this.fields.add(field);
    }

    @Override
    public List<String> getMethods() {
        return methods;
    }

    public void addMethod(String method) {
        this.methods.add(method);
    }

    @Override
    public Type getReturnType(String s) {
        return methodToReturnType.get(s);
    }

    public void setReturnType(String method, Type returnType) {
        this.methodToReturnType.put(method, returnType);
    }


    @Override
    public List<Symbol> getParameters(String s) {
        return methodToParamaters.get(s);
    }

    public void setParameters(String method, List<Symbol> parameters) {
        this.methodToParamaters.put(method, parameters);
    }

    public void addParameters(String method, Symbol parameter) {
        this.methodToParamaters.get(method).add(parameter);
    }

    @Override
    public List<Symbol> getLocalVariables(String s) {
        return methodToLocalVars.get(s);
    }

    public void setLocalVariables(String method, List<Symbol> localVariables) {
        this.methodToLocalVars.put(method, localVariables);
    }

    public void addLocalVariables(String method, Symbol localVariable) {
        this.methodToLocalVars.get(method).add(localVariable);
    }

    public void setIsStatic(String method,Boolean isStatic){
        this.methodIsStatic.put(method,isStatic);
    }

    public Boolean methodIsStatic(String method){
        return this.methodIsStatic.get(method);
    }

    public void setMethodOllirCode(String method,String ollirCode){
        this.methodToOllirCode.put(method,ollirCode);
    }

    public String getMethodOllirCode(String method){
        return this.methodToOllirCode.get(method);
    }

    public String getNewVariable(){
       return String.format("t%s",++lastUsedVariable);
    }

    public String getNewLabel(){
        return String.format("label%s",++lastUsedLabel);
    }


    public void decreaseVariable(){lastUsedVariable--;}

    public void setImportPackage(String import_,List<String> package_){
        this.importToPackage.put(import_,package_);
    }

    public List<String> getImportPackage(String import_){
        return this.importToPackage.get(import_);
    }

    public JmmNode getMethodNode(String method){
        return methodToNode.get(method);
    }
    public void addMethodNode(String method,JmmNode node){
        methodToNode.put(method,node);
    }
}
