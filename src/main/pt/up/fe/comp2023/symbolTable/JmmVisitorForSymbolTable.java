package pt.up.fe.comp2023.symbolTable;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;

import java.util.*;

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
        addVisit("RelOp",this::dealWithBinaryOp);
        addVisit("And",this::dealWithBinaryOp);
        addVisit("Grouping",this::dealWithGrouping);
        addVisit("Constructor",this::dealWithConstructor);
        addVisit("MethodCall",this::dealWithMethodCall);
        addVisit("Negation",this::dealWithNegation);
        addVisit("Boolean",this::dealWithBoolean);
        addVisit("ExpressionStatement",this::dealWithExpressionStatement);
        addVisit("IfStatement",this::dealWithIfStatement);
        addVisit("WhileStatement",this::dealWithWhileStatement);
        addVisit("This",this::dealWithThis);
        addVisit("ArrayConstructor",this::dealWithArrayConstructor);
        addVisit("ArrayAccess",this::dealWithArrayAccess);
        addVisit("ArrayAssignment",this::dealWithArrayAssignment);
        setDefaultVisit(this::dealWithDefaultVisit);
    }

    private String dealWithArrayAssignment(JmmNode jmmNode, String s) {
        String arrayName = jmmNode.get("array");
        dealWithId(jmmNode,arrayName);
        visit(jmmNode.getJmmChild(0),"");
        visit(jmmNode.getJmmChild(1),"");
         JmmNode index = jmmNode.getJmmChild(0);
         JmmNode value = jmmNode.getJmmChild(1);
        if (!index.getObject("type").equals(new Type("int",false))){
            throw new RuntimeException("Array index must be an integer");
        }
        if (!value.getObject("type").equals(new Type("int",false))){
            throw new RuntimeException("Array value must be an integer");
        }
        if(jmmNode.getOptional("undeclaredID").isPresent() || jmmNode.get("import").equals("true")){
            throw new RuntimeException("Array " + arrayName + " is not declared");
        }
        jmmNode.putObject("type",new Type("int",false));
        jmmNode.putObject("array",arrayName);
        return "";
    }

    private String dealWithArrayAccess(JmmNode jmmNode, String s) {
        String arrayName = visit(jmmNode.getJmmChild(0),"");
        String index = visit(jmmNode.getJmmChild(1),"");
        String code = "\t\t" + arrayName + " = " + arrayName + "[" + index + "];\n";
        jmmNode.putObject("type",new Type("int",false));
        return code;
    }

    private String dealWithIfStatement(JmmNode jmmNode, String s) {
        return visitAllChildren(jmmNode,"");
    }

    private String dealWithWhileStatement(JmmNode jmmNode, String s) {
        return visitAllChildren(jmmNode,"");
    }

    private String dealWithProgram (JmmNode jmmNode , String s) {

        for ( JmmNode child : jmmNode.getChildren ()){
            visit(child,"");
        }
        return "";
    }
    private String dealWithImport (JmmNode jmmNode , String s) {
        symbolTable.addImport(jmmNode.get("className"));
        symbolTable.setImportPackage(jmmNode.get("className"),(List<String>) jmmNode.getObject("path"));
        return "";
    }

    private String dealWithClassDeclaration (JmmNode jmmNode , String s) {
        symbolTable.setClassName(jmmNode.get("className"));
        if(jmmNode.hasAttribute("classParentName"))
            symbolTable.setSuper(jmmNode.get("classParentName"));
        else
            symbolTable.setSuper(null);

        jmmNode.putObject("methodStatements",new ArrayList<>());
        for ( JmmNode child : jmmNode.getChildren()){
            visit(child,"");
        }
        List<JmmNode> methodStatements = (List<JmmNode>)jmmNode.getObject("methodStatements");

        for(JmmNode methodStatement:methodStatements){
            visit(methodStatement,"");
        }
        return "";
    }

    private String dealWithNormalMethod(JmmNode jmmNode , String s) {
        String functionName = jmmNode.get("functionName");
        symbolTable.addMethod(functionName);
        symbolTable.addMethodNode(functionName,jmmNode);

        String type = visit(jmmNode.getJmmChild(0),"");
        Boolean isArray=false;
        if (type.endsWith("[]")) {
            isArray=true;
            type = type.substring(0, type.length() - 2);
        }

        Type returnType = new Type(type,isArray);
        symbolTable.setReturnType(functionName,returnType);
        symbolTable.setLocalVariables(functionName,new ArrayList<>());
        symbolTable.setParameters(functionName,new ArrayList<>());
        symbolTable.setIsStatic(functionName,false);

        jmmNode.putObject("type", returnType);


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
            if(!Objects.equals(child.getKind(), "VarDeclaration"))
                break;
            else
                visit(child,"");
        }

        List<JmmNode> methodStatements = (List<JmmNode>)jmmNode.getJmmParent().getObject("methodStatements");
        for (;index<jmmNode.getNumChildren();index++){
            JmmNode child = jmmNode.getJmmChild(index);
            methodStatements.add(child);
        }
        jmmNode.getJmmParent().putObject("methodStatements",methodStatements);
        return "";
    }

    private String dealWithStaticMethod(JmmNode jmmNode , String s) {
        String functionName = jmmNode.get("functionName");
        symbolTable.addMethodNode(functionName,jmmNode);
        symbolTable.addMethod(functionName);
        symbolTable.setReturnType(functionName,new Type("void",false));
        symbolTable.setLocalVariables(functionName,new ArrayList<>());
        symbolTable.setParameters(functionName,new ArrayList<>());

        String argumentName = jmmNode.get("argument");
        jmmNode.putObject("type",new Type("void",false));

        symbolTable.addParameters(functionName,new Symbol(new Type("String",true),argumentName));
        symbolTable.setIsStatic(functionName,true);
        int index = 0;
        for (;index<jmmNode.getNumChildren();index++){
            JmmNode child = jmmNode.getJmmChild(index);
            if(!Objects.equals(child.getKind(), "VarDeclaration"))
                break;
            else
                visit(child,"");
        }

        List<JmmNode> methodStatements = (List<JmmNode>)jmmNode.getJmmParent().getObject("methodStatements");
        for (;index<jmmNode.getNumChildren();index++){
            JmmNode child = jmmNode.getJmmChild(index);
            methodStatements.add(child);
        }
        jmmNode.getJmmParent().putObject("methodStatements",methodStatements);
        return "";
    }

    private Type deduceReturnType(JmmNode jmmNode){
        String methodName = jmmNode.get("methodName");
        JmmNode parent = jmmNode.getJmmParent();
        switch (parent.getKind()) {
            case "ArrayAccess" -> {
                return new Type("int", false);
            }
            case "BinaryOp"->{
                String op = parent.get("op");
                if(Objects.equals(op, "&&"))
                    return new Type("boolean", false);
                else
                    return new Type("int", false);

            }
            case "MethodCall" -> {
                JmmNode parentObjectWithMethod = parent.getJmmChild(0);
                if (parentObjectWithMethod.getOptionalObject("type").isPresent() &&
                        Objects.equals(((Type) parentObjectWithMethod.getObject("type")).getName(), symbolTable.getClassName()) &&
                        symbolTable.getMethods().contains(parent.get("methodName")))
                    return symbolTable.getParameters(parent.get("methodName")).get(jmmNode.getIndexOfSelf() - 1).getType();
                else
                    throw new RuntimeException("Can't deduce return type of " + methodName);

            }
            default -> {
                return (Type) jmmNode.getJmmParent().getObject("type");
            }
        }
    }

    private String dealWithMethodCall(JmmNode jmmNode, String s){
        List<JmmNode> children = jmmNode.getChildren();
        String methodName = jmmNode.get("methodName");
        JmmNode objectWithMethod = children.get(0);
        visit(objectWithMethod);

        boolean isStatic = true;
        Type returnType = null;

        if(objectWithMethod.get("import").equals("true")){ //imported class static method
            jmmNode.put("isImported","true");
            returnType = deduceReturnType(jmmNode);
        }
        else {
            jmmNode.put("import","false");//result of method call can never be a static reference to a class
            jmmNode.put("isImported","false");
            boolean isImported = false;
            String className = ((Type)objectWithMethod.getObject("type")).getName();
            for (String imported_class : symbolTable.getImports()) {
                if (Objects.equals(imported_class, className)) {
                    jmmNode.put("isImported","true");
                    isImported = true; //imported class normal method
                    isStatic = false;
                    break;
                }
                else jmmNode.put("isImported","false");
            }
            if(isImported) {
                jmmNode.put("isImported","true");
                returnType = deduceReturnType(jmmNode);
            }
            else{
                if(!symbolTable.getMethods().contains(methodName)){
                    if(symbolTable.getSuper()!=null){
                        jmmNode.put("isImported","true");
                        returnType = deduceReturnType(jmmNode);
                        isStatic = Objects.equals(objectWithMethod.getKind(), "Identifier") && objectWithMethod.get("value")==symbolTable.getClassName();
                    }
                    else
                        throw new RuntimeException("Method " + methodName + " not found");
                }
                else{
                    returnType = symbolTable.getReturnType(methodName);
                    isStatic = symbolTable.methodIsStatic(methodName);
                }
            }
        }
        jmmNode.putObject("type",returnType);
        jmmNode.put("static",isStatic?"true":"false");

        for(JmmNode argument:children.subList(1,children.size())) {
            visit(argument);
        }

        return "";
    }

    private String dealWithVarDeclaration (JmmNode jmmNode, String s){
        String typeString = visit(jmmNode.getJmmChild(0),"");
        Boolean isArray=false;
        if (typeString.endsWith("[]")) {
            isArray=true;
            typeString = typeString.substring(0, typeString.length() - 2);
        }

        JmmNode parent = jmmNode.getJmmParent();
        String parentKind = parent.getKind();
        String varName = jmmNode.get("varName");


        Type type = new Type(typeString,isArray);
        jmmNode.putObject("type",type);


        if(parentKind.equals("ClassDeclaration")){
            symbolTable.addField(new Symbol(type,jmmNode.get("varName")));
        }
        else if(parentKind.equals("NormalMethod") || parentKind.equals("StaticMethod")){
            symbolTable.addLocalVariables(parent.get("functionName"),new Symbol(new Type(typeString,isArray),varName));
        }


        return "";
    }

    private String dealWithType(JmmNode jmmNode,String s){
        if((Boolean) jmmNode.getObject("isArray"))
            return jmmNode.get("value")+"[]";
        else
            return jmmNode.get("value");
    }

    private String dealWithIdentifier(JmmNode jmmNode, String s) {
        String varName = jmmNode.get("value");
        String typeString = dealWithId(jmmNode,varName);
        Boolean isArray=false;
        if (typeString.endsWith("[]")) {
            isArray=true;
            typeString = typeString.substring(0, typeString.length() - 2);
        }

        Type type = new Type(typeString,isArray);
        jmmNode.putObject("type",type);
        return "";
    }
    private String dealWithId(JmmNode jmmNode,String varName) {
        Optional<JmmNode> methodNode = jmmNode.getAncestor("NormalMethod");
        String methodName;

        if (methodNode.isEmpty())
            methodNode = jmmNode.getAncestor("StaticMethod");


        if (methodNode.isPresent()) {
            methodName = methodNode.get().get("functionName");
            List<Symbol> localVars = symbolTable.getLocalVariables(methodName);
            for (Symbol localVar : localVars) {
                String localVarName = localVar.getName();
                if (Objects.equals(localVarName, varName)) {
                    Type type = localVar.getType();
                    jmmNode.putObject("type", type);
                    jmmNode.put("param", "false");
                    jmmNode.put("field", "false");
                    jmmNode.put("localVar", "true");
                    jmmNode.put("import","false");
                    jmmNode.put("offset", "0");
                    return type.getName() + (type.isArray() ? "[]" : "");
                }
            }

            List<Symbol> parameters = symbolTable.getParameters(methodName);
            int param_offset = 0;
            if (!symbolTable.methodIsStatic(methodName))
                param_offset = 1;
            for (int i = 0; i < parameters.size(); i++) {
                Symbol paramater = parameters.get(i);
                String paramaterName = paramater.getName();
                if (Objects.equals(paramaterName, varName)) {
                    Type type = paramater.getType();
                    jmmNode.putObject("type", type);
                    jmmNode.put("param", "true");
                    jmmNode.put("field", "false");
                    jmmNode.put("localVar", "false");
                    jmmNode.put("import","false");
                    jmmNode.put("offset", String.valueOf(param_offset + i));
                    return type.getName() + (type.isArray() ? "[]" : "");

                }
            }

            List<Symbol> fields = symbolTable.getFields();
            for (int i = 0; i < fields.size(); i++) {
                Symbol paramater = fields.get(i);
                String paramaterName = paramater.getName();
                if (Objects.equals(paramaterName, varName)) {
                    Type type = paramater.getType();
                    jmmNode.putObject("type", type);
                    jmmNode.put("param", "false");
                    jmmNode.put("field", "true");
                    jmmNode.put("localVar", "false");
                    jmmNode.put("offset", "0");
                    jmmNode.put("import","false");
                    return type.getName() + (type.isArray() ? "[]" : "");
                }
            }
            for (String imported_class : symbolTable.getImports()) {
                if (Objects.equals(imported_class, varName)) {
                    jmmNode.putObject("type",new Type(imported_class,false));
                    jmmNode.put("import","true");
                    jmmNode.put("param","false");
                    jmmNode.put("field","false");
                    jmmNode.put("localVar","false");
                    jmmNode.put("offset","0");
                    return imported_class;
                }
            }

        }

        jmmNode.put("undeclaredID","true");
        return "";
    }

    private String dealWithAssignment(JmmNode jmmNode, String s){
        String varName = jmmNode.get("varName");
        String typeString = dealWithId(jmmNode,varName);
        Boolean isArray=false;
        if (typeString.endsWith("[]")) {
            isArray=true;
            typeString = typeString.substring(0, typeString.length() - 2);
        }

        Type type = new Type(typeString,isArray);
        jmmNode.putObject("type",type);

        JmmNode child = jmmNode.getJmmChild(0);
        visit(child);

        return "";
    }

    private String dealWithNegation (JmmNode jmmNode, String s){
        JmmNode child = jmmNode.getJmmChild(0);
        jmmNode.putObject("type",new Type("boolean",false));
        return visit(child);
    }

    private String dealWithBinaryOp(JmmNode jmmNode,String s) {
        String type = null;
        if(Objects.equals(jmmNode.get("op"), "+") || Objects.equals(jmmNode.get("op"), "-") ||
                Objects.equals(jmmNode.get("op"), "/") || Objects.equals(jmmNode.get("op"), "*"))
            type = "int";
        else if (Objects.equals(jmmNode.get("op"), "&&") || Objects.equals(jmmNode.get("op"), "<") )
            type = "boolean";

        jmmNode.putObject("type",new Type(type,false));

        return visitAllChildren(jmmNode,"");
    }

    private String dealWithGrouping(JmmNode jmmNode,String s){
        visitAllChildren(jmmNode,"");
        JmmNode child = jmmNode.getJmmChild(0);
        if(Objects.equals(child.getKind(), "MethodCall"))
            jmmNode.putObject("type",jmmNode.getJmmParent().getObject("type"));
        else{
            for(String attribute: child.getAttributes())
                jmmNode.putObject(attribute,child.getObject(attribute));
        }
        return "";
    }

    private String dealWithExpressionStatement(JmmNode jmmNode,String s){
        jmmNode.putObject("type",new Type("void",false));
        return visitAllChildren(jmmNode,"");
    }



    private String dealWithInteger (JmmNode jmmNode, String s){
        jmmNode.putObject("type",new Type("int",false));
        return "";
    }

    private String dealWithBoolean (JmmNode jmmNode, String s){
        jmmNode.putObject("type",new Type("boolean",false));
        return "";
    }



    private String dealWithConstructor (JmmNode jmmNode, String s){
        String className = symbolTable.getClassName();
        jmmNode.putObject("type", new Type(className,false));
        jmmNode.put("import","false");
        jmmNode.put("param","false");
        jmmNode.put("field","false");
        jmmNode.put("localVar","false");
        jmmNode.put("offset","0");
        return "";

    }

    private  String dealWithThis (JmmNode jmmNode, String s) {
        Optional<JmmNode> staticMethodNode = jmmNode.getAncestor("StaticMethod");
        if(staticMethodNode.isPresent()){
            throw new RuntimeException("Cannot use this in a static method");
        }
        String className = symbolTable.getClassName();
        jmmNode.putObject("type", new Type(className,false));
        jmmNode.put("import","false");
        jmmNode.put("param","false");
        jmmNode.put("field","false");
        jmmNode.put("localVar","false");
        jmmNode.put("offset","0");
        return "";
    }


    private String dealWithArrayConstructor(JmmNode jmmNode, String s) {
        visit(jmmNode.getJmmChild(0));
        jmmNode.putObject("type", new Type("int",true));
        return "";
    }
    private  String dealWithDefaultVisit (JmmNode jmmNode, String s){
        return visitAllChildren(jmmNode,s);
    }

}
