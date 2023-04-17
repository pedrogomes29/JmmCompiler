package pt.up.fe.comp2023.analysis;

import pt.up.fe.comp.jmm.analysis.JmmAnalysis;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.parser.JmmParserResult;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2023.symbolTable.JmmSymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class JmmAnalysisImpl implements JmmAnalysis {
    @Override
    public JmmSemanticsResult semanticAnalysis(JmmParserResult jmmParserResult) {

        JmmNode rootNode = jmmParserResult.getRootNode();
        JmmSymbolTable symbolTable;
        try {
            symbolTable = new JmmSymbolTable(rootNode);
        }
        catch (Exception e) {
            Report report = new Report(ReportType.ERROR, Stage.SEMANTIC, -1, -1, e.getMessage());
            List<Report> reports = new ArrayList<>();
            reports.add(report);
            return new JmmSemanticsResult(jmmParserResult, null, reports);
        }
        System.out.println(rootNode.toTree());
        // Type verification
        List<Report> reports = new ArrayList<>();
        verifyIdentifiers(rootNode, symbolTable, reports);
        verifyAssignments(rootNode, symbolTable, reports);
        verifyTypes(rootNode, reports);
        verifyExpressionsInConditions(rootNode, reports);
        verifyArrayAccess(rootNode, reports);
        verifyArguments(rootNode, symbolTable, reports);
        verifyReturn(rootNode, symbolTable, reports);
        verifyFields(rootNode, symbolTable, reports);
        return new JmmSemanticsResult(jmmParserResult, symbolTable, reports);
    }

    private void verifyFields(JmmNode node, JmmSymbolTable symbolTable, List<Report> reports) {
        if(Objects.equals(node.getKind(), "Identifier")) {
            String varName = (String) node.get("value");
            Optional<JmmNode> staticMethodNode = node.getAncestor("StaticMethod");
            if (staticMethodNode.isPresent() && Objects.equals(node.get("field"), "true")) {
                Report report = new Report(ReportType.ERROR, Stage.SEMANTIC, -1, -1, "Cannot access non-static variable " + varName + " from a static context");
                reports.add(report);
            }

        }

    }

    private void verifyIdentifiers(JmmNode node, JmmSymbolTable symbolTable, List<Report> reports) {
        if (Objects.equals(node.getKind(), "Identifier")) {
            String name = (String) node.get("value");
            if (!symbolTable.containsIdentifier(name)) {
                Report report = new Report(ReportType.ERROR, Stage.SEMANTIC, -1, -1, "Identifier " + name + " not declared");
                reports.add(report);
            }
        }
        for (JmmNode child : node.getChildren()) {
            verifyIdentifiers(child, symbolTable, reports);
        }
    }
    private void verifyTypes(JmmNode node, List<Report> reports) {
        if (Objects.equals(node.getKind(), "BinaryOp")) {
            JmmNode left = node.getChildren().get(0);
            JmmNode right = node.getChildren().get(1);
            Type leftType = (Type)left.getObject("type");
            Type rightType = (Type)right.getObject("type");
            String op = (String) node.get("op");
            if (!areTypesCompatible(leftType, rightType, op)) {
                Report report = new Report(ReportType.ERROR, Stage.SEMANTIC, -1, -1, "Incompatible types " + leftType + " and " + rightType + " for operator " + op);
                reports.add(report);
            }
        }
        for (JmmNode child : node.getChildren()) {
            verifyTypes(child, reports);
        }
    }
    private void verifyExpressionsInConditions(JmmNode node, List<Report> reports) {
        if (Objects.equals(node.getKind(), "IfStatement") || Objects.equals(node.getKind(), "WhileStatement")) {
            JmmNode condition = node.getChildren().get(0);
            String conditionType = ((Type)condition.getObject("type")).getName();
            if (!conditionType.equals("boolean")) {
                Report report = new Report(ReportType.ERROR, Stage.SEMANTIC, -1, -1, "Condition must be of type boolean");
                reports.add(report);
            }
        }
        for (JmmNode child : node.getChildren()) {
            verifyExpressionsInConditions(child, reports);
        }
    }
    private void verifyAssignments(JmmNode node, JmmSymbolTable symbolTable, List<Report> reports) {
        if (Objects.equals(node.getKind(), "Assignment")) {
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
                Report report = new Report(ReportType.ERROR, Stage.SEMANTIC, -1, -1, "Incompatible types " + childType + " and " + assignmentType + " for assignment");
                reports.add(report);
            }
            if (!childType.equals(assignmentType) && (assignmentType.equals("int") || assignmentType.equals("boolean") || assignmentType.equals("int[]"))){
                Report report = new Report(ReportType.ERROR, Stage.SEMANTIC, -1, -1, "Incompatible types " + childType + " and " + assignmentType + " for assignment");
                reports.add(report);
            }
            //falta ver se são arrays int[] y;int x = y
            //int y; int[]x = y;
            if (assignmentType.equals("int[]") && !childType.equals("int[]")) {
                Report report = new Report(ReportType.ERROR, Stage.SEMANTIC, -1, -1, "Incompatible types " + childType + " and " + assignmentType + " for assignment");
                reports.add(report);
            }
        }
        for (JmmNode child_ : node.getChildren()) {
            verifyAssignments(child_, symbolTable, reports);
        }
    }
    private void verifyArrayAccess(JmmNode node,List<Report> reports) {
        if (Objects.equals(node.getKind(), "ArrayAccess")) {
            if(node.getChildren().size() != 2){
                Report report = new Report(ReportType.ERROR, Stage.SEMANTIC, -1, -1, "Array access must have 2 children");
                reports.add(report);
            }
            JmmNode child = node.getChildren().get(0);
            JmmNode index = node.getChildren().get(1);
            Type childType = (Type) child.getObject("type");
            Type indexType = (Type) index.getOptionalObject("type").orElse(new Type("int", false));
            if (!childType.isArray()) {
                Report report = new Report(ReportType.ERROR, Stage.SEMANTIC, -1, -1, "Cannot access array");
                reports.add(report);
            }
            if (!indexType.getName().equals("int") || indexType.isArray()) {
                Report report = new Report(ReportType.ERROR, Stage.SEMANTIC, -1, -1, "Array index must be of type int");
                reports.add(report);
            }
        }
        for (JmmNode child_ : node.getChildren()) {
            verifyArrayAccess(child_, reports);
        }
    }
    private void verifyArguments(JmmNode node, JmmSymbolTable symbolTable, List<Report> reports) {
        if (Objects.equals(node.getKind(), "MethodCall")) {
            if (node.get("isImported").equals("true")){
                return;
            }
            String methodName = (String) node.get("methodName");
            JmmNode methodNode = symbolTable.getMethodNode(methodName);
            List<JmmNode> arguments = node.getChildren().subList(1, node.getChildren().size());
            List<Symbol> parameters = symbolTable.getParameters(methodName);
            if (arguments.size() != parameters.size()) {
                Report report = new Report(ReportType.ERROR, Stage.SEMANTIC, -1, -1, "Wrong number of arguments for method " + methodName);
                reports.add(report);
            }
            else {
                for (int i = 0; i < arguments.size(); i++) {
                    JmmNode argument = arguments.get(i);
                    Symbol parameter = parameters.get(i);
                    String argumentType = ((Type) argument.getObject("type")).getName();
                    String parameterType = ((Type) parameter.getType()).getName();
                    if (!argumentType.equals(parameterType)) {
                        Report report = new Report(ReportType.ERROR, Stage.SEMANTIC, -1, -1, "Incompatible types " + argumentType + " and " + parameterType + " for argument " + i + " of method " + methodName);
                        reports.add(report);
                    }
                }
            }
        }
        for (JmmNode child : node.getChildren()) {
            verifyArguments(child, symbolTable, reports);
        }
    }
    private void verifyReturn(JmmNode node, JmmSymbolTable symbolTable, List<Report> reports) {
        if (Objects.equals(node.getKind(), "NormalMethod")) {
            String methodName = node.get("functionName");
            String returnType = ((Type) symbolTable.getReturnType(methodName)).getName();
            JmmNode child = node.getChildren().get(node.getNumChildren() - 1);
            String childType = ((Type) child.getObject("type")).getName();
            if (!childType.equals(returnType)) {
                Report report = new Report(ReportType.ERROR, Stage.SEMANTIC, -1, -1, "Incompatible types " + childType + " and " + returnType + " for return");
                reports.add(report);
            }
        }
        for (JmmNode child : node.getChildren()) {
            verifyReturn(child, symbolTable, reports);
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
