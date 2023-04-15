package pt.up.fe.comp2023;

import jas.LocalVarTableAttr;
import org.specs.comp.ollir.*;

import javax.print.DocFlavor;
import java.beans.Statement;
import java.lang.reflect.Array;
import java.util.HashMap;
import java.util.List;

import static org.specs.comp.ollir.ElementType.*;
import static org.specs.comp.ollir.OperationType.*;

public class OllirVisitorForJasmin{

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
        result.append(".super ");
        if (classUnit.getSuperClass() == null){
            result.append("java/lang/Object\n\n");
        } else {
            result.append(classUnit.getSuperClass()).append("\n\n");
        }
        return result;
    }

    public StringBuilder visitClassFields(ClassUnit classUnit) {
        StringBuilder result = new StringBuilder();
        for (Field field : classUnit.getFields()) {
            result.append(visitField(field)).append("\n");
        }
        result.append("\n");
        return result;
    }

    public StringBuilder visitField(Field field) {
        StringBuilder result = new StringBuilder();
        result.append(".field ");
        if (field.getFieldAccessModifier().name().equals("DEFAULT")){
            result.append("private");
        }else{
            result.append(field.getFieldAccessModifier().toString().toLowerCase()).append(" ");
        }
        result.append(field.getFieldName());
        if (field.getFieldType().getTypeOfElement().name().equals("INT32")) {
            result.append(" I");
        }   else if (field.getFieldType().getTypeOfElement().name().equals("BOOLEAN")){
            result.append(" Z");
        }

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
            result.append("    invokespecial ");
            if (method.getOllirClass().getSuperClass() != null){
                result.append(method.getOllirClass().getSuperClass()).append("/<init>()V\n");
            } else {
                result.append("java/lang/Object/<init>()V\n");
            }
            result.append("    return\n");
            result.append(".end method\n");
        }  else {
            result.append(".method").append(" ").append(method.getMethodAccessModifier().toString().toLowerCase()).append(" ");
            if (method.isStaticMethod()){
                result.append("static ");
            }
            result.append(method.getMethodName()).append("(");

            for (Element arg : method.getParams()) {
                if (arg.getType().getTypeOfElement().name().equals("INT32")) {
                    result.append("I");
                } else if (arg.getType().getTypeOfElement().name().equals("BOOLEAN")){
                    result.append("Z");
                } else if (arg.getType().getTypeOfElement().name().equals("ARRAYREF")){
                    result.append("[Ljava/lang/String;");
                }
            }

            result.append(")");
            if (method.getReturnType().getTypeOfElement().name().equals("BOOLEAN")) {
                result.append("Z").append("\n");
            } else if (method.getReturnType().getTypeOfElement().name().equals("INT32")) {
                result.append("I").append("\n");
            } else if (method.getReturnType().getTypeOfElement().name().equals("VOID")) {
                result.append("V").append("\n");
            }


            result.append("    .limit stack 99\n").append("    .limit locals 99\n");
            HashMap<String,Integer> localVariableIndices = new HashMap<String,Integer>();
            for (Instruction instruction : method.getInstructions()) {
                if (instruction instanceof AssignInstruction) {
                    result.append(visitAssignmentStatement((AssignInstruction) instruction, localVariableIndices));
                } else if (instruction instanceof BinaryOpInstruction) {
                    result.append(visitBinaryOpInstruction((BinaryOpInstruction) instruction));
                } else if (instruction instanceof CallInstruction) {
                    result.append(visitCallInstruction((CallInstruction) instruction));
                } else if (instruction instanceof ReturnInstruction){
                    result.append(visitReturnStatement((ReturnInstruction) instruction));
                }
            }
            if (method.getReturnType().getTypeOfElement().equals(VOID)){
                result.append("\treturn\n");
            } else {
                result.append("\tireturn\n");
            }
            result.append(".end method\n");
        }

        return result;
    }

    public StringBuilder visitAssignmentStatement(AssignInstruction assign, HashMap<String,Integer> localVariableIndices) {
        StringBuilder result = new StringBuilder();
        Instruction rhs = assign.getRhs();
        if (rhs instanceof SingleOpInstruction) {
            Integer value = Integer.parseInt(((LiteralElement) (Element) ((SingleOpInstruction) rhs).getSingleOperand()).getLiteral());
            if (value == 0) {
                result.append("\ticonst_0\n");
            } else if (value == 1) {
                result.append("\ticonst_1\n");
            } else if (value == -1) {
                result.append("\ticonst_m1\n");
            } else if (value >= -128 && value <= 127) {
                result.append("\tbipush ").append(value).append("\n");
            } else if (value >= -32768 && value <= 32767) {
                result.append("\tsipush ").append(value).append("\n");
            } else {
                result.append("\tldc ").append(value).append("\n");
            }
        }

        Operand destOperand = (Operand) assign.getDest();
        if (destOperand.isParameter()) {
            result.append("\tiload_").append(destOperand.getParamId()).append("\n");
        } else {
            String localVariableName = destOperand.getName();
            int localVariableIdx;
            if (!localVariableIndices.containsKey(localVariableName)){
                localVariableIdx = localVariableIndices.size() + 1;
                localVariableIndices.put(localVariableName, localVariableIdx);
                result.append("\tistore_").append(localVariableIdx).append("\n");
            } else {
                localVariableIdx = localVariableIndices.get(localVariableName);
                result.append("\tistore_").append(localVariableIdx).append("\n");
            }
        }

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

    public StringBuilder visitCallInstruction(CallInstruction callInstruction){
        StringBuilder result = new StringBuilder();
        Operand firstArg = (Operand) callInstruction.getFirstArg();
        ClassType classType = (ClassType) firstArg.getType();
        result.append("\t").append(callInstruction.getInvocationType()).append(" ").append(classType.getName());
        if (callInstruction.getSecondArg() != null) {
            result.append("/").append(((LiteralElement) callInstruction.getSecondArg()).getLiteral().toString().replaceAll("\"",""));
        }
        result.append("(");

        for (Element e : callInstruction.getListOfOperands()) {
            if (e.getType().getTypeOfElement().equals(VOID)){
                result.append("V");
            } else if (e.getType().getTypeOfElement().equals(INT32)){
                result.append("I");
            } else if (e.getType().getTypeOfElement().equals(BOOLEAN)){
                result.append("Z");
            }
        }

        result.append(")");
        if (callInstruction.getReturnType().getTypeOfElement().equals(VOID)){
            result.append("V");
        } else if (callInstruction.getReturnType().getTypeOfElement().equals(INT32)){
            result.append("I");
        } else if (callInstruction.getReturnType().getTypeOfElement().equals(BOOLEAN)){
            result.append("Z");
        }
        result.append("\n");

        return result;
    }

    public StringBuilder visitReturnStatement(ReturnInstruction returnIntruction){
        StringBuilder result = new StringBuilder();
        if (returnIntruction.getOperand() == null) return result;
        if (returnIntruction.getOperand().isLiteral()){
            Integer value = Integer.parseInt(((LiteralElement) returnIntruction.getOperand()).getLiteral());
            if (value == 0) {
                result.append("\ticonst_0\n");
            } else if (value == 1) {
                result.append("\ticonst_1\n");
            } else if (value == -1) {
                result.append("\ticonst_m1\n");
            } else if (value >= -128 && value <= 127) {
                result.append("\tbipush ").append(value).append("\n");
            } else if (value >= -32768 && value <= 32767) {
                result.append("\tsipush ").append(value).append("\n");
            } else {
                result.append("\tldc ").append(value).append("\n");
            }
        } else if (((Operand)returnIntruction.getOperand()).isParameter()) {
            result.append("\tiload_").append(((Operand)returnIntruction.getOperand()).getParamId()).append("\n");
        }
        return result;
    }
}