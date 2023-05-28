package pt.up.fe.comp2023.constPropagation;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.JmmNodeImpl;

import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;

public class JmmVisitorForConstPropagation extends AJmmVisitor<Boolean,Boolean> {
    private boolean hasOptimized;
    private SymbolTable symbolTable;

    public JmmVisitorForConstPropagation(SymbolTable symbolTable){
        hasOptimized = false;
        this.symbolTable = symbolTable;
        buildVisitor();
    }
    @Override
    protected void buildVisitor() {
        addVisit("NormalMethod", this::dealWithMethod);
        addVisit("StaticMethod", this::dealWithMethod);
        addVisit("Assignment",this::dealWithAssignment);
        addVisit("Identifier",this::dealWithIdentifier);
        addVisit("IfStatement",this::dealWithIfStatement);
        addVisit("WhileStatement",this::dealWithWhileStatement);


        setDefaultVisit(this::dealWithDefaultVisit);
    }

    public Boolean dealWithMethod(JmmNode node, Boolean b){
        HashMap<String,String> in = new HashMap<>();
        String methodName = node.get("functionName");
        for(Symbol field: symbolTable.getFields()){
            in.put(field.getName(),"undefined");
        }
        for(Symbol param: symbolTable.getParameters(methodName)){
            in.put(param.getName(),"undefined");
        }
        for(Symbol localVar: symbolTable.getLocalVariables(methodName)){
            in.put(localVar.getName(),"undefined");
        }

        for(JmmNode child:node.getChildren()){
            child.putObject("in",in); //passed by reference
            visit(child);
        }
        return true;
    }

    public boolean hasOptimized(){
        return hasOptimized;
    }
    public boolean isConstant(JmmNode node){
        return Objects.equals(node.getKind(), "Integer") || Objects.equals(node.getKind(), "Boolean");
    }

    public boolean dealWithAssignment(JmmNode node, Boolean b){
        String var = node.get("varName");
        JmmNode child = node.getJmmChild(0);
        HashMap<String,String> in = (HashMap<String, String>) node.getObject("in");
        boolean changed = false;
        if(isConstant(child)) {
            in.put(var, child.get("value"));
        }
        else{
            child.putObject("in",in);
            Optional<String> changedStr = node.getOptional("changed");
            if(changedStr.isPresent() && changedStr.get().equals("true")){
                child.put("changed","true");
                node.put("changed","false"); //set change to false, if child changes it sets changed to true
                changed = true;
            }
            visit(child,true);
            in.put(var,"not a constant");
        }
        if(changed)
            checkIfInChanged(node,in);
        return true;
    }
    public boolean dealWithDefaultVisit(JmmNode node, Boolean b){
        HashMap<String,String> in = (HashMap<String, String>) node.getOptionalObject("in").orElse(null);
        String changedStr = node.getOptional("changed").orElse(null);
        Boolean changed = Objects.equals(changedStr, "true");
        if(changed)
            node.put("changed","false");//needs to be set to false so it doesn't absorve the children's change
        for(JmmNode child:node.getChildren()){
            if(in!=null)
                child.putObject("in",in); //passed by reference
            if(changedStr!=null)
                child.put("changed",changed?"true":"false");
            visit(child);
        }
        if(changed) {
            checkIfInChanged(node,in);
        }
        return true;
    }

    public void intersect(HashMap<String,String> a,HashMap<String,String> b, HashMap<String,String> result){
        result.clear();
        for(String var: a.keySet()){
            String aConst = a.get(var);
            String bConst = b.get(var);
            if(Objects.equals(aConst, "not a constant") || Objects.equals(bConst, "not a constant")) //not a constant is the bottom element of the lattice
                result.put(var,"not a constant");
            else if (Objects.equals(aConst, "undefined")) //undefined is the top element of the lattice
                result.put(var,bConst);
            else if(Objects.equals(bConst, "undefined")) //undefined is the top element of the lattice
                result.put(var,aConst);
            else if(Objects.equals(aConst, bConst)) //neither const is not a constant or undefined and they are equal
                result.put(var,aConst);
            else //neither const is not a constant or undefined and they are different
                result.put(var,"not a constant");
        }
    }

    public void checkIfInChanged(JmmNode node,HashMap<String,String> in ){
        HashMap<String, String> prevIn = (HashMap<String, String>) node.getOptionalObject("prevIn").orElse(null);
        boolean childrenChanged =  Objects.equals(node.get("changed"), "true");
        boolean nodeChanged = !in.equals(prevIn);
        boolean parentChanged = Objects.equals(node.getJmmParent().get("changed"), "true");      //if any child changes it sets it's parent changed to true
                                                                                                    //and absorves it's siblings' falses
        node.getJmmParent().put("changed",(childrenChanged || parentChanged || nodeChanged)?"true":"false");
        node.putObject("prevIn",new HashMap<>(in));
    }

    public boolean dealWithIfStatement(JmmNode node, Boolean b){
        JmmNode condition = node.getJmmChild(0);
        JmmNode ifCode = node.getJmmChild(1);
        JmmNode elseCode = node.getJmmChild(2);
        boolean changed = false;


        HashMap<String,String> in = (HashMap<String,String>) node.getObject("in");
        HashMap<String,String> inIf = new HashMap<String,String>(in); //copy instead of pass by reference => data-flow of if and else is independent
        HashMap<String,String> inElse = new HashMap<String,String>(in); //copy instead of pass by reference => data-flow of if and else is independent

        Optional<String> changedStr = node.getOptional("changed");

        if(changedStr.isPresent() && changedStr.get().equals("true")){
            condition.put("changed","true");
            ifCode.put("changed","true");
            elseCode.put("changed","true");
            node.put("changed","false");
            changed = true;
        }

        condition.putObject("in",in);
        visit(condition,true);
        ifCode.putObject("in",inIf);
        visit(ifCode,true);
        elseCode.putObject("in",inElse);
        visit(elseCode,true);

        intersect(inIf,inElse,in);

        if(changed)
            checkIfInChanged(node,in);

        return true;
    }

    public boolean dealWithWhileStatement(JmmNode node, Boolean b){
        JmmNode condition = node.getJmmChild(0);
        JmmNode code = node.getJmmChild(1);
        HashMap<String, String> in = (HashMap<String, String>) node.getObject("in");
        HashMap<String, String> prevCodeOut = new HashMap<>(in);
        node.put("changed","true");


        while(Objects.equals(node.get("changed"), "true")) {
            node.put("changed","false");
            HashMap<String, String> whileOut = new HashMap<>(in);
            condition.putObject("in", whileOut);
            condition.put("changed","true");
            visit(condition, true);

            code.putObject("in", whileOut);
            code.put("changed","true");
            visit(code, true);

            intersect(whileOut, prevCodeOut, in);
            HashMap<String, String> prevIn = (HashMap<String, String>) node.getOptionalObject("prevIn").orElse(null);
            boolean changed = !in.equals(prevIn);
            boolean childrenChanged = Objects.equals(node.get("changed"), "true");
            node.getJmmParent().put("changed",(childrenChanged || changed)?"true":"false");
            node.putObject("prevIn",new HashMap<>(in));
        }

        condition.putObject("in", in);
        condition.put("changed","false");
        visit(condition, true);

        code.putObject("in", in);
        code.put("changed","false");
        visit(code, true);

        return true;
    }

    public boolean dealWithIdentifier(JmmNode node,Boolean b){
        Optional<String> changed = node.getOptional("changed");
        if(changed.isEmpty() || changed.get().equals("false")) {
            HashMap<String, String> in = (HashMap<String, String>) node.getOptionalObject("in").orElse(null);
            if (in != null) {
                String constant = in.get(node.get("value"));
                if (constant!=null && !Objects.equals(constant, "undefined") && !Objects.equals(constant, "not a constant")) {
                    hasOptimized = true;
                    Type idType = (Type) node.getObject("type");
                    JmmNode constantNode;
                    if(Objects.equals(idType.getName(), "int")){
                            constantNode = new JmmNodeImpl("Integer");
                    }
                    else if (Objects.equals(idType.getName(), "boolean")){
                            constantNode = new JmmNodeImpl("Boolean");
                    }
                    else{
                        throw new RuntimeException("Const should be int or boolean");
                    }
                    constantNode.put("value", constant);
                    constantNode.putObject("type", idType);
                    node.replace(constantNode);
                }
            }
        }
        return true;
    }
}
