package pt.up.fe.comp2023.jasmin;

import jas.LocalVarTableAttr;
import org.antlr.runtime.tree.RewriteEmptyStreamException;
import org.specs.comp.ollir.*;

import javax.lang.model.element.TypeElement;
import javax.print.DocFlavor;
import javax.swing.*;
import java.beans.Statement;
import java.lang.reflect.Array;
import java.security.interfaces.ECKey;
import java.util.*;

import static org.specs.comp.ollir.CallType.*;
import static org.specs.comp.ollir.ElementType.*;
import static org.specs.comp.ollir.InstructionType.*;
import static org.specs.comp.ollir.OperationType.*;

public class OllirVisitorForJasmin {

    private String className;

    public int conditionNumber = 0;


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
        this.className = classUnit.getClassName();
        result.append(".super ");
        if (classUnit.getSuperClass() == null) {
            result.append("java/lang/Object\n");
        } else {
            result.append(classUnit.getSuperClass()).append("\n");
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
        if (field.getFieldAccessModifier().name().equals("DEFAULT")) {
            result.append("private ");
        } else {
            result.append(field.getFieldAccessModifier().toString().toLowerCase()).append(" ");
        }
        result.append(field.getFieldName()).append(" ");
        ElementType elementType = field.getFieldType().getTypeOfElement();
        if (elementType.equals(ARRAYREF)) {
            result.append("[");
            elementType = ((ArrayType) field.getFieldType()).getArrayType();
        }

        if (elementType.name().equals("INT32")) {
            result.append("I");
        } else if (elementType.name().equals("BOOLEAN")) {
            result.append("Z");
        } else if (elementType.name().equals("OBJECTREF")) {
            result.append("L").append(((ClassType) field.getFieldType()).getName()).append(";");
        } else if (elementType.name().equals("VOID")) {
            result.append("V");
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

    public static int calculateLimitLocals(Method method) {
        HashMap<String, Descriptor> varTable = method.getVarTable();
        int lastRegister = -1;
        if (!method.isStaticMethod())
            lastRegister = 0;
        for (Descriptor descriptor : varTable.values()) {
            lastRegister = Math.max(lastRegister, descriptor.getVirtualReg());
        }

        return lastRegister + 1;
    }

    public StringBuilder visitMethod(Method method) {
        StringBuilder result = new StringBuilder();
        if (method.isConstructMethod()) {
            result.append(".method public <init>()V\n");
            result.append("\t.limit stack 1\n").append("\t.limit locals 1").append("\n");
            result.append("\taload_0\n");
            result.append("\tinvokespecial ");
            if (method.getOllirClass().getSuperClass() != null) {
                result.append(method.getOllirClass().getSuperClass()).append("/<init>()V\n");
            } else {
                result.append("java/lang/Object/<init>()V\n");
            }
            result.append("\treturn\n");
            result.append(".end method");
        } else {
            result.append(".method").append(" ").append(method.getMethodAccessModifier().toString().toLowerCase()).append(" ");
            if (method.isStaticMethod()) {
                result.append("static ");
            }
            result.append(method.getMethodName()).append("(");
            for (Element arg : method.getParams()) {
                String elementTypeName = arg.getType().getTypeOfElement().name();
                if (elementTypeName.equals("ARRAYREF")) {
                    result.append("[");
                    elementTypeName = ((ArrayType) arg.getType()).getArrayType().name();
                }
                if (elementTypeName.equals("INT32")) {
                    result.append("I");
                } else if (elementTypeName.equals("BOOLEAN")) {
                    result.append("Z");
                } else if (elementTypeName.equals("STRING")) {
                    result.append("Ljava/lang/String;");
                } else if (elementTypeName.equals("OBJECTREF")) {
                    result.append("L").append(((ClassType) arg.getType()).getName()).append(";");
                } else if (elementTypeName.equals("VOID")) {
                    result.append("V");
                }
            }

            result.append(")");
            ElementType returnTypeOfElement = method.getReturnType().getTypeOfElement();
            if (returnTypeOfElement.equals(ARRAYREF)) {
                result.append("[");
                returnTypeOfElement = ((ArrayType) method.getReturnType()).getArrayType();
            }
            if (returnTypeOfElement.name().equals("BOOLEAN")) {
                result.append("Z").append("\n");
            } else if (returnTypeOfElement.name().equals("INT32")) {
                result.append("I").append("\n");
            } else if (returnTypeOfElement.name().equals("VOID")) {
                result.append("V").append("\n");
            } else if (returnTypeOfElement.name().equals("OBJECTREF")) {
                result.append("L").append(((ClassType) method.getReturnType()).getName()).append(";\n");
            }

            HashMap<String, Descriptor> varTable = method.getVarTable();

            int nrRegisters = calculateLimitLocals(method);
            result.append("\t.limit stack 99\n").append("\t.limit locals ").append(nrRegisters).append("\n");

            for (Instruction instruction : method.getInstructions()) {
                for (Map.Entry<String, Instruction> label : method.getLabels().entrySet()) {
                    if (label.getValue().equals(instruction)) {
                        result.append(label.getKey()).append(":\n");
                    }
                }
                result.append(getInstruction(instruction, varTable));
            }
            if (method.getReturnType().getTypeOfElement().equals(VOID)) {
                result.append("\treturn\n");
            } else if (method.getReturnType().getTypeOfElement().equals(OBJECTREF) || method.getReturnType().getTypeOfElement().equals(ARRAYREF)) {
                result.append("\tareturn\n");
            } else {
                result.append("\tireturn\n");
            }
            result.append(".end method");
        }

        return result;
    }

    private StringBuilder getInstruction(Instruction instruction, HashMap<String, Descriptor> varTable) {
        StringBuilder result = new StringBuilder();
        if (instruction instanceof AssignInstruction) {
            result.append(visitAssignmentStatement((AssignInstruction) instruction, varTable));
        } else if (instruction instanceof CallInstruction) {
            result.append(visitCallInstruction((CallInstruction) instruction, varTable));
        } else if (instruction instanceof ReturnInstruction) {
            result.append(visitReturnStatement((ReturnInstruction) instruction, varTable));
        } else if (instruction instanceof PutFieldInstruction) {
            result.append(visitPutFieldInstruction((PutFieldInstruction) instruction, varTable));
        } else if (instruction instanceof GotoInstruction) {
            result.append(visitGotoInstruction((GotoInstruction) instruction, varTable));
        } else if (instruction instanceof CondBranchInstruction) {
            result.append(visitCondBranchInstruction((CondBranchInstruction) instruction, varTable));
        } else if (instruction.getInstType().equals(NOPER)){
            getLoadInstruction(result, ((SingleOpInstruction) instruction).getSingleOperand(), varTable);
        } else if (instruction.getInstType().equals(BINARYOPER)) {
            result.append(visitBinaryOpInstruction((BinaryOpInstruction) instruction,varTable));
        } else if (instruction.getInstType().equals(UNARYOPER)){
            result.append(visitUnaryOpInstruction((UnaryOpInstruction) instruction, varTable));
        }

        if (instruction.getInstType() == InstructionType.CALL && ((CallInstruction) instruction).getReturnType().getTypeOfElement() != ElementType.VOID) {

            result.append("\tpop\n");
        }
        return result;
    }

    public StringBuilder visitAssignmentStatement(AssignInstruction assign, HashMap<String, Descriptor> localVariable) {
        StringBuilder result = new StringBuilder();
        Instruction rhs = assign.getRhs();
        if (rhs instanceof SingleOpInstruction && ((SingleOpInstruction) rhs).getSingleOperand().isLiteral()) {
            Integer value = Integer.parseInt(((LiteralElement) ((SingleOpInstruction) rhs).getSingleOperand()).getLiteral());
            jasmincodeForIntegerVariable(result, value);
        } else if (rhs instanceof SingleOpInstruction) {
            ElementType type = ((SingleOpInstruction) rhs).getSingleOperand().getType().getTypeOfElement();
            String variableName = ((Operand) ((SingleOpInstruction) rhs).getSingleOperand()).getName();
            if (type == THIS) {
                result.append("\taload_0\n");
            } else if (type == OBJECTREF || type == ARRAYREF) {
                result.append(legalizeInstruction("\taload", localVariable.get(variableName).getVirtualReg())).append("\n");
            } else if (((SingleOpInstruction) rhs).getSingleOperand() instanceof ArrayOperand) {
                result.append(legalizeInstruction("\taload", localVariable.get(variableName).getVirtualReg())).append("\n");
                for (Element element : ((ArrayOperand) ((SingleOpInstruction) rhs).getSingleOperand()).getIndexOperands()) {
                    if (element.isLiteral()) {
                        jasmincodeForIntegerVariable(result, Integer.parseInt(((LiteralElement) element).getLiteral()));
                    } else {
                        getLoadInstruction(result, element, localVariable);
                    }
                }
                result.append("\tiaload\n");
            } else {
                result.append(legalizeInstruction("\tiload", localVariable.get(variableName).getVirtualReg())).append("\n");
            }
        } else if (rhs instanceof BinaryOpInstruction) {
            System.out.println(((BinaryOpInstruction) rhs).getLeftOperand());
            System.out.println(assign.getDest());
            if(assign.getDest().toString().equals(((BinaryOpInstruction) rhs).getLeftOperand().toString()) && ((BinaryOpInstruction) rhs).getRightOperand().isLiteral()) {
                Operand destOperand = (Operand) assign.getDest();
                if (((BinaryOpInstruction) rhs).getOperation().getOpType() == ADD) {
                    result.append("\tiinc " + localVariable.get(destOperand.getName()).getVirtualReg() + " " + ((LiteralElement) ((BinaryOpInstruction) rhs).getRightOperand()).getLiteral() + "\n");
                }
                if (((BinaryOpInstruction) rhs).getOperation().getOpType() == SUB) {
                    result.append("\tiinc " + localVariable.get(destOperand.getName()).getVirtualReg() + " -" + ((LiteralElement) ((BinaryOpInstruction) rhs).getRightOperand()).getLiteral() + "\n");
                }
                return result;
            }
            else if(assign.getDest().toString().equals(((BinaryOpInstruction) rhs).getRightOperand().toString()) && ((BinaryOpInstruction) rhs).getLeftOperand().isLiteral()) {
                Operand destOperand = (Operand) assign.getDest();
                if (((BinaryOpInstruction) rhs).getOperation().getOpType() == ADD) {
                    result.append("\tiinc " + localVariable.get(destOperand.getName()).getVirtualReg() + " " + ((LiteralElement) ((BinaryOpInstruction) rhs).getLeftOperand()).getLiteral() + "\n");
                }
                if (((BinaryOpInstruction) rhs).getOperation().getOpType() == SUB) {
                    result.append("\tiinc " + localVariable.get(destOperand.getName()).getVirtualReg() + " -" + ((LiteralElement) ((BinaryOpInstruction) rhs).getLeftOperand()).getLiteral() + "\n");
                }
                return result;
            }
            else {
                result.append(visitBinaryOpInstruction((BinaryOpInstruction) rhs, localVariable));
            }
        } else if (rhs instanceof CallInstruction) {
            result.append(visitCallInstruction((CallInstruction) rhs, localVariable));
        } else if (rhs instanceof GetFieldInstruction) {
            result.append(visitGetFieldInstruction((GetFieldInstruction) rhs, localVariable));
        } else if (rhs instanceof UnaryOpInstruction) {
            result.append(visitUnaryOpInstruction((UnaryOpInstruction) rhs,localVariable));
        }
        Operand destOperand = (Operand) assign.getDest();
        InstructionType ins = assign.getInstType();
        if (localVariable.containsKey(destOperand.getName()) && !ins.equals(ASSIGN)) {
            result.append(legalizeInstruction("\tiload", localVariable.get(destOperand.getName()).getVirtualReg())).append("\n");
        } else {
            String localVariableName = destOperand.getName();
            int localVariableIdx;
            localVariableIdx = localVariable.get(localVariableName).getVirtualReg();
            if (rhs instanceof CallInstruction) {
                ElementType e = ((CallInstruction) rhs).getReturnType().getTypeOfElement();
            }
            if (rhs instanceof SingleOpInstruction) {
                if (destOperand instanceof ArrayOperand) {
                    result.append("");
                } else if (((SingleOpInstruction) rhs).getSingleOperand().getType().getTypeOfElement().equals(OBJECTREF) || ((SingleOpInstruction) rhs).getSingleOperand().getType().getTypeOfElement().equals(ARRAYREF)) {
                    result.append(legalizeInstruction("\tastore", localVariableIdx)).append("\n");
                } else {
                    result.append(legalizeInstruction("\tistore", localVariableIdx)).append("\n");
                }
            } else if ((!(rhs instanceof CallInstruction)) || ((!((CallInstruction) rhs).getReturnType().getTypeOfElement().equals(OBJECTREF)) && (!((CallInstruction) rhs).getReturnType().getTypeOfElement().equals(CLASS)) && (!((CallInstruction) rhs).getReturnType().getTypeOfElement().equals(THIS)) && (!((CallInstruction) rhs).getReturnType().getTypeOfElement().equals(ARRAYREF)))) {
                result.append(legalizeInstruction("\tistore", localVariableIdx)).append("\n");
            } else {
                result.append(legalizeInstruction("\tastore", localVariableIdx)).append("\n");
            }
            if (destOperand instanceof ArrayOperand) {
                getLoadInstruction(result, destOperand, localVariable);
                for (Element element : ((ArrayOperand) destOperand).getIndexOperands()) {
                    if (element.isLiteral()) {
                        jasmincodeForIntegerVariable(result, Integer.parseInt(((LiteralElement) element).getLiteral()));
                    } else {
                        getLoadInstruction(result, element, localVariable);
                    }
                }
                if (rhs instanceof SingleOpInstruction && ((SingleOpInstruction) rhs).getSingleOperand().isLiteral()) {
                    Integer value = Integer.parseInt(((LiteralElement) ((SingleOpInstruction) rhs).getSingleOperand()).getLiteral());
                    jasmincodeForIntegerVariable(result, value);
                } else if (rhs instanceof SingleOpInstruction) {
                    ElementType type = ((SingleOpInstruction) rhs).getSingleOperand().getType().getTypeOfElement();
                    String variableName = ((Operand) ((SingleOpInstruction) rhs).getSingleOperand()).getName();
                    if (type == THIS) {
                        result.append("\taload_0\n");
                    } else if (type == OBJECTREF || type == ARRAYREF) {
                        result.append(legalizeInstruction("\taload", localVariable.get(variableName).getVirtualReg())).append("\n");
                    } else {
                        result.append(legalizeInstruction("\tiload", localVariable.get(variableName).getVirtualReg())).append("\n");
                    }
                }
                result.append("\tiastore\n");
            }
        }


        return result;
    }



    public StringBuilder visitBinaryOpInstruction(BinaryOpInstruction operation, HashMap <String, Descriptor> localVariableIndices){
        StringBuilder result = new StringBuilder();
        Element leftElement = operation.getLeftOperand();
        Element rightElement = operation.getRightOperand();
        if (leftElement.isLiteral()){
            jasmincodeForIntegerVariable(result, Integer.valueOf(((LiteralElement) leftElement).getLiteral()));
        } else {
            Operand leftOperand = (Operand) operation.getLeftOperand();
            result.append(visitOperand(leftOperand, localVariableIndices));
        }

        if (rightElement.isLiteral()) {
            jasmincodeForIntegerVariable(result, Integer.valueOf(((LiteralElement) rightElement).getLiteral()));
        } else {
            Operand rightOperand = (Operand) operation.getRightOperand();
            result.append(visitOperand(rightOperand, localVariableIndices));
        }

        OperationType opType = operation.getOperation().getOpType();
        if (opType.equals(ADD)){
            result.append("\tiadd");
        } else if (opType.equals(SUB)) {
            result.append("\tisub");
        } else if (opType.equals(MUL)) {
            result.append("\timul");
        } else if (opType.equals(DIV)) {
            result.append("\tidiv");
        } else if (opType.equals(ANDB)) {
            result.append("\tiand");
        } else if (opType.equals(LTH)) {
            result.append("\tif_icmplt");
        } else if (opType.equals(NOTB)) {
            result.append("\tifeq");
        }
        boolean isIfOperation = false;
        if (opType.equals(EQ) || opType.equals(LTH) || opType.equals(GTH) || opType.equals(NEQ)){
            isIfOperation = true;
        }
        if (isIfOperation){
            result.append(" TRUE").append(this.conditionNumber).append("\n").append(
                    "\ticonst_0\n \tgoto NEXT").append(this.conditionNumber).append("\n TRUE").append(
                    this.conditionNumber).append(":\n\ticonst_1\n NEXT").append(this.conditionNumber++).append(":");
        }
        result.append("\n");

        return result;
    }

    public StringBuilder visitUnaryOpInstruction(UnaryOpInstruction instruction,HashMap<String, Descriptor> varTable){
        StringBuilder result = new StringBuilder();
        getLoadInstruction(result, instruction.getOperand(),varTable);
        OperationType opType = instruction.getOperation().getOpType();
        if (opType.equals(ADD)){
            result.append("\tiadd");
        } else if (opType.equals(SUB)) {
            result.append("\tisub");
        } else if (opType.equals(MUL)) {
            result.append("\timul");
        } else if (opType.equals(DIV)) {
            result.append("\tidiv");
        } else if (opType.equals(ANDB)) {
            result.append("\tiand");
        } else if (opType.equals(LTH)) {
            result.append("\tif_icmplt");
        } else if (opType.equals(NOTB)) {
            result.append("\tifeq");
        }
        boolean isIfOperation = false;
        if (opType.equals(NOTB)){
            isIfOperation = true;
        }
        if (isIfOperation){
            result.append(" TRUE").append(this.conditionNumber).append("\n").append(
                    "\ticonst_0\n \tgoto NEXT").append(this.conditionNumber).append("\n TRUE").append(
                    this.conditionNumber).append(":\n\ticonst_1\n NEXT").append(this.conditionNumber++).append(":");
        }
        result.append("\n");
        return result;
    }


    public StringBuilder visitCallInstruction(CallInstruction callInstruction,HashMap <String, Descriptor> localVariableIndices){
        StringBuilder result = new StringBuilder();
        Operand firstArg = (Operand) callInstruction.getFirstArg();
        if (firstArg.getType().getTypeOfElement().equals(ARRAYREF)){
            return result.append(visitCallArray(callInstruction, localVariableIndices));
        }
        ClassType classType = (ClassType) firstArg.getType();
        CallType invocationType = callInstruction.getInvocationType();
        switch (invocationType){
            case invokespecial -> result.append(visitInvokeSpecial(callInstruction,localVariableIndices));
            case invokestatic -> result.append(visitInvokeStatic(callInstruction, localVariableIndices));
            case invokevirtual -> result.append(visitInvokeVirtual(callInstruction, localVariableIndices));
            case NEW -> result.append(visitNew(callInstruction,localVariableIndices));
        }
        result.append("\t").append(callInstruction.getInvocationType().toString().toLowerCase()).append(" ");
        if (firstArg.getName().equals("this")){
            result.append(this.className);
        } else if (localVariableIndices.containsKey(firstArg.getName())) {
            result.append(classType.getName());
        } else {
            result.append(firstArg.getName());
        }

        if (callInstruction.getSecondArg() != null) {
            result.append("/").append(((LiteralElement) callInstruction.getSecondArg()).getLiteral().toString().replaceAll("\"",""));
        }

        if (! invocationType.equals(NEW)) {
            result.append("(");
        }
        ArrayList<Element> operands = callInstruction.getListOfOperands();
        for (Element e : operands) {
            ElementType elementType = e.getType().getTypeOfElement();
            if (elementType.equals(ARRAYREF)){
                result.append("[");
                elementType = ((ArrayType) e.getType()).getArrayType();
            }
            if (elementType.equals(VOID)){
                result.append("V");
            } else if (elementType.equals(INT32)){
                result.append("I");
            } else if (elementType.equals(BOOLEAN)){
                result.append("Z");
            } else if (elementType.equals(OBJECTREF)){
                result.append("L").append(((ClassType) e.getType()).getName()).append(";");
            }
        }

        if (! invocationType.equals(NEW)) {
            result.append(")");
        }
        ElementType elementType = callInstruction.getReturnType().getTypeOfElement();
        if (elementType.equals(ARRAYREF)){
            result.append("[");
            elementType = ((ArrayType) callInstruction.getReturnType()).getArrayType();
        }
        if (callInstruction.getReturnType().getTypeOfElement().equals(VOID)){
            result.append("V");
        } else if (callInstruction.getReturnType().getTypeOfElement().equals(INT32)){
            result.append("I");
        } else if (callInstruction.getReturnType().getTypeOfElement().equals(BOOLEAN)){
            result.append("Z");
        } else if (callInstruction.getReturnType().getTypeOfElement().equals(CLASS)){
            result.append("L").append(((ClassType) callInstruction.getReturnType()).getName()).append(";");
        }
        result.append("\n");

        if (invocationType.equals(NEW)){
            result.append("\tdup\n");
        }


        return result;
    }



    public StringBuilder visitReturnStatement(ReturnInstruction returnIntruction,HashMap <String, Descriptor> localVariable){
        StringBuilder result = new StringBuilder();
        if (returnIntruction.getOperand() == null) return result;
        if (returnIntruction.getOperand().isLiteral()){
            Integer value = Integer.parseInt(((LiteralElement) returnIntruction.getOperand()).getLiteral());
            jasmincodeForIntegerVariable(result, value);
        } else {
            Operand returnInstructionOperand = ((Operand)returnIntruction.getOperand());
            String localVariableName = returnInstructionOperand.getName();
            int localVariableIdx;
            if (returnInstructionOperand.getType().getTypeOfElement().equals(OBJECTREF) || returnInstructionOperand.getType().getTypeOfElement().equals(ARRAYREF)){
                result.append(legalizeInstruction("\taload",localVariable.get(returnInstructionOperand.getName()).getVirtualReg())).append("\n");
            } else if (returnInstructionOperand.getType().getTypeOfElement().equals(THIS)){
                result.append("\taload_0\n");
            } else {
                localVariableIdx = localVariable.get(localVariableName).getVirtualReg();
                result.append(legalizeInstruction("\tiload",localVariableIdx)).append("\n");
            }
        }
        return result;
    }

    private void jasmincodeForIntegerVariable(StringBuilder result, Integer value) {
        if (value >= -1 && value <= 5 )
            result.append("\ticonst_").append(value).append("\n");
        else if (value >= -128 && value <= 127)
            result.append("\tbipush ").append(value).append("\n");
        else if (value >= -32768 && value <= 32767)
            result.append("\tsipush ").append(value).append("\n");
        else
            result.append("\tldc ").append(value).append("\n");
    }

    public StringBuilder visitOperand(Operand operand, HashMap <String, Descriptor> localVariable){
        StringBuilder result = new StringBuilder();
        if (operand.isParameter()){
            result.append(legalizeInstruction("\tiload",operand.getParamId())).append("\n");
        }else {
            String localVariableName = operand.getName();
            int localVariableIdx;
            localVariableIdx = localVariable.get(localVariableName).getVirtualReg();
            result.append(legalizeInstruction("\tiload",localVariableIdx)).append("\n");
        }

        return result;
    }

    public StringBuilder visitInvokeSpecial(CallInstruction callInstruction,HashMap <String, Descriptor> localVariable){
        StringBuilder result = new StringBuilder();
        String name = ((Operand)callInstruction.getFirstArg()).getName();
        result.append(legalizeInstruction("\taload",localVariable.get(name).getVirtualReg())).append("\n");
        return result;
    }
    public StringBuilder visitInvokeVirtual(CallInstruction callInstruction,HashMap <String, Descriptor> localVariable){
        StringBuilder result = new StringBuilder();
        Element firstOperand = callInstruction.getFirstArg();
        if (firstOperand.getType().getTypeOfElement().equals(THIS)){
            result.append("\taload_0\n");
        } else {
            result.append(legalizeInstruction("\taload",localVariable.get(((Operand)callInstruction.getFirstArg()).getName()).getVirtualReg())).append("\n");
        }
        for (Element operand: callInstruction.getListOfOperands()){
            if (operand.isLiteral()){
                jasmincodeForIntegerVariable(result, Integer.valueOf(((LiteralElement) operand).getLiteral()));
            } else {
                Operand op = (Operand) operand;
                if (op.getType().getTypeOfElement().equals(OBJECTREF) || op.getType().getTypeOfElement().equals(STRING) || op.getType().getTypeOfElement().equals(ARRAYREF)) {
                    result.append(legalizeInstruction("\taload", localVariable.get(op.getName()).getVirtualReg())).append("\n");
                } else if (firstOperand.getType().getTypeOfElement().equals(THIS)) {
                    result.append("\taload_0\n");
                } else {
                    result.append(legalizeInstruction("\tiload", localVariable.get(op.getName()).getVirtualReg())).append("\n");
                }
            }
        }
        return result;
    }
    public StringBuilder visitInvokeStatic(CallInstruction callInstruction,HashMap <String, Descriptor> localVariable){
        StringBuilder result = new StringBuilder();
        Element firstOperand = callInstruction.getFirstArg();
        for (Element operand: callInstruction.getListOfOperands()){
            if (operand.isLiteral()){
                jasmincodeForIntegerVariable(result,Integer.parseInt(((LiteralElement) operand).getLiteral()));
            } else {
                Operand op = (Operand) operand;
                if (op.getType().getTypeOfElement().equals(OBJECTREF) || op.getType().getTypeOfElement().equals(STRING) || op.getType().getTypeOfElement().equals(ARRAYREF)) {
                    result.append(legalizeInstruction("\taload", localVariable.get(op.getName()).getVirtualReg())).append("\n");
                } else if (firstOperand.getType().getTypeOfElement().equals(THIS)) {
                    result.append("\taload_0\n");
                } else {
                    result.append(legalizeInstruction("\tiload", localVariable.get(op.getName()).getVirtualReg())).append("\n");
                }
            }
        }
        return result;
    }

    public StringBuilder visitNew(CallInstruction callInstruction, HashMap<String, Descriptor> localVariable){
        StringBuilder result = new StringBuilder();
        for (Element element : callInstruction.getListOfOperands()){
            String name = ((Operand)element).getName();
            result.append(legalizeInstruction("\taload",localVariable.get(name).getVirtualReg())).append("\n");

        }

        return result;
    }

    public StringBuilder visitPutFieldInstruction(PutFieldInstruction putFieldInstruction, HashMap <String, Descriptor> localVariables){
        StringBuilder result = new StringBuilder();
        Integer value = Integer.parseInt(((LiteralElement) putFieldInstruction.getThirdOperand()).getLiteral());
        Element firstOperand =putFieldInstruction.getFirstOperand();
        Element secondOperand = putFieldInstruction.getSecondOperand();
        if (firstOperand.getType().getTypeOfElement().equals(THIS)){
            result.append("\taload_0\n");
            jasmincodeForIntegerVariable(result,value);
            result.append("\tputfield ").append(this.className).append("/").append(((Operand) putFieldInstruction.getSecondOperand()).getName());
        } else {
            result.append(legalizeInstruction("\taload",localVariables.get(((Operand)putFieldInstruction.getFirstOperand()).getName()).getVirtualReg()));
            jasmincodeForIntegerVariable(result,value);
            result.append("\tputfield ").append(((Operand) putFieldInstruction.getFirstOperand())).append("/").append(((Operand) putFieldInstruction.getSecondOperand()).getName());
        }
        if (secondOperand.getType().getTypeOfElement().equals(INT32)) {
            result.append(" I");
        }   else if (secondOperand.getType().getTypeOfElement().equals(BOOLEAN)){
            result.append(" Z");
        }
        result.append("\n");
        return result;
    }

    public StringBuilder visitGetFieldInstruction(GetFieldInstruction getFieldInstruction, HashMap <String, Descriptor> localVariable){
        StringBuilder result = new StringBuilder();
        Element firstOperand = getFieldInstruction.getFirstOperand();
        Element secondOperand = getFieldInstruction.getSecondOperand();
        if (firstOperand.getType().getTypeOfElement().equals(THIS)){
            result.append("\taload_0\n");
            result.append("\tgetfield ").append(this.className).append("/").append(((Operand) getFieldInstruction.getSecondOperand()).getName());
        } else {
            result.append(legalizeInstruction("\taload", localVariable.get(((Operand)getFieldInstruction.getFirstOperand()).getName()).getVirtualReg()));
            result.append("\tgetfield ").append(((Operand) getFieldInstruction.getFirstOperand())).append("/").append(((Operand) getFieldInstruction.getSecondOperand()).getName());
        }
        if (secondOperand.getType().getTypeOfElement().equals(INT32)) {
            result.append(" I");
        }   else if (secondOperand.getType().getTypeOfElement().equals(BOOLEAN)){
            result.append(" Z");
        }
        result.append("\n");
        return result;
    }

    public StringBuilder visitGotoInstruction(GotoInstruction gotoInstruction, HashMap <String, Descriptor> localVariable){
        StringBuilder result = new StringBuilder();
        result.append("\tgoto ").append(gotoInstruction.getLabel()).append("\n");
        return result;
    }

    public StringBuilder visitCondBranchInstruction(CondBranchInstruction condBranchInstruction, HashMap<String, Descriptor> localVariable){
        StringBuilder result = new StringBuilder();
        Instruction instruction;
        if (condBranchInstruction instanceof SingleOpCondInstruction) {
            SingleOpCondInstruction singleOpCondInstruction = (SingleOpCondInstruction) condBranchInstruction;
            instruction = singleOpCondInstruction.getCondition();
        } else if (condBranchInstruction instanceof OpCondInstruction) {
            OpCondInstruction opCondInstruction = (OpCondInstruction) condBranchInstruction;
            instruction = opCondInstruction.getCondition();
        } else {
            return result;
        }

        InstructionType type = instruction.getInstType();
        if (type.equals(BINARYOPER)){
            BinaryOpInstruction binaryOpInstruction = (BinaryOpInstruction) instruction;
            OperationType operationType = binaryOpInstruction.getOperation().getOpType();
            if (operationType.equals(ANDB)){
                result.append(getInstruction(instruction, localVariable));
                result.append("\tifne ").append(condBranchInstruction.getLabel()).append("\n");
            } else if (operationType.equals(LTH)) {
                Element lhs = binaryOpInstruction.getLeftOperand();
                Element rhs = binaryOpInstruction.getRightOperand();
                boolean leftIsLiteral = false;
                boolean rightIsLiteral = false;

                if (lhs instanceof  LiteralElement) {
                    leftIsLiteral = true;
                } else if (rhs instanceof  LiteralElement) {
                    rightIsLiteral = true;
                }
                if (leftIsLiteral && ((LiteralElement) lhs).getLiteral().equals("0")) {
                    jasmincodeForIntegerVariable(result,Integer.parseInt(((LiteralElement) lhs).getLiteral()));
                    getLoadInstruction(result,rhs, localVariable);
                    result.append("\tifgt ").append(condBranchInstruction.getLabel()).append("\n");
                } else if (rightIsLiteral && ((LiteralElement) rhs).getLiteral().equals("0")){
                    jasmincodeForIntegerVariable(result,Integer.parseInt(((LiteralElement) rhs).getLiteral()));
                    getLoadInstruction(result,lhs,localVariable);
                    result.append("\tiflt ").append(condBranchInstruction.getLabel()).append("\n");
                } else {
                    getLoadInstruction(result,lhs,localVariable);
                    getLoadInstruction(result,rhs,localVariable);
                    result.append("\tif_icmplt ").append(condBranchInstruction.getLabel()).append("\n");
                }
            } else if (operationType.equals(GTE)){
                Element lhs = binaryOpInstruction.getLeftOperand();
                Element rhs = binaryOpInstruction.getRightOperand();
                boolean leftIsLiteral = false;
                boolean rightIsLiteral = false;

                if (lhs instanceof  LiteralElement) {
                    leftIsLiteral = true;
                } else if (rhs instanceof  LiteralElement) {
                    rightIsLiteral = true;
                }
                if (leftIsLiteral) {
                    jasmincodeForIntegerVariable(result,Integer.parseInt(((LiteralElement) lhs).getLiteral()));
                    getLoadInstruction(result,rhs, localVariable);
                    result.append("\tiflt ").append(condBranchInstruction.getLabel()).append("\n");
                } else if  (rightIsLiteral){
                    jasmincodeForIntegerVariable(result,Integer.parseInt(((LiteralElement) lhs).getLiteral()));
                    getLoadInstruction(result,lhs,localVariable);
                    result.append("\tifgt ").append(condBranchInstruction.getLabel()).append("\n");
                } else {
                    getLoadInstruction(result,lhs,localVariable);
                    getLoadInstruction(result,rhs,localVariable);
                    result.append("\tif_icmpgt ").append(condBranchInstruction.getLabel()).append("\n");
                }
            }

        } else if (type.equals(UNARYOPER)) {
            UnaryOpInstruction unaryOpInstruction = (UnaryOpInstruction) instruction;
            if (unaryOpInstruction.getOperation().getOpType().equals(NOTB)) {
                getLoadInstruction(result,unaryOpInstruction.getOperand(),localVariable);
                result.append("\tifeq ").append(condBranchInstruction.getLabel()).append("\n");
            }
        } else {
            result.append(getInstruction(instruction,localVariable));
            result.append("\tifne ").append(condBranchInstruction.getLabel()).append("\n");
        }
        return result;
    }


    public void getLoadInstruction(StringBuilder result, Element element, HashMap<String, Descriptor> varTable){
        if (element.isLiteral()){
            jasmincodeForIntegerVariable(result, Integer.parseInt(((LiteralElement) element).getLiteral()));
            return;
        }
        Operand op = (Operand) element;
        ElementType type = op.getType().getTypeOfElement();
        if (type.equals(OBJECTREF) || type.equals(STRING)){
            result.append(legalizeInstruction("\taload",varTable.get(op.getName()).getVirtualReg())).append("\n");
        } else if(element instanceof ArrayOperand) {
            result.append(legalizeInstruction("\taload",varTable.get(op.getName()).getVirtualReg())).append("\n");
        } else if (element.getType().getTypeOfElement().equals(ARRAYREF)){
            result.append(legalizeInstruction("\taload",varTable.get(op.getName()).getVirtualReg())).append("\n");
        } else if (type.equals(THIS)){
            result.append("\taload0").append("\n");
        } else {
            result.append(legalizeInstruction("\tiload",varTable.get(op.getName()).getVirtualReg())).append("\n");
        }
    }

    public StringBuilder visitCallArray(CallInstruction callInstruction, HashMap<String, Descriptor> localVariables){
        StringBuilder result = new StringBuilder();
        if (callInstruction.getInvocationType().equals(arraylength)){
            getLoadInstruction(result, callInstruction.getFirstArg(), localVariables);
            result.append("\tarraylength\n");
        } else {
            for (Element element : callInstruction.getListOfOperands()) {
                getLoadInstruction(result, element, localVariables);
            }

            result.append("\tnewarray int\n");
        }


        return result;
    }

    public StringBuilder legalizeInstruction(String instruction, Integer number){
        StringBuilder result = new StringBuilder();
        if (number <= 3){
            result.append(instruction).append("_").append(number);
        } else {
            result.append(instruction).append(" ").append(number);
        }
        return result;
    }
}

