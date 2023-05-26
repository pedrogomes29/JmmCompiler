package pt.up.fe.comp2023.symbolTable;

import pt.up.fe.comp.jmm.ast.JmmNode;

public class SemanticAnalysisException extends RuntimeException{
    private int lineStart;
    private int colStart;

    public int getLine(){
        return lineStart;
    }
    public int getCol(){
        return colStart;
    }
    public SemanticAnalysisException(String message, JmmNode node) {
        super(message);
        this.lineStart = Integer.parseInt(node.get("lineStart"));
        this.colStart = Integer.parseInt(node.get("colStart"));
    }
}
