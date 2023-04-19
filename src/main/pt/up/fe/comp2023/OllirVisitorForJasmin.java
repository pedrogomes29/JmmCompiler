package pt.up.fe.comp2023;

import jas.LocalVarTableAttr;
import org.specs.comp.ollir.*;

import javax.print.DocFlavor;
import java.beans.Statement;
import java.lang.reflect.Array;
import java.security.interfaces.ECKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.specs.comp.ollir.CallType.NEW;
import static org.specs.comp.ollir.CallType.invokespecial;
import static org.specs.comp.ollir.ElementType.*;
import static org.specs.comp.ollir.InstructionType.ASSIGN;
import static org.specs.comp.ollir.OperationType.*;

public class OllirVisitorForJasmin{

    private String className;
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
        if (classUnit.getSuperClass() == null){
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
        if (field.getFieldAccessModifier().name().equals("DEFAULT")){
            result.append("private ");
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
            result.append("\taload_0\n");
            result.append("\tinvokespecial ");
            if (method.getOllirClass().getSuperClass() != null){
                result.append(method.getOllirClass().getSuperClass()).append("/<init>()V\n");
            } else {
                result.append("java/lang/Object/<init>()V\n");
            }
            result.append("\treturn\n");
            result.append(".end method");
        }  else {
            result.append(".method").append(" ").append(method.getMethodAccessModifier().toString().toLowerCase()).append(" ");
            if (method.isStaticMethod()) {
                result.append("static ");
            }
            result.append(method.getMethodName()).append("(");
            for (Element arg : method.getParams()) {
                if (arg.getType().getTypeOfElement().name().equals("INT32")) {
                    result.append("I");
                } else if (arg.getType().getTypeOfElement().name().equals("BOOLEAN")) {
                    result.append("Z");
                } else if (arg.getType().getTypeOfElement().name().equals("ARRAYREF")) {
                    result.append("[Ljava/lang/String;");
                } else if (arg.getType().getTypeOfElement().name().equals("OBJECTREF")) {
                    result.append("Ljava/lang/").append(((ClassType) arg.getType()).getName()).append(";");
                }
            }

            result.append(")");
            if (method.getReturnType().getTypeOfElement().name().equals("BOOLEAN")) {
                result.append("Z").append("\n");
            } else if (method.getReturnType().getTypeOfElement().name().equals("INT32")) {
                result.append("I").append("\n");
            } else if (method.getReturnType().getTypeOfElement().name().equals("VOID")) {
                result.append("V").append("\n");
            } else if (method.getReturnType().getTypeOfElement().name().equals("OBJECTREF")) {
                result.append("L").append(((ClassType) method.getReturnType()).getName()).append(";\n");
            }

            HashMap <String, Descriptor> varTable = method.getVarTable();
            result.append("\t.limit stack 99\n").append("\t.limit locals 99\n");

            for (Instruction instruction : method.getInstructions()) {
                if (instruction instanceof AssignInstruction) {
                    result.append(visitAssignmentStatement((AssignInstruction) instruction, varTable));
                } else if (instruction instanceof CallInstruction) {
                    result.append(visitCallInstruction((CallInstruction) instruction, varTable));
                } else if (instruction instanceof ReturnInstruction) {
                    result.append(visitReturnStatement((ReturnInstruction) instruction, varTable));
                } else if (instruction instanceof PutFieldInstruction) {
                    result.append(visitPutFieldInstruction((PutFieldInstruction) instruction, varTable));
                }
                if (instruction.getInstType() == InstructionType.CALL && ((CallInstruction) instruction).getReturnType().getTypeOfElement() != ElementType.VOID) {

                    result.append("\tpop\n");
                }
            }
            if (method.getReturnType().getTypeOfElement().equals(VOID)) {
                result.append("\treturn\n");
            } else if (method.getReturnType().getTypeOfElement().equals(OBJECTREF)){
                result.append("\tareturn\n");
            } else {
                result.append("\tireturn\n");
            }
            result.append(".end method");
        }

        return result;
    }

    public StringBuilder visitAssignmentStatement(AssignInstruction assign, HashMap <String, Descriptor> localVariable) {
        StringBuilder result = new StringBuilder();
        Instruction rhs = assign.getRhs();
        if (rhs instanceof SingleOpInstruction && ((SingleOpInstruction) rhs).getSingleOperand().isLiteral()) {
            Integer value = Integer.parseInt(((LiteralElement) ((SingleOpInstruction) rhs).getSingleOperand()).getLiteral());
            jasmincodeForIntegerVariable(result, value);
        } else if (rhs instanceof SingleOpInstruction){
            ElementType type = ((SingleOpInstruction) rhs).getSingleOperand().getType().getTypeOfElement();
            String variableName = ((Operand)((SingleOpInstruction) rhs).getSingleOperand()).getName();
            if (type == THIS){
                result.append("\taload_0\n");
            } else if (type == OBJECTREF){
                result.append(legalizeInstruction("\taload",localVariable.get(variableName).getVirtualReg())).append("\n");
            } else {
                result.append(legalizeInstruction("\tiload",localVariable.get(variableName).getVirtualReg())).append("\n");
            }
        } else if (rhs instanceof BinaryOpInstruction){
            result.append(visitBinaryOpInstruction((BinaryOpInstruction) rhs, localVariable));
        } else if (rhs instanceof CallInstruction){
            result.append(visitCallInstruction((CallInstruction) rhs,localVariable));
        } else if (rhs instanceof  GetFieldInstruction) {
            result.append(visitGetFieldInstruction((GetFieldInstruction) rhs, localVariable));
        }

        Operand destOperand = (Operand) assign.getDest();
        InstructionType ins =  assign.getInstType();
        if (localVariable.containsKey(destOperand.getName()) && !ins.equals(ASSIGN)) {
            result.append(legalizeInstruction("\tiload",localVariable.get(destOperand.getName()).getVirtualReg())).append("\n");
        } else {
            String localVariableName = destOperand.getName();
            int localVariableIdx;
            localVariableIdx = localVariable.get(localVariableName).getVirtualReg();
            if (rhs instanceof CallInstruction) {
                ElementType e = ((CallInstruction) rhs).getReturnType().getTypeOfElement();
            }
            if (rhs instanceof  SingleOpInstruction){
                if (((SingleOpInstruction) rhs).getSingleOperand().getType().getTypeOfElement().equals(OBJECTREF)){
                    result.append(legalizeInstruction("\tastore",localVariableIdx)).append("\n");
                } else {
                    result.append(legalizeInstruction("\tistore",localVariableIdx)).append("\n");
                }
            }
            else if ((! (rhs instanceof CallInstruction)) || ((!((CallInstruction) rhs).getReturnType().getTypeOfElement().equals(OBJECTREF)) && (!((CallInstruction) rhs).getReturnType().getTypeOfElement().equals(CLASS)) && (!((CallInstruction) rhs).getReturnType().getTypeOfElement().equals(THIS)))) {
                result.append(legalizeInstruction("\tistore",localVariableIdx)).append("\n");
            }else {
                result.append(legalizeInstruction("\tastore",localVariableIdx)).append("\n");
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
            result.append("\n");
        } else if (opType.equals(SUB)) {
            result.append("\tisub");
            result.append("\n");
        } else if (opType.equals(MUL)) {
            result.append("\timul");
            result.append("\n");
        } else if (opType.equals(DIV)) {
            result.append("\tidiv");
            result.append("\n");
        }
        return result;
    }

    public StringBuilder visitCallInstruction(CallInstruction callInstruction,HashMap <String, Descriptor> localVariableIndices){
        StringBuilder result = new StringBuilder();
        Operand firstArg = (Operand) callInstruction.getFirstArg();
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
            if (classType.getName() == "Integer"){
                result.append("java/lang/Integer");
            }else {

                result.append(classType.getName());
            }
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
            if (e.getType().getTypeOfElement().equals(VOID)){
                result.append("V");
            } else if (e.getType().getTypeOfElement().equals(INT32)){
                result.append("I");
            } else if (e.getType().getTypeOfElement().equals(BOOLEAN)){
                result.append("Z");
            } else if (e.getType().getTypeOfElement().equals(OBJECTREF)){
                result.append("Ljava/lang/").append(((ClassType) e.getType()).getName()).append(";");
            }
        }

        if (! invocationType.equals(NEW)) {
            result.append(")");
        }
        if (callInstruction.getReturnType().getTypeOfElement().equals(VOID)){
            result.append("V");
        } else if (callInstruction.getReturnType().getTypeOfElement().equals(INT32)){
            result.append("I");
        } else if (callInstruction.getReturnType().getTypeOfElement().equals(BOOLEAN)){
            result.append("Z");
        } else if (callInstruction.getReturnType().getTypeOfElement().equals(CLASS)){
            result.append("Ljava/lang/").append(((ClassType) callInstruction.getReturnType()).getName()).append(";");
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
            if (returnInstructionOperand.getType().getTypeOfElement().equals(OBJECTREF)){
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
                if (op.getType().getTypeOfElement().equals(OBJECTREF)) {
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
            Operand op = (Operand) operand;
            if (op.getType().getTypeOfElement().equals(OBJECTREF)){
                result.append(legalizeInstruction("\taload",localVariable.get(op.getName()).getVirtualReg())).append("\n");
            } else if (firstOperand.getType().getTypeOfElement().equals(THIS)){
                result.append("\taload_0\n");
            } else {
                result.append(legalizeInstruction("\tiload",localVariable.get(op.getName()).getVirtualReg())).append("\n");
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
