package pt.up.fe.comp2023.ollir;

import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2023.symbolTable.JmmSymbolTable;

import java.util.*;


public class JmmVisitorForOllir extends AJmmVisitor< String , String > {

    private final JmmSymbolTable symbolTable;

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
        addVisit("ArrayAssignment",this::dealWithArrayAssignment);
        addVisit("Integer",this::dealWithInteger);
        addVisit("Identifier",this::dealWithIdentifier);
        addVisit("BinaryOp",this::dealWithBinaryOp);
        addVisit("And",this::dealWithAnd);
        addVisit("Grouping",this::dealWithGrouping);
        addVisit("Constructor",this::dealWithConstructor);
        addVisit("MethodCall",this::dealWithMethodCall);
        addVisit("Negation",this::dealWithNegation);
        addVisit("Boolean",this::dealWithBoolean);
        addVisit("ExpressionStatement",this::dealWithExpressionStatement);
        addVisit("IfStatement",this::dealWithIfStatement);
        addVisit("WhileStatement",this::dealWithWhileStatement);
        addVisit("BlockOfStatements",this::dealWithBlockOfStatements);
        addVisit("This",this::dealWithThis);
        addVisit("ArrayAccess",this::dealWithArrayAccess);
        addVisit("ArrayConstructor",this::dealWithArrayConstructor);
        addVisit("Length",this::dealWithLength);

        setDefaultVisit(this::dealWithDefaultVisit);
    }



    private Boolean isShortCircuit(JmmNode jmmNode){
        Optional<JmmNode> optionalAncestor = jmmNode.getAncestor("IfStatement");
        if(optionalAncestor.isEmpty())
            optionalAncestor = jmmNode.getAncestor("WhileStatement");
        if(optionalAncestor.isPresent()){
            JmmNode ancestor = optionalAncestor.get();
            JmmNode condition = ancestor.getJmmChild(0);
            JmmNode traveling_node = jmmNode;
            String traveling_node_kind = jmmNode.getKind();
            while(traveling_node!=ancestor && (Objects.equals(traveling_node_kind, "Negation") ||
                                            Objects.equals(traveling_node_kind, "And") || Objects.equals(traveling_node_kind, "Grouping"))){
                if(traveling_node==condition){
                    traveling_node = jmmNode;
                    traveling_node_kind = traveling_node.getKind();
                    if (Objects.equals(traveling_node_kind, "And"))
                        return true;

                    while(Objects.equals(traveling_node_kind, "Negation") || Objects.equals(traveling_node_kind, "Grouping"))
                        {

                        traveling_node = traveling_node.getJmmChild(0);
                        traveling_node_kind = traveling_node.getKind();
                        if (Objects.equals(traveling_node_kind, "And"))
                            return true;
                    }
                    break;
                }
                traveling_node = traveling_node.getJmmParent();
            }
        }
        return false;
    }

    private Boolean isLiteralOrFunctionVariable(JmmNode jmmNode){
        return Objects.equals(jmmNode.getKind(), "Integer") || Objects.equals(jmmNode.getKind(), "Boolean") || Objects.equals(jmmNode.getKind(), "This") || (Objects.equals(jmmNode.getKind(), "Identifier") && Objects.equals(jmmNode.get("field"), "false"))
                || Objects.equals(jmmNode.getKind(), "Grouping") && isLiteralOrFunctionVariable(jmmNode.getJmmChild(0));
    }
    private Boolean isConstructor(JmmNode jmmNode){
        return Objects.equals(jmmNode.getKind(), "Constructor");
    }

    private Boolean isOpInstruction(JmmNode jmmNode){
        return Objects.equals(jmmNode.getKind(), "Negation") || Objects.equals(jmmNode.getKind(), "BinaryOp") || Objects.equals(jmmNode.getKind(), "And")
                || Objects.equals(jmmNode.getKind(), "Grouping") && isOpInstruction(jmmNode.getJmmChild(0));
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
        String returnType = OllirUtils.typeToOllir(ollirReturnType);
        for (int index = 1 + nrArguments+symbolTable.getLocalVariables(functionName).size();index<jmmNode.getNumChildren();index++){
            JmmNode child = jmmNode.getJmmChild(index);
            String childCode = visit(child,"");
            if(index==jmmNode.getNumChildren()-1) {
                if(isLiteralOrFunctionVariable(child)) {
                    methodCode.append("\t\tret.").append(returnType)
                            .append(" ").append(childCode).append(";\n");
                }
                else{
                        methodCode.append(childCode).append("\t\t").append("ret").append(".").append(returnType).append(" ").
                                append(child.get("var")).append(";\n");
                }
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
        methodCode.append("\t\tret.V;\n");
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
        String returnType = OllirUtils.typeToOllir((Type) jmmNode.getObject("type"));

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
            arguments_call = ", " + String.join(", ", arguments);


        if (Objects.equals(returnType, "V")) {
            if(isStatic)
                methodCallCode.append("\t\tinvokestatic(");
            else
                methodCallCode.append("\t\tinvokevirtual(");

            methodCallCode.append(objectWithMethod.get("var")).append(", \"").append(methodName).append("\"").
                    append(arguments_call).append(").V;\n");
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
    private String dealWithBlockOfStatements(JmmNode jmmNode,String s){
        StringBuilder code = new StringBuilder();
        for(JmmNode child:jmmNode.getChildren()){
            code.append(visit(child));
        }
        return code.toString();
    }

    private  String dealWithIfStatement (JmmNode S, String s) {
        JmmNode E = S.getJmmChild(0);
        JmmNode S1 = S.getJmmChild(1);
        JmmNode S2 = S.getJmmChild(2);

        boolean noNext = false;

        E.put("true",symbolTable.getNewLabel());
        E.put("false",symbolTable.getNewLabel());

        if(S.getOptional("next").isEmpty()) {
            S.put("next", symbolTable.getNewLabel());
            noNext = true;
        }


        S1.put("next",S.get("next"));
        S2.put("next",S.get("next"));

        String ECode = visit(E,"");
        String S1Code = visit(S1,"");
        String S2CCode = visit(S2,"");

        String ifCode;
        if(isShortCircuit(E))
            ifCode= ECode; //and Nodes already have the if/else code
        else{
            ifCode = generateIfCode(E,ECode);
        }

        ifCode +=  "\t\t" + E.get("true") + ":\n" +  S1Code + "\t\tgoto " +
                S.get("next") + ";\n" + "\t\t" + E.get("false") + ":\n" + S2CCode;

        if(noNext){
            ifCode += "\t\t" + S.get("next") + ":\n";
        }


        return ifCode;
    }
    private  String dealWithWhileStatement(JmmNode S, String s) {
        JmmNode E = S.getJmmChild(0);
        JmmNode S1 = S.getJmmChild(1);

        boolean noNext = false;




        S.put("begin", getSymbolTable().getNewLabel());
        E.put("true", getSymbolTable().getNewLabel());

        if(S.getOptional("next").isEmpty()) {
            S.put("next", symbolTable.getNewLabel());
            noNext = true;
        }

        E.put("false",S.get("next"));
        S1.put("next",S.get("begin"));

        String ECode = visit(E,"");
        String S1Code = visit(S1,"");


        String expressionCode;
        if(isShortCircuit(E))
            expressionCode = ECode; //and Nodes already have the if/else code
        else{
            expressionCode = generateIfCode(E,ECode);
        }


        String whileCode = "\t\t" + S.get("begin") + ":\n" + expressionCode + "\t\t" + E.get("true") + ":\n" +
                S1Code + "\t\tgoto " + S.get("begin") + ";\n";
        if(noNext){
            whileCode += "\t\t" + S.get("next") + ":\n";
        }

        return whileCode;
    }





    private String dealWithIdentifier(JmmNode jmmNode, String s) {
        String varName = jmmNode.get("value");
        String varType = OllirUtils.typeToOllir((Type)jmmNode.getObject("type"));
        String idCode = "";
        if(Objects.equals(jmmNode.get("field"), "false") && Objects.equals(jmmNode.get("import"), "false")) {
            StringBuilder code = new StringBuilder();
            if (Objects.equals(jmmNode.get("param"), "true"))
                code.append("$").append(jmmNode.get("offset")).append(".");

            code.append(varName).append(".").append(varType);
            idCode = code.toString();
            jmmNode.put("previousCode","");
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
                jmmNode.put("previousCode","");
                jmmNode.put("var", idCode);
            }
        }


        return idCode;
    }

    private String dealWithAssignment(JmmNode jmmNode, String s){
        String varName = jmmNode.get("varName");
        String varType = OllirUtils.typeToOllir((Type)jmmNode.getObject("type"));
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
            if (Objects.equals(jmmNode.get("param"), "true"))
                idCode = "$"+jmmNode.get("offset")+"." + idCode;

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

    private String dealWithArrayAssignment(JmmNode jmmNode, String s) {
        String arrayName = jmmNode.get("array");
        Type arrayMemberType = (Type) jmmNode.getObject("type");
        String arrayMemberTypeString = OllirUtils.typeToOllir(arrayMemberType);
        String arrayTypeString = OllirUtils.typeToOllir(new Type(arrayMemberType.getName(),true));



        StringBuilder code = new StringBuilder();

        JmmNode lengthNode = jmmNode.getJmmChild(0);
        String lengthCode = visit(lengthNode);
        JmmNode rhs = jmmNode.getJmmChild(1);
        if(!isLiteralOrFunctionVariable(rhs))
            symbolTable.decreaseVariable();
        String rhsCode = visit(rhs);
        String arrayToAccess = arrayName;

        if(Objects.equals(jmmNode.get("field"), "true")){
            String temp_var = symbolTable.getNewVariable();
            arrayToAccess = temp_var; //so the array assignment is done to the temp variable
            temp_var = String.format("%s.%s", temp_var,arrayTypeString);
            code.append("\t\t").append(temp_var).append(" :=.").append(arrayTypeString).
                    append(" getfield(this, ").append(temp_var).append(").").
                    append(arrayTypeString).append(";\n");

        }

        if (Objects.equals(jmmNode.get("param"), "true"))
            arrayToAccess = "$"+jmmNode.get("offset")+"." + arrayToAccess;

        if (isLiteralOrFunctionVariable(rhs)) {
            if(isLiteralOrFunctionVariable(lengthNode)){
                code.append("\t\t").append(arrayToAccess).append("[").append(lengthCode).append("].").append(arrayMemberTypeString)
                        .append(" :=.").append(arrayMemberTypeString).append(" ").append(rhsCode).append(";\n");
            }
            else{
                code.append(lengthCode).append("\t\t").append(arrayToAccess).append("[").append(lengthNode.get("var")).append("].").
                        append(arrayMemberTypeString).append(" :=.").append(arrayMemberTypeString).append(" ").append(rhsCode).append(";\n");
            }
        }
        else {
            if(isLiteralOrFunctionVariable(lengthNode)){
                code.append(rhs.get("previousCode")).append("\t\t").append(arrayToAccess).append("[").append(lengthCode).append("].").
                        append(arrayMemberTypeString).append(" :=.").append(arrayMemberTypeString).append(" ").append(rhs.get("rhsCode")).append(";\n");
            }
            else{
                code.append(lengthCode).append(rhs.get("previousCode")).append("\t\t").append(arrayToAccess).append("[").append(lengthNode.get("var")).append("].")
                        .append(arrayMemberTypeString).append(" :=.").append(arrayMemberTypeString).append(" ").append(rhs.get("rhsCode")).append(";\n");
            }

            if(isConstructor(rhs)){
                code.append("\t\tinvokespecial(").append(arrayToAccess).append(",\"<init>\").V;\n");
            }
        }


        if(Objects.equals(jmmNode.get("field"), "true")){
            code.append("\t\tputfield(this, ").append(arrayName).append(".").append(arrayTypeString).append(", ").
                    append(arrayToAccess).append(".").append(arrayTypeString).append(").V;\n");
        }

        return code.toString();
    }

    private String dealWithArrayAccess(JmmNode jmmNode,String s){
        JmmNode arrayToAccessNode = jmmNode.getJmmChild(0);
        String arrayToAccessNodeCode = visit (arrayToAccessNode);
        String[] splittedList = arrayToAccessNode.get("var").split("\\.");
        int i;
        for(i = 0; !Objects.equals(splittedList[i], "array"); i++){

        }
        String arrayToAccess = String.join(".",Arrays.copyOfRange(splittedList, 0, i));


        JmmNode lengthNode = jmmNode.getJmmChild(1);
        String lengthCode = visit(lengthNode);
        String arrayMemberTypeString = OllirUtils.typeToOllir((Type)jmmNode.getObject("type"));
        StringBuilder code = new StringBuilder();

        String temp_var = symbolTable.getNewVariable();
        temp_var = String.format("%s.%s", temp_var, arrayMemberTypeString);
        jmmNode.put("var", temp_var);

        StringBuilder rhsCode = new StringBuilder();

        if (!isLiteralOrFunctionVariable(arrayToAccessNode)) {
            code.append(arrayToAccessNodeCode);
        }


        if(isLiteralOrFunctionVariable(lengthNode)){
            jmmNode.put("previousCode",code.toString());
            rhsCode.append(arrayToAccess).append("[").append(lengthCode).append("].").append(arrayMemberTypeString);
            jmmNode.put("rhsCode",rhsCode.toString());
        }
        else{
            code.append(lengthCode);
            jmmNode.put("previousCode",code.toString());
            rhsCode.append(arrayToAccess).append("[").append(lengthNode.get("var")).append("].").append(arrayMemberTypeString);
            jmmNode.put("rhsCode",rhsCode.toString());
        }
        code.append("\t\t").append(temp_var).append(" :=.").append(arrayMemberTypeString).append(" ").append(rhsCode).append(";\n");

        return code.toString();
    }

    private String dealWithLength(JmmNode jmmNode, String s){
        JmmNode array = jmmNode.getJmmChild(0);
        String arrayCode = visit(array);
        String temp_var = symbolTable.getNewVariable()+".i32";
        jmmNode.put("var",temp_var);

        if(isLiteralOrFunctionVariable(array)){
            jmmNode.put("previousCode","");
            String rhsCode = "arraylength("+arrayCode+").i32";
            jmmNode.put("rhsCode",rhsCode);
            return "\t\t"+temp_var+" :=.i32 " + rhsCode + ";\n";
        }
        else{
            jmmNode.put("previousCode",arrayCode);
            String rhsCode = "arraylength("+array.get("var")+ ").i32";
            jmmNode.put("rhsCode",rhsCode);
            return arrayCode + "\t\t"+temp_var+" :=.i32 " + rhsCode + ";\n";
        }
    }

    private String dealWithNegation (JmmNode jmmNode, String s){
        JmmNode child = jmmNode.getJmmChild(0);
        if(isShortCircuit(jmmNode)){
            child.put("true",jmmNode.get("false"));
            child.put("false",jmmNode.get("true"));
            return visit(child);
        }
        else {
            String childCode = visit(child);
            String temp_var = symbolTable.getNewVariable();
            jmmNode.put("var", temp_var + ".bool");
            if (isLiteralOrFunctionVariable(child)) {
                jmmNode.put("previousCode", "");
                jmmNode.put("rhsCode", String.format("!.bool %s", childCode));
                return String.format("\t\t%s.bool :=.bool !.bool %s;\n", temp_var, childCode);
            } else {
                jmmNode.put("previousCode", childCode);
                jmmNode.put("rhsCode", String.format("!.bool %s", child.get("var")));
                return childCode + String.format("\t\t%s.bool :=.bool !.bool %s;\n", temp_var, child.get("var"));
            }
        }
    }


    private String generateIfCode(JmmNode node,String nodeCode){
        if (isLiteralOrFunctionVariable(node)) {
            return String.format("\t\tif (%s) goto %s;\n\t\tgoto %s;\n",
                    nodeCode,node.get("true"),node.get("false"));
        }
        else{
            if(isOpInstruction(node)) {
                symbolTable.decreaseVariable();
                return node.get("previousCode") + String.format("\t\tif (%s) goto %s;\n\t\tgoto %s;\n",
                        node.get("rhsCode"), node.get("true"), node.get("false"));
            }
            else{
                return nodeCode + String.format("\t\tif (%s) goto %s;\n\t\tgoto %s;\n",
                        node.get("var"),node.get("true"),node.get("false"));
            }
        }
    }

    private String dealWithAnd(JmmNode jmmNode,String s){
        if(isShortCircuit(jmmNode)){
            JmmNode leftNode = jmmNode.getJmmChild(0);
            JmmNode rightNode = jmmNode.getJmmChild(1);

            leftNode.put("true", symbolTable.getNewLabel());
            leftNode.put("false",jmmNode.get("false"));
            rightNode.put("true", jmmNode.get("true"));
            rightNode.put("false",jmmNode.get("false"));

            String leftNodeCode = visit(leftNode);
            String rightNodeCode = visit(rightNode);

            String firstIf;
            String secondIf;

            if(Objects.equals(leftNode.getKind(), "And")){
                firstIf =  leftNodeCode;
            }
            else{
                firstIf = generateIfCode(leftNode,leftNodeCode);
            }

            secondIf = generateIfCode(rightNode,rightNodeCode);

            return firstIf + "\t\t" + leftNode.get("true")+":\n" + secondIf;
        }
        else{
            return dealWithBinaryOp(jmmNode,s);
        }
    }


    private String dealWithBinaryOp(JmmNode jmmNode,String s) {
        JmmNode leftNode = jmmNode.getJmmChild(0);
        JmmNode rightNode = jmmNode.getJmmChild(1);

        String temp_var;


        String type = OllirUtils.typeToOllir((Type)jmmNode.getObject("type"));
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
        if(isShortCircuit(jmmNode)){
            child.put("true",jmmNode.get("true"));
            child.put("false",jmmNode.get("false"));
            return visit(child);
        }
        else {
            String code = visit(child);
            for (String attribute : child.getAttributes())
                jmmNode.put(attribute, child.get(attribute));
            return code;
        }
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
        int value = Objects.equals(jmmNode.get("value"), "true") ?1:0;
        jmmNode.put("var", value + ".bool");
        return value + ".bool";
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

    private String dealWithArrayConstructor(JmmNode jmmNode, String s) {
        JmmNode lengthNode = jmmNode.getJmmChild(0);
        String lengthCode = visit(lengthNode);
        Type arrayType = (Type)jmmNode.getObject("type");
        String arrayTypeOllir = OllirUtils.typeToOllir(arrayType);
        String temp_var = symbolTable.getNewVariable() + "." + arrayTypeOllir;
        if(isLiteralOrFunctionVariable(lengthNode)){
            String rhsCode = "new(array, "+ lengthCode +")."+arrayTypeOllir;
            jmmNode.put("previousCode", "");
            jmmNode.put("rhsCode", rhsCode);
            jmmNode.put("var", temp_var);
            return "\t\t" + temp_var + " :=." + arrayTypeOllir + " " +  rhsCode+";\n";
        }
        else{
            String rhsCode = "new(array, "+ lengthNode.get("var") +")."+arrayTypeOllir;
            jmmNode.put("previousCode", lengthCode);
            jmmNode.put("rhsCode", rhsCode);
            jmmNode.put("var", temp_var);
            return lengthCode + "\t\t" + temp_var + " :=." + arrayTypeOllir + " " +  rhsCode + ";\n";
        }
    }

    private  String dealWithDefaultVisit (JmmNode jmmNode, String s){
        return visitAllChildren(jmmNode,s);
    }

}
