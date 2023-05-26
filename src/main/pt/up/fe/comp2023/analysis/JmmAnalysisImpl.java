package pt.up.fe.comp2023.analysis;

import pt.up.fe.comp.jmm.analysis.JmmAnalysis;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.parser.JmmParserResult;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2023.symbolTable.JmmSymbolTable;
import pt.up.fe.comp2023.symbolTable.SemanticAnalysisException;

import java.util.ArrayList;
import java.util.List;

public class JmmAnalysisImpl implements JmmAnalysis {
    @Override
    public JmmSemanticsResult semanticAnalysis(JmmParserResult jmmParserResult) {

        JmmNode rootNode = jmmParserResult.getRootNode();
        JmmSymbolTable symbolTable;
        try {
            symbolTable = new JmmSymbolTable(rootNode);
        } catch (SemanticAnalysisException e) {
            Report report = new Report(ReportType.ERROR, Stage.SEMANTIC, e.getLine(), e.getCol(), e.getMessage());
            List<Report> reports = new ArrayList<>();
            reports.add(report);
            return new JmmSemanticsResult(jmmParserResult, null, reports);
        }
        System.out.println(rootNode.toTree());
        // Type verification
        JmmVisitorForAnalysis gen = new JmmVisitorForAnalysis(symbolTable);
        gen.visit(rootNode);
        List<Report> reports = gen.getReports();

        return new JmmSemanticsResult(jmmParserResult, symbolTable, reports);
    }
}


