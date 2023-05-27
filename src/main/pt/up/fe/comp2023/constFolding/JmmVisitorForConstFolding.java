package pt.up.fe.comp2023.constFolding;

import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PostorderJmmVisitor;


public class JmmVisitorForConstFolding extends PostorderJmmVisitor<Void, Void> {
    ExpressionVisitor expressionVisitor = new ExpressionVisitor();
    @Override
    protected void buildVisitor() {
        this.addVisit("BinaryOp", this::visitBinaryOp);
    }

    private Void visitBinaryOp(JmmNode jmmNode, Void unused) {
        expressionVisitor.visitBinaryOpExpression(jmmNode);
        return null;
    }
}
