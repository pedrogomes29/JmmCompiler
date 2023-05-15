package pt.up.fe.comp2023.analysis;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2023.symbolTable.JmmSymbolTable;

import java.util.*;

public class JmmVisitorForAnalysis extends PreorderJmmVisitor< String , String > {

    private JmmSymbolTable symbolTable;
    private List<Report> reports;

    public JmmVisitorForAnalysis(JmmSymbolTable symbolTable){
        buildVisitor();
        this.symbolTable = symbolTable;
        this.reports =  new ArrayList<>();;
    }


    public List<Report> getReports(){
        return reports;
    }

    @Override
    protected void buildVisitor() {
        setDefaultVisit(this::dealWithDefaultVisit);
    }

    private  String dealWithDefaultVisit (JmmNode jmmNode, String s) {
        verifyArguments(jmmNode);
        verifyAssignments(jmmNode);
        verifyFields(jmmNode);
        verifyIdentifiers(jmmNode);
        verifyTypes(jmmNode);
        verifyReturn(jmmNode);
        verifyArrayAccess(jmmNode);
        verifyExpressionsInConditions(jmmNode);
        return "";
    }

    private void verifyFields(JmmNode node) {
        if(Objects.equals(node.getKind(), "Identifier") && node.getOptional("undeclaredID").isEmpty()){
            String varName = (String) node.get("value");
            Optional<JmmNode> staticMethodNode = node.getAncestor("StaticMethod");
            if (staticMethodNode.isPresent() && Objects.equals(node.get("field"), "true")) {
                Report report = new Report(ReportType.ERROR, Stage.SEMANTIC, -1, -1, "Cannot access non-static variable " + varName + " from a static context");
                reports.add(report);
            }

        }
    }

    private void verifyIdentifiers(JmmNode node) {
        if (Objects.equals(node.getKind(), "Identifier")) {
            String name = (String) node.get("value");
            if (node.getOptional("undeclaredID").isPresent()) {
                Report report = AnalysisUtils.newErrorReport( "Identifier " + name + " not declared",node);
                reports.add(report);
            }
        }
    }
    private void verifyTypes(JmmNode node) {
        if (Objects.equals(node.getKind(), "BinaryOp") || Objects.equals(node.getKind(), "And")) {
            JmmNode left = node.getChildren().get(0);
            JmmNode right = node.getChildren().get(1);
            Type leftType = (Type)left.getObject("type");
            Type rightType = (Type)right.getObject("type");
            String op = (String) node.get("op");
            if (!areTypesCompatible(leftType, rightType, op)) {
                Report report = AnalysisUtils.newErrorReport("Incompatible types " + leftType + " and " + rightType + " for operator " + op,node);
                reports.add(report);
            }
        }
    }
    private void verifyExpressionsInConditions(JmmNode node) {
        if (Objects.equals(node.getKind(), "IfStatement") || Objects.equals(node.getKind(), "WhileStatement")) {
            JmmNode condition = node.getChildren().get(0);
            String conditionType = ((Type)condition.getObject("type")).getName();
            if (!conditionType.equals("boolean")) {
                Report report = AnalysisUtils.newErrorReport("Condition must be of type boolean",node);
                reports.add(report);
            }
        }
    }
    private void verifyAssignments(JmmNode node) {
        if(Objects.equals(node.getKind(), "Assignment") && node.getOptional("undeclaredID").isEmpty()){
            String varName = node.get("varName");
            Optional<JmmNode> staticMethodNode = node.getAncestor("StaticMethod");
            if (staticMethodNode.isPresent() && Objects.equals(node.get("field"), "true")) {
                Report report = new Report(ReportType.ERROR, Stage.SEMANTIC, -1, -1, "Cannot access non-static variable " + varName + " from a static context");
                reports.add(report);
            }
            JmmNode child = node.getChildren().get(0);
            String childType = ((Type) child.getObject("type")).getName();
            String assignmentType = ((Type) node.getObject("type")).getName();
            String className = symbolTable.getClassName();
            String superClassName = symbolTable.getSuper();
            if (assignmentType.equals(className) && !childType.equals(className) && (!childType.equals(superClassName))) {
                Report report = AnalysisUtils.newErrorReport("Incompatible types " + childType + " and " + assignmentType + " for assignment",node);
                reports.add(report);
            }
            if (!childType.equals(assignmentType) && (assignmentType.equals("int") || assignmentType.equals("boolean") || assignmentType.equals("int[]"))){
                Report report = AnalysisUtils.newErrorReport("Incompatible types " + childType + " and " + assignmentType + " for assignment",node);
                reports.add(report);
            }
            //falta ver se são arrays int[] y;int x = y
            //int y; int[]x = y;
            if (assignmentType.equals("int[]") && !childType.equals("int[]")) {
                Report report = AnalysisUtils.newErrorReport("Incompatible types " + childType + " and " + assignmentType + " for assignment",node);
                reports.add(report);
            }
        }
    }
    private void verifyArrayAccess(JmmNode node) {
        if (Objects.equals(node.getKind(), "ArrayAccess")) {
            if(node.getChildren().size() != 2){
                Report report = AnalysisUtils.newErrorReport("Array access must have 2 children",node);
                reports.add(report);
            }
            JmmNode child = node.getChildren().get(0);
            JmmNode index = node.getChildren().get(1);
            Type childType = (Type) child.getObject("type");
            Type indexType = (Type) index.getOptionalObject("type").orElse(new Type("int", false));
            if (!childType.isArray()) {
                Report report = AnalysisUtils.newErrorReport( "Cannot access array",node);
                reports.add(report);
            }
            if (!indexType.getName().equals("int") || indexType.isArray()) {
                Report report = AnalysisUtils.newErrorReport("Array index must be of type int",node);
                reports.add(report);
            }
        }
    }
    private void verifyArguments(JmmNode node) {
        if (Objects.equals(node.getKind(), "MethodCall")) {
            if (node.get("isImported").equals("true")){
                return;
            }
            String methodName = (String) node.get("methodName");
            JmmNode methodNode = symbolTable.getMethodNode(methodName);
            List<JmmNode> arguments = node.getChildren().subList(1, node.getChildren().size());
            List<Symbol> parameters = symbolTable.getParameters(methodName);
            if (arguments.size() != parameters.size()) {
                Report report = AnalysisUtils.newErrorReport( "Wrong number of arguments for method " + methodName,node);
                reports.add(report);
            }
            else {
                for (int i = 0; i < arguments.size(); i++) {
                    JmmNode argument = arguments.get(i);
                    Symbol parameter = parameters.get(i);
                    String argumentType = ((Type) argument.getObject("type")).getName();
                    String parameterType = ((Type) parameter.getType()).getName();
                    if (!argumentType.equals(parameterType)) {
                        Report report = AnalysisUtils.newErrorReport("Incompatible types " + argumentType + " and " + parameterType +
                                " for argument " + i + " of method " + methodName,node);
                        reports.add(report);
                    }
                    if(((Type) argument.getObject("type")).isArray() != ((Type) parameter.getType()).isArray()){
                        Report report = AnalysisUtils.newErrorReport( "Incompatible types " + argument.getObject("type") + " and " +
                                parameter.getType() + " for argument " + i + " of method " + methodName,node);
                        reports.add(report);
                    }
                }
            }
        }
    }
    private void verifyReturn(JmmNode node) {
        if (Objects.equals(node.getKind(), "NormalMethod")) {
            String methodName = node.get("functionName");
            String returnType = ((Type) symbolTable.getReturnType(methodName)).getName();
            JmmNode child = node.getChildren().get(node.getNumChildren() - 1);
            String childType = ((Type) child.getObject("type")).getName();
            if (!childType.equals(returnType)) {
                Report report = AnalysisUtils.newErrorReport("Incompatible types " + childType + " and " + returnType + " for return",node);
                reports.add(report);
            }
        }
    }
    private boolean areTypesCompatible(Type leftType, Type rightType, String op) {
        boolean b = leftType.getName().equals("int") && rightType.getName().equals("int");
        if (op.equals("+") || op.equals("-") || op.equals("*") || op.equals("/")) {
            return b && !leftType.isArray() && !rightType.isArray();
        }
        if (op.equals("<") || op.equals(">") || op.equals("<=") || op.equals(">=")) {
            return b && !leftType.isArray() && !rightType.isArray();
        }
        if (op.equals("==") || op.equals("!=")) {
            return b || (leftType.getName().equals("boolean") && rightType.getName().equals("boolean"));
        }
        if (op.equals("&&") || op.equals("||")) {
            return leftType.getName().equals("boolean") && rightType.getName().equals("boolean") && !leftType.isArray() && !rightType.isArray();
        }
        if (op.equals("=")) {
            return b || (leftType.getName().equals("boolean") && rightType.getName().equals("boolean"));
        }
        return false;
    }
}

