package pt.up.fe.comp2023.constPropagation;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.JmmNodeImpl;

import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;

public class JmmVisitorForConstPropagation extends AJmmVisitor<Boolean,Boolean> {
    private HashMap<JmmNode,HashMap<String,String>> methodNodeToIn;

    public JmmVisitorForConstPropagation(){
        this.methodNodeToIn = new HashMap<>();
        buildVisitor();
    }
    @Override
    protected void buildVisitor() {
        addVisit("NormalMethod", this::dealWithMethod);
        addVisit("StaticMethod", this::dealWithMethod);
        addVisit("Assignment",this::dealWithAssignment);
        addVisit("Identifier",this::dealWithIdentifier);
        setDefaultVisit(this::dealWithDefaultVisit);
    }

    public Boolean dealWithMethod(JmmNode node, Boolean b){
        methodNodeToIn.put(node,new HashMap<>());
        return visitAllChildren(node,true);
    }


    public JmmNode getMethodNode (JmmNode descendent){
        Optional<JmmNode> methodOptional = descendent.getAncestor("NormalMethod");
        if(methodOptional.isEmpty())
            methodOptional = descendent.getAncestor("StaticMethod");

        return methodOptional.get();
    }

    public boolean isConstant(JmmNode node){
        return Objects.equals(node.getKind(), "Integer") || Objects.equals(node.getKind(), "Boolean") || Objects.equals(node.getKind(), "This");
    }

    public boolean dealWithAssignment(JmmNode node, Boolean b){
        String var = node.get("id");
        JmmNode child = node.getJmmChild(0);
        if(isConstant(child))
            methodNodeToIn.get(getMethodNode(node)).put(var,child.get("value"));
        else{
            methodNodeToIn.get(getMethodNode(node)).remove(var);
            visit(child,true);
        }

        return true;
    }
    public boolean dealWithDefaultVisit(JmmNode node, Boolean b){
        return visitAllChildren(node,true);
    }

    public boolean dealWithIdentifier(JmmNode node,Boolean b){
        String constant = methodNodeToIn.get(getMethodNode(node)).get(node.get("value"));
        if(constant!=null) {
            Type idType = (Type) node.getObject("type");
            JmmNode constantNode;
            switch(idType.getName()){
                case "int"->{
                    constantNode = new JmmNodeImpl("Integer");
                }
                case "boolean"->{
                    constantNode = new JmmNodeImpl("Boolean");
                }
                default->{
                    constantNode = new JmmNodeImpl("This");
                }
            }
            constantNode.put("value", constant);
            constantNode.putObject("type",idType);
            node.replace(constantNode);
        }
        return true;
    }
}
