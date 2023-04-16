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
        verifyTypes(rootNode, reports);
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
        return false;
    }
    }
