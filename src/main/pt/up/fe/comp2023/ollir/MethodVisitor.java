package pt.up.fe.comp2023.ollir;

import org.specs.comp.ollir.*;

import java.util.*;

import static org.specs.comp.ollir.NodeType.INSTRUCTION;

public class MethodVisitor {
    private final Method method;
    private final Queue<Node> queue;
    private final HashMap<Node, HashSet<String>> def;
    private final HashMap<Node, HashSet<String>> use;
    private final HashMap<Node, HashSet<String>> in;
    private final HashMap<Node, HashSet<String>> out;

    private HashMap<String,Integer> varToRegister;

    private InterferanceGraph interferanceGraph;
    private boolean notEnoughRegisters;

    private int maxNrRegisters;

    public MethodVisitor(Method method,int maxNrRegisters){
        this.method = method;
        this.def = new HashMap<>();
        this.use = new HashMap<>();
        this.in = new HashMap<>();
        this.out = new HashMap<>();
        this.queue = new LinkedList<>();
        this.maxNrRegisters = maxNrRegisters;
    }

    public void visit(){
        Node rootNode = method.getBeginNode();
        queue.offer(rootNode);
        while(!queue.isEmpty()){
            Node currentNode = queue.poll();
            for(Node successor: currentNode.getSuccessors())
                queue.offer(successor);
            if(currentNode.getNodeType()==INSTRUCTION){
                Instruction currentInstruction = (Instruction) currentNode;
                def.put(currentInstruction,new HashSet<>());
                use.put(currentInstruction,new HashSet<>());
                in.put(currentInstruction,new HashSet<>());
                out.put(currentInstruction,new HashSet<>());


                switch(currentInstruction.getInstType()){
                    case CALL -> {
                        dealWithCall((CallInstruction) currentInstruction);
                    }
                    case ASSIGN -> {
                        dealWithAssign((AssignInstruction) currentInstruction);
                    }
                    case BRANCH -> {
                        dealWithBranch((OpCondInstruction)currentInstruction);
                    }
                    case RETURN -> {
                        dealWithReturn((ReturnInstruction)currentInstruction);
                    }
                    case GETFIELD -> {
                        dealWithGetField((GetFieldInstruction) currentInstruction);
                    }
                    case PUTFIELD -> {
                        dealWithPutField((PutFieldInstruction) currentInstruction);
                    }
                    case UNARYOPER -> {
                        dealWithUnaryOp((UnaryOpInstruction) currentInstruction);
                    }
                    case BINARYOPER -> {
                        dealWithBinaryOp((BinaryOpInstruction) currentInstruction);
                    }
                    case NOPER -> {
                        dealWithSingleOp((SingleOpInstruction)currentInstruction);
                    }
                    case GOTO -> {
                        //do nothing, no variables
                    }
                }
            }
        }
        liveInLiveOut();
        interferanceGraph = new InterferanceGraph(def,use,in,out);
        if(maxNrRegisters>0) {
            varToRegister = interferanceGraph.colorGraph(maxNrRegisters);
        }
        else{
            int currentMax = 0;
            while(varToRegister == null)
                varToRegister = interferanceGraph.colorGraph(++currentMax);
        }
        if(varToRegister!=null) {
            replaceVarsByRegisters();
            notEnoughRegisters = false;
        }
        else
            notEnoughRegisters = true;
    }




    public void replaceVarsByRegisters(){
        Node rootNode = method.getBeginNode();
        queue.offer(rootNode);
        while(!queue.isEmpty()){
            Node currentNode = queue.poll();
            for(Node successor: currentNode.getSuccessors())
                queue.offer(successor);
            if(currentNode.getNodeType()==INSTRUCTION){
                Instruction currentInstruction = (Instruction) currentNode;
                def.put(currentInstruction,new HashSet<>());
                use.put(currentInstruction,new HashSet<>());
                in.put(currentInstruction,new HashSet<>());
                out.put(currentInstruction,new HashSet<>());


                switch(currentInstruction.getInstType()){
                    case CALL -> {
                        replaceVarsByRegistersCall((CallInstruction) currentInstruction);
                    }
                    case ASSIGN -> {
                        replaceVarsByRegistersAssign((AssignInstruction) currentInstruction);
                    }

                    case RETURN -> {
                        replaceVarsByRegistersReturn((ReturnInstruction)currentInstruction);
                    }
                    case GETFIELD -> {
                        replaceVarsByRegistersGetField((GetFieldInstruction) currentInstruction);
                    }
                    case PUTFIELD -> {
                        replaceVarsByRegistersPutField((PutFieldInstruction) currentInstruction);
                    }
                    case UNARYOPER -> {
                        replaceVarsByRegistersUnaryOp((UnaryOpInstruction) currentInstruction);
                    }
                    case BINARYOPER -> {
                        replaceVarsByRegistersBinaryOp((BinaryOpInstruction) currentInstruction);
                    }
                    case NOPER -> {
                        replaceVarsByRegistersSingleOp((SingleOpInstruction)currentInstruction);
                    }
                    case GOTO -> {
                        //do nothing, no variables
                    }
                    case BRANCH -> {
                        //do nothing, no variables
                    }
                }
            }
            if(currentNode instanceof AssignInstruction instruction)
                queue.offer(instruction.getRhs());
            else if(currentNode instanceof OpCondInstruction instruction)
                queue.offer(instruction.getCondition());

            for(Node successor: currentNode.getSuccessors()) {
                queue.offer(successor);
            }
        }

    }


    private void replaceVarsByRegistersElement(Element element){
        if(element instanceof Operand operand){
            Integer register = varToRegister.get(operand.getName());
            if(register!=null)
                operand.setName("r_"+ register);

            if(operand instanceof ArrayOperand arrayOperand){
                for (Element indexElement : arrayOperand.getIndexOperands()) {
                    if(indexElement instanceof Operand indexOperand){
                        register = varToRegister.get(indexOperand.getName());
                        if(register!=null)
                            indexOperand.setName("r_"+ register);
                    }
                }
            }
        }

    }

    private void replaceVarsByRegistersCall(CallInstruction instruction){
        Element firstArg = instruction.getFirstArg();
        Element secondArg = instruction.getSecondArg();

        replaceVarsByRegistersElement(firstArg);

        if(secondArg!=null)
            replaceVarsByRegistersElement(secondArg);


        for(Element element: instruction.getListOfOperands())
            replaceVarsByRegistersElement(element);
    }
    private void replaceVarsByRegistersAssign(AssignInstruction instruction){
        Element dest = instruction.getDest();
        replaceVarsByRegistersElement(dest);
    }
    private void replaceVarsByRegistersReturn(ReturnInstruction instruction){
        Element operand = instruction.getOperand();
        if(operand!=null)
            replaceVarsByRegistersElement(operand);
    }
    private void replaceVarsByRegistersGetField(GetFieldInstruction instruction){
        Element firstElement = instruction.getFirstOperand();
        replaceVarsByRegistersElement(firstElement);
    }

    private void replaceVarsByRegistersPutField(PutFieldInstruction instruction){
        Element firstElement = instruction.getFirstOperand();
        Element thirdElement = instruction.getThirdOperand();

        replaceVarsByRegistersElement(firstElement);
        replaceVarsByRegistersElement(thirdElement);
    }
    private void replaceVarsByRegistersUnaryOp(UnaryOpInstruction instruction){
        Element element = instruction.getOperand();
        replaceVarsByRegistersElement(element);
    }
    private void replaceVarsByRegistersBinaryOp(BinaryOpInstruction instruction){
        Element leftOperand = instruction.getLeftOperand();
        Element rightOperand = instruction.getRightOperand();

        replaceVarsByRegistersElement(leftOperand);
        replaceVarsByRegistersElement(rightOperand);
    }
    private void replaceVarsByRegistersSingleOp(SingleOpInstruction instruction){
        Element element = instruction.getSingleOperand();
        replaceVarsByRegistersElement(element);
    }

    private void liveInLiveOut(){
        Node rootNode = method.getBeginNode();
        boolean changed = true;
        while(changed){
            changed = false;
            queue.offer(rootNode);
            while(!queue.isEmpty()){
                Node currentNode = queue.poll();
                if(currentNode.getNodeType()==INSTRUCTION){
                    Instruction currentInstruction = (Instruction) currentNode;
                    HashSet<String> currentOut =  new HashSet<>(out.get(currentInstruction));
                    currentOut.removeAll(def.get(currentInstruction));
                    HashSet<String> newIn = new HashSet<>(use.get(currentInstruction));
                    newIn.addAll(currentOut);
                    if(in.get(currentInstruction).size()!=newIn.size())
                        changed=true;
                    in.put(currentInstruction,newIn);
                    HashSet<String> newOut = new HashSet<>();
                    for(Node successor: currentNode.getSuccessors()) {
                        if(successor.getNodeType()==INSTRUCTION)
                            newOut.addAll(in.get(successor));
                    }

                    if(out.get(currentNode).size()!=newOut.size()) {
                        changed = true;
                    }
                    out.put(currentNode,newOut);

                }

                if(currentNode instanceof AssignInstruction instruction)
                    queue.offer(instruction.getRhs());
                else if(currentNode instanceof OpCondInstruction instruction)
                    queue.offer(instruction.getCondition());

                for(Node successor: currentNode.getSuccessors()) {
                    queue.offer(successor);
                }


            }
        }
    }



    private void dealWithAssign(AssignInstruction instruction){
        Element element = instruction.getDest();
        Operand operand = (Operand) element;
        if(!operand.isParameter())
            def.get(instruction).add(operand.getName());

        if(element instanceof ArrayOperand arrayOperand){
            for(Element indexElement:arrayOperand.getIndexOperands())
                dealWithElement(instruction,indexElement);
        }

        queue.offer(instruction.getRhs());
    }

    private void dealWithUnaryOp(UnaryOpInstruction instruction){
        Element element = instruction.getOperand();
        dealWithElement(instruction,element);

    }
    private void dealWithBinaryOp(BinaryOpInstruction instruction){
        Element leftElement = instruction.getLeftOperand();
        Element rightElement = instruction.getRightOperand();
        dealWithElement(instruction,leftElement);
        dealWithElement(instruction,rightElement);
    }

    private void dealWithBranch(OpCondInstruction instruction){
        queue.offer(instruction.getCondition());
    }


    private boolean isNotArrayThisOrLiteral(Element element){
        if(element.isLiteral()){
            return false;
        }
        else{
            Operand operand = (Operand) element;
            Type type = operand.getType();
            if(type instanceof ClassType classType){
                if(classType.getTypeOfElement()==ElementType.OBJECTREF && Objects.equals(operand.getName(), classType.getName()))
                    return false;

            }
            return !Objects.equals(operand.getName(), "this") && !Objects.equals(operand.getName(), "array");
        }
    }
    private void dealWithCall(CallInstruction instruction){
        Element firstArg = instruction.getFirstArg();
        Element secondArg = instruction.getSecondArg();

        dealWithElement(instruction,firstArg);

        if(secondArg!=null)
            dealWithElement(instruction,secondArg);


        for(Element element: instruction.getListOfOperands())
            dealWithElement(instruction,element);
    }

    private void dealWithElement(Instruction instruction,Element element){
        if(isNotArrayThisOrLiteral(element)){
            Operand operand = (Operand) element;
            if(!operand.isParameter())
                use.get(instruction).add(operand.getName());
            if(element instanceof ArrayOperand arrayOperand){
                for (Element indexElement : arrayOperand.getIndexOperands()) {
                    if (isNotArrayThisOrLiteral(indexElement)) {
                        Operand indexOperand = (Operand) indexElement;
                        if(!indexOperand.isParameter())
                            use.get(instruction).add(indexOperand.getName());
                    }
                }
            }
        }
    }

    private void dealWithSingleOp(SingleOpInstruction instruction){
        Element element = instruction.getSingleOperand();
        dealWithElement(instruction,element);
    }

    private void dealWithReturn(ReturnInstruction instruction){
        Element element = instruction.getOperand();
        if(element!=null)
            dealWithElement(instruction,element);
    }

    private void dealWithGetField(GetFieldInstruction instruction){
        Element firstElement = instruction.getFirstOperand();
        dealWithElement(instruction,firstElement);
    }

    private void dealWithPutField(PutFieldInstruction instruction){
        Element firstElement = instruction.getFirstOperand();
        Element thirdElement = instruction.getThirdOperand();

        dealWithElement(instruction,firstElement);
        dealWithElement(instruction,thirdElement);
    }

    public boolean insufficientRegisters(){
        return notEnoughRegisters;
    }
}
