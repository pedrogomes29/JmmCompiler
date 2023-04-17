package pt.up.fe.comp2023.analysis;

import pt.up.fe.comp.jmm.analysis.JmmAnalysis;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
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
        return new JmmSemanticsResult(jmmParserResult, symbolTable, reports);
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
            Type indexType = (Type) index.getObject("type");
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
