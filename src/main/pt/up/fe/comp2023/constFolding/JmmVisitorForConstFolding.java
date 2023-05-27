package pt.up.fe.comp2023.constFolding;

import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PostorderJmmVisitor;


public class JmmVisitorForConstFolding extends PostorderJmmVisitor<Void, Void> {
    ExpressionVisitor expressionVisitor = new ExpressionVisitor();
    private boolean optimized = false;
    @Override
    protected void buildVisitor() {
        this.addVisit("BinaryOp", this::visitBinaryOp);
        this.setDefaultVisit(this::defaultVisit);

    }

    public boolean hasOptimized(){
        return optimized;
    }

    private Void defaultVisit(JmmNode jmmNode, Void unused) {
        visitAllChildren(jmmNode, unused);
        return null;
    }

    private Void visitBinaryOp(JmmNode jmmNode, Void unused) {
        if(expressionVisitor.visitBinaryOpExpression(jmmNode))
            optimized = true;
        return null;
    }
}
