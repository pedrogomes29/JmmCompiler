package pt.up.fe.comp2023.Analysis;

import pt.up.fe.comp.jmm.analysis.JmmAnalysis;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.parser.JmmParserResult;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2023.Analysis.JmmSymbolTable;

public class JmmAnalysisImpl implements JmmAnalysis {
    @Override
    public JmmSemanticsResult semanticAnalysis(JmmParserResult jmmParserResult) {

        JmmNode rootNode = jmmParserResult.getRootNode();
        SymbolTable symbolTable = new JmmSymbolTable(rootNode);
        return new JmmSemanticsResult(jmmParserResult, symbolTable, jmmParserResult.getReports());
    }
}
