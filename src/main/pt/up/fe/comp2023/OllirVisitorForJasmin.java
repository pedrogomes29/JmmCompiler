package pt.up.fe.comp2023;

import org.specs.comp.ollir.*;

import java.beans.Statement;
import java.util.List;

import static org.specs.comp.ollir.ElementType.*;
import static org.specs.comp.ollir.OperationType.*;

public class OllirVisitorForJasmin {

    public String visit(ClassUnit classUnit) {
        StringBuilder result = new StringBuilder();

        result.append(visitClassHeader(classUnit));
        result.append(visitClassFields(classUnit));
        result.append(visitMethods(classUnit));
        return result.toString();
    }

    public StringBuilder visitClassHeader(ClassUnit classUnit) {
        StringBuilder result = new StringBuilder();
        result.append(".class public ").append(classUnit.getClassName()).append("\n");
        result.append(".super java/lang/Object");
        return result;
    }

    public StringBuilder visitClassFields(ClassUnit classUnit) {
        StringBuilder result = new StringBuilder();
        for (Field field : classUnit.getFields()) {
            result.append(visitField(field)).append("\n");
        }
        return result;
    }

    public StringBuilder visitField(Field field) {
        StringBuilder result = new StringBuilder();
        result.append(".field").append(field.getFieldAccessModifier()).append(" ").append(field.getFieldName()).append(" ").append(field.getFieldType()).append("\n");
        return result;
    }

    public StringBuilder visitMethods(ClassUnit classUnit) {
        StringBuilder result = new StringBuilder();
        for (Method method : classUnit.getMethods()) {
            result.append(visitMethod(method)).append("\n");
        }
        return result;
    }

    public StringBuilder visitMethod(Method method) {
        StringBuilder result = new StringBuilder();
        if (method.isConstructMethod()) {
            result.append(".method public <init>()V\n");
            result.append("    aload_0\n");
            result.append("    invokespecial java/lang/Object/<init>()V\n");
            result.append("    return\n");
            result.append(".end method\n");
        } else {
            result.append(".method").append(method.getMethodAccessModifier()).append(" ").append(method.getMethodName()).append("(");

            for (Element arg : method.getParams()) {
                result.append(arg.getType());
            }

            result.append(")").append(method.getReturnType()).append("\n");



            result.append(".limit stack 99\n").append(".limit locals 99\n");

            for (Instruction instruction : method.getInstructions()) {
                if (instruction instanceof AssignInstruction) {
                    result.append(visitAssignmentStatement((AssignInstruction) instruction));
                } else if (instruction instanceof BinaryOpInstruction) {
                    result.append(visitBinaryOpInstruction((BinaryOpInstruction) instruction));
                } /*else if (instruction instanceof CallInstruction) {
                    result.append(visitCallInstruction((CallInstruction) instruction));
                }*/
            }
        }

        result.append(".end method\n");

        return result;
    }

    public StringBuilder visitAssignmentStatement(AssignInstruction assign) {
        StringBuilder result = new StringBuilder();
        Operand destOperand = (Operand) assign.getDest();
        result.append("    ");

        if (assign.getTypeOfAssign().equals(INT32)) {
            result.append("istore");
        } else if (assign.getTypeOfAssign().equals(BOOLEAN)) {
            result.append("istore");
        } else if (assign.getTypeOfAssign().equals(CLASS)) {
            result.append("astore");
        }

        result.append(" ").append(destOperand.getName()).append("\n");

        return result;
    }

    public StringBuilder visitBinaryOpInstruction(BinaryOpInstruction operation){
        StringBuilder result = new StringBuilder();
        Operand leftOperand = (Operand) operation.getLeftOperand();
        Operand rightOperand = (Operand) operation.getRightOperand();
        OperationType opType = operation.getOperation().getOpType();
        if (opType.equals(ADD)){
            result.append("iadd");
            result.append("\n");
        } else if (opType.equals(SUB)) {
            result.append("isub");
            result.append("\n");
        } else if (opType.equals(MUL)) {
            result.append("imul");
            result.append("\n");
        } else if (opType.equals(DIV)) {
            result.append("idiv");
            result.append("\n");
        }
        return result;
    }

}