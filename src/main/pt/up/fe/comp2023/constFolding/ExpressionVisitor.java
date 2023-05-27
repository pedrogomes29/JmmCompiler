package pt.up.fe.comp2023.constFolding;

import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.JmmNodeImpl;

public class ExpressionVisitor {
    public void visitBinaryOpExpression(JmmNode node) {
        JmmNode leftChild = node.getChildren().get(0);
        JmmNode rightChild = node.getChildren().get(1);

        // Check if both child nodes are constant expressions
        if (isConstantExpression(leftChild) && isConstantExpression(rightChild)) {
            // Evaluate the binary operation and determine the resulting constant value
            int leftValue = leftChild.getKind().equals("Integer") ? Integer.parseInt(leftChild.get("value")) : leftChild.getKind().equals("True") ? 1 : 0;
            int rightValue = rightChild.getKind().equals("Integer") ? Integer.parseInt(rightChild.get("value")) : rightChild.getKind().equals("True") ? 1 : 0;
            int resultValue = evaluateBinaryOperation(node.get("op"), leftValue, rightValue);

            // Replace the binary operation expression node with a constant expression node
            if (node.get("op").equals("<")) {
                JmmNode constantNode = new JmmNodeImpl("Boolean");
                if (resultValue == 1) {
                    constantNode.put("value", "true");
                }
                else {
                    constantNode.put("value", "false");
                }
                constantNode.put("value", String.valueOf(resultValue));
                constantNode.putObject("type", "boolean");
                node.getJmmParent().replace(constantNode);

            }
            else {
                JmmNode constantNode = new JmmNodeImpl("Integer");
                constantNode.put("value", String.valueOf(resultValue));
                constantNode.putObject("type", "int");
                node.getJmmParent().replace(constantNode);
            }
        }
    }
    private boolean isConstantExpression(JmmNode node) {
        return node.getKind().equals("Integer") || node.getKind().equals("True") || node.getKind().equals("False");
    }
    private int evaluateBinaryOperation(String operator, int leftValue, int rightValue) {
        // Evaluate the binary operation and return the resulting constant value
        return switch (operator) {
            case "+" -> leftValue + rightValue;
            case "-" -> leftValue - rightValue;
            case "*" -> leftValue * rightValue;
            case "/" -> leftValue / rightValue;
            case "<" -> leftValue < rightValue ? 1 : 0;
            default -> 0;
        };
    }
}
