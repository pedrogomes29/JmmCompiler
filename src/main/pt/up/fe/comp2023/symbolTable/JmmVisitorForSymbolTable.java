package pt.up.fe.comp2023.symbolTable;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2023.ollir.JmmOptimizationImpl;

import java.util.*;
import java.util.stream.Collectors;

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
        addVisit("Grouping",this::dealWithGrouping);
        addVisit("Constructor",this::dealWithConstructor);
        addVisit("MethodCall",this::dealWithMethodCall);
        addVisit("Negation",this::dealWithNegation);
        addVisit("Boolean",this::dealWithBoolean);
        addVisit("ExpressionStatement",this::dealWithExpressionStatement);
        addVisit("IfStatement",this::dealWithIfStatement);
        addVisit("This",this::dealWithThis);
        addVisit("ArrayConstructor",this::dealWithArrayConstructor);
        setDefaultVisit(this::dealWithDefaultVisit);
    }

    private String dealWithIfStatement(JmmNode jmmNode, String s) {
        String condition = visit(jmmNode.getJmmChild(0),"");
        String ifCode = visit(jmmNode.getJmmChild(1),"");
        String elseCode = "";
        if(jmmNode.getNumChildren()==3)
            elseCode = visit(jmmNode.getJmmChild(2),"");
        return "\t\tif " + condition + " {\n" + ifCode + "\t\t}\n" + elseCode;
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

        Type returnType = new Type(type,isArray);
        String ollirReturnType = JmmOptimizationImpl.typeToOllir(returnType);
        symbolTable.setReturnType(functionName,returnType);
        symbolTable.setLocalVariables(functionName,new ArrayList<>());
        symbolTable.setParameters(functionName,new ArrayList<>());
        symbolTable.setIsStatic(functionName,false);

        jmmNode.put("type", ollirReturnType);


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
                if(isLiteralOrFunctionVariable(child)) {
                    methodCode.append("\t\tret.").append(ollirReturnType)
                                .append(" ").append(childCode).append(";\n");
                }
                else
                    methodCode.append(childCode).append("\t\t").append("ret").append(".").append(ollirReturnType).append(" ").
                            append(child.get("var")).append(";\n");
            }
            else
                methodCode.append(childCode);
        }

        symbolTable.setMethodOllirCode(functionName,methodCode.toString());
        return "";
    }

    private String dealWithMethodCall(JmmNode jmmNode, String s){
        List<JmmNode> children = jmmNode.getChildren();
        String methodName = jmmNode.get("methodName");
        JmmNode objectWithMethod = children.get(0);
        String objectWithMethodCode = visit(objectWithMethod);
        StringBuilder methodCallCode = new StringBuilder();
        List<String> arguments = new ArrayList<>();

        if(!isLiteralOrFunctionVariable((objectWithMethod)))
            methodCallCode.append(objectWithMethodCode);


        boolean isStatic = true;
        String returnType = null;

        if(objectWithMethod.get("import").equals("true")){ //imported class static method
            returnType = jmmNode.getJmmParent().get("type");

        }
        else {
            jmmNode.put("import","false"); //result of method call can never be a static reference to a class
            boolean isImported = false;
            String[] splitedList = objectWithMethod.get("var").split("\\.");
            String className = splitedList[splitedList.length - 1];
            for (String imported_class : symbolTable.getImports()) {
                if (Objects.equals(imported_class, className)) {
                    isStatic = false;
                    isImported = true; //imported class normal method
                    break;
                }
            }

            if(isImported)
                returnType = jmmNode.getJmmParent().get("type");
            else{
                isStatic = symbolTable.methodIsStatic(methodName);
                returnType = JmmOptimizationImpl.typeToOllir(symbolTable.getReturnType(methodName));
            }
        }
        jmmNode.put("type",returnType);


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

    private String dealWithIdentifier(JmmNode jmmNode, String s) {
        String varName = jmmNode.get("value");
        String idCode = dealWithId(jmmNode, varName);

        String[] splitedList = idCode.split("\\.");
        boolean isArray = false;
        for (int i = 0; i < splitedList.length-1; i++) {
            if (splitedList[i].equals("array")) {
                isArray = true;
                break;
            }
        }

        jmmNode.put("isArray", isArray ? "true" : "false"); // Set the isArray attribute based on the result of the check
        String varType = splitedList[splitedList.length-1];
        if(Objects.equals(jmmNode.get("field"), "false") && Objects.equals(jmmNode.get("import"), "false"))
            jmmNode.put("var",varName+"."+varType);
        else
            jmmNode.put("var",varName);
        jmmNode.put("type",varType);
        jmmNode.put("previousCode",idCode);
        return idCode;
    }
    private String dealWithId(JmmNode jmmNode,String varName) {
        Optional<JmmNode> methodNode = jmmNode.getAncestor("NormalMethod");
        String methodName;
        String varType = null;
        Boolean isLocalVar = false;
        Boolean isField = true;
        Integer param_offset = 0;





        if (methodNode.isEmpty())
            methodNode = jmmNode.getAncestor("StaticMethod");

        for (String imported_class : symbolTable.getImports()) {
            if (Objects.equals(imported_class, varName)) {
                jmmNode.put("import","true");
                jmmNode.put("field", "false");
                jmmNode.put("var",imported_class);
                return imported_class;
            }
        }

        jmmNode.put("import","false");






        if (methodNode.isPresent()) {
            methodName = methodNode.get().get("functionName");
            List<Symbol> localVars = symbolTable.getLocalVariables(methodName);
            for (Symbol localVar : localVars) {
                String localVarName = localVar.getName();
                if (Objects.equals(localVarName, varName)) {
                    varType = JmmOptimizationImpl.typeToOllir(localVar.getType());
                    isLocalVar = true;
                    isField = false;
                }
            }

            if (!isLocalVar) {
                List<Symbol> parameters = symbolTable.getParameters(methodName);
                if (!symbolTable.methodIsStatic(methodName))
                    param_offset = 1;
                for (int i = 0; i < parameters.size(); i++) {
                    Symbol paramater = parameters.get(i);
                    String paramaterName = paramater.getName();
                    if (Objects.equals(paramaterName, varName)) {
                        varType = JmmOptimizationImpl.typeToOllir(paramater.getType());
                        param_offset += i;
                        isField = false;
                    }
                }
            }
        }

        if (isField) {
            List<Symbol> parameters = symbolTable.getFields();
            for (int i = 0; i < parameters.size(); i++) {
                Symbol paramater = parameters.get(i);
                String paramaterName = paramater.getName();
                if (Objects.equals(paramaterName, varName)) {
                    varType = JmmOptimizationImpl.typeToOllir(paramater.getType());
                }
            }
            jmmNode.put("field", "true");

            if(Objects.equals(jmmNode.getKind(), "Assignment"))
                return varName + "." + varType;
            else {
                String temp_var = symbolTable.getNewVariable();
                temp_var = String.format("%s.%s", temp_var, varType);
                jmmNode.put("var", temp_var);
                return "\t\t" + temp_var + " :=." + varType +
                        " getfield(this, " + varName + "." + varType + ")." + varType + ";\n";
            }
        }
        else {
            StringBuilder code = new StringBuilder();

            jmmNode.put("field", "false");
            if (!isLocalVar)
                code.append("$").append(param_offset).append(".");


            code.append(varName).append(".").append(varType);

            return code.toString();
        }
    }

    private String dealWithAssignment(JmmNode jmmNode, String s){
        String varName = jmmNode.get("varName");
        String idCode = dealWithId(jmmNode,varName);
        String[] splitedList = idCode.split("\\.");
        String varType = splitedList[splitedList.length - 1];
        jmmNode.put("type",varType);

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
        jmmNode.put("type","bool");
        String childCode = visit(child);
        String temp_var = symbolTable.getNewVariable();
       if(isLiteralOrFunctionVariable(child)) {
           jmmNode.put("previousCode", "");
           jmmNode.put("rhsCode", String.format("!.bool %s", childCode));
           return String.format("\t\t%s.bool :=.bool !.bool %s;\n", temp_var, childCode);
       }
       else {
           jmmNode.put("previousCode", childCode);
           jmmNode.put("rhsCode", String.format("!.bool %s", childCode));
           return childCode + String.format("\t\t%s.bool :=.bool !.bool %s;\n", temp_var, childCode);       }
    }

    private String dealWithBinaryOp(JmmNode jmmNode,String s) {
        JmmNode leftNode = jmmNode.getJmmChild(0);
        JmmNode rightNode = jmmNode.getJmmChild(1);




        String temp_var;
        temp_var= symbolTable.getNewVariable();

        String type = null;
        if(Objects.equals(jmmNode.get("op"), "+") || Objects.equals(jmmNode.get("op"), "-") ||
                Objects.equals(jmmNode.get("op"), "/") || Objects.equals(jmmNode.get("op"), "*"))
            type = "i32";
        else if (Objects.equals(jmmNode.get("op"), "&&") || Objects.equals(jmmNode.get("op"), "<") )
            type = "bool";

        jmmNode.put("type",type);
        jmmNode.put("var", String.format("%s.%s", temp_var, type));

        String leftOperandCode = visit(leftNode);
        String rightOperandCode = visit(rightNode);

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
        jmmNode.put("type",jmmNode.getJmmParent().get("type"));
        String code =  visit(child);
        for(String attribute: child.getAttributes())
            jmmNode.put(attribute,child.get(attribute));

        return code;
    }

    private String dealWithExpressionStatement(JmmNode jmmNode,String s){
        JmmNode child = jmmNode.getJmmChild(0);
        jmmNode.put("type","V");
        String code =  visit(child);

        for(String attribute: child.getAttributes())
            jmmNode.put(attribute,child.get(attribute));

        return code;
    }



    private String dealWithInteger (JmmNode jmmNode, String s){
        jmmNode.put("var",jmmNode.get("value")+".i32");
        jmmNode.put("type","int");
        jmmNode.put("isArray","false");
        return jmmNode.get("value")+".i32";
    }

    private String dealWithBoolean (JmmNode jmmNode, String s){
        /*
        String int_conversion_of_bool = null;
        if(Objects.equals(jmmNode.get("value"), "false"))
            int_conversion_of_bool = "0";
        else if (Objects.equals(jmmNode.get("value"), "true"))
            int_conversion_of_bool = "1";

        jmmNode.put("var",int_conversion_of_bool+".i32");
        return int_conversion_of_bool+".i32";
        */
        jmmNode.put("var",jmmNode.get("value")+".bool");
        jmmNode.put("type","bool");
        jmmNode.put("isArray","false");
        return jmmNode.get("value")+".bool";
    }



    private String dealWithConstructor (JmmNode jmmNode, String s){
        String className = jmmNode.get("className");
        String temp_var = symbolTable.getNewVariable();
        jmmNode.put("previousCode", "");
        jmmNode.put("rhsCode", "new("+className+")." +  className);
        jmmNode.put("var", temp_var+"."+className);
        jmmNode.put("type", className);

        return  "\t\t" + temp_var + "." + className + " :=." + className + " new(" + className + ")." + className + ";\n"
                + "\t\tinvokespecial(" + temp_var+ "." + className+",\"<init>\").V;\n";
    }

    private  String dealWithThis (JmmNode jmmNode, String s) {
        jmmNode.put("var", "this");
        jmmNode.put("import","false");
        return "this";

    }
    private  String dealWithDefaultVisit (JmmNode jmmNode, String s){
        return "";
    }

    private String dealWithArrayConstructor(JmmNode jmmNode, String s) {
        String temp_var = symbolTable.getNewVariable();
        String type = "int[]";
        JmmNode sizeNode = jmmNode.getChildren().get(0);
        String sizeCode = sizeNode.get("value");
        jmmNode.put("var", temp_var + "." + type);
        jmmNode.put("type", type);
        jmmNode.put("previousCode", "");
        jmmNode.put("rhsCode", "newarray(" + sizeCode + ")." + type);
        return "\t" + temp_var + "." + type + " := newarray(" + sizeCode + ")." + type + ";\n";
    }

}
