package pt.up.fe.comp2023.analysis;

import pt.up.fe.comp.jmm.analysis.JmmAnalysis;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.parser.JmmParserResult;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2023.symbolTable.JmmSymbolTable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class JmmAnalysisImpl implements JmmAnalysis {
    @Override
    public JmmSemanticsResult semanticAnalysis(JmmParserResult jmmParserResult) {

        JmmNode rootNode = jmmParserResult.getRootNode();
        JmmSymbolTable symbolTable = new JmmSymbolTable(rootNode);
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
            String leftType = left.get("type");
            String rightType = right.get("type");
            String op = (String) node.get("op");
            String isLeftArray = (String) left.get("isArray");
            String isRightArray = (String) right.get("isArray");
            if (!areTypesCompatible(leftType, rightType, isLeftArray, isRightArray, op)) {
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
            String conditionType = condition.get("type");
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
            String childType = child.get("type");
            String assignmentType = node.get("type");
            if (childType.equals("i32")) {
                childType = "int";
            }
            if (assignmentType.equals("i32")) {
                assignmentType = "int";
            }
            String className = symbolTable.getClassName();
            String superClassName = symbolTable.getSuper();
            if (assignmentType.equals(className) && !childType.equals(className) && (!childType.equals(superClassName))) {
                Report report = new Report(ReportType.ERROR, Stage.SEMANTIC, -1, -1, "Incompatible types " + childType + " and " + assignmentType + " for assignment");
                reports.add(report);
            }
            if (!childType.equals(assignmentType) && (assignmentType.equals("int") || assignmentType.equals("bool") || assignmentType.equals("int[]"))){
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
            JmmNode child = node.getChildren().get(0);
            JmmNode index = node.getChildren().get(1);
            if (!child.get("isArray").equals("true")) {
                Report report = new Report(ReportType.ERROR, Stage.SEMANTIC, -1, -1, "Cannot access array");
                reports.add(report);
            }
            if (!index.get("type").equals("int") && !index.get("type").equals("i32")) {
                Report report = new Report(ReportType.ERROR, Stage.SEMANTIC, -1, -1, "Array index must be of type int");
                reports.add(report);
            }

        }
        for (JmmNode child_ : node.getChildren()) {
            verifyArrayAccess(child_, reports);
        }
    }

    private boolean areTypesCompatible(String leftType, String rightType, String isLeftArray, String isRightArray, String op) {
        boolean b = leftType.equals("int") && rightType.equals("int") || leftType.equals("i32") && rightType.equals("i32");
        if (op.equals("+") || op.equals("-") || op.equals("*") || op.equals("/")) {
            return b && isLeftArray.equals("false") && isRightArray.equals("false");
        }
        if (op.equals("<") || op.equals(">") || op.equals("<=") || op.equals(">=")) {
            return b && isLeftArray.equals("false") && isRightArray.equals("false");
        }
        if (op.equals("==") || op.equals("!=")) {
            return b || (leftType.equals("boolean") && rightType.equals("boolean"));
        }
        if (op.equals("&&") || op.equals("||")) {
            return leftType.equals("boolean") && rightType.equals("boolean") && isLeftArray.equals("false") && isRightArray.equals("false");
        }
        if (op.equals("=")) {
            return b || (leftType.equals("boolean") && rightType.equals("boolean"));
        }
        return false;
    }
}
