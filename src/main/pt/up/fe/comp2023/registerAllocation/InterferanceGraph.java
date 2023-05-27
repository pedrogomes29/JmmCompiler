package pt.up.fe.comp2023.registerAllocation;

import org.specs.comp.ollir.*;

import java.util.*;

public class InterferanceGraph {
    private final HashMap<String,Vertex> contentToNode;

    private void addEdge(String src,String dest){
        Vertex srcNode = contentToNode.get(src);
        Vertex destNode = contentToNode.get(dest);
        assert srcNode!=null && destNode!=null;
        srcNode.addEdge(destNode);
        destNode.addEdge(srcNode);
    }
    public InterferanceGraph(HashMap<Node, HashSet<String>> def, HashMap<Node, HashSet<String>> use, HashMap<Node, HashSet<String>> in,HashMap<Node, HashSet<String>> out){
        contentToNode = new HashMap<>();
        for(Node node:def.keySet()){
            Instruction instruction = (Instruction) node;
            for(String content:def.get(node)){
                if(!contentToNode.containsKey(content))
                    contentToNode.put(content,new Vertex(content));
            }

            HashSet<String> currentIn = in.get(node);
            HashSet<String> currentUseUnionOut = new HashSet<>(use.get(node));
            currentUseUnionOut.addAll(out.get(node));
            for(String varOut:currentUseUnionOut){
                for(String varIn:currentIn) {
                    if (!Objects.equals(varOut, varIn))
                        addEdge(varIn, varOut);
                }
            }
        }



    }

    public HashMap<String,Integer> colorGraph(Method method, int k){
        TreeSet<Integer> availableRegisters = new TreeSet<>();
        int nrRegistersAlreadyTaken = method.isStaticMethod()?0:1;
        for(int i=nrRegistersAlreadyTaken;i<k;i++){
            availableRegisters.add(i);
        }
        k-=nrRegistersAlreadyTaken;

        for(Element var :method.getParams()){
            if(var instanceof Operand operand) {
                availableRegisters.remove(method.getVarTable().get(operand.getName()).getVirtualReg());
                k--;
            }
        }

        if(k<0)
            return null;

        boolean allNodesGTEKEdges = false;
        Stack<String> stack = new Stack<>();
        int visitedNodes = 0;
        while(!allNodesGTEKEdges && visitedNodes<contentToNode.size()){
            allNodesGTEKEdges = true;
            for(String currentVar:contentToNode.keySet()){
                Vertex currentVertex = contentToNode.get(currentVar);
                if(currentVertex.unvisitedNeighbors() < k && !currentVertex.visited){
                    allNodesGTEKEdges = false;
                    stack.push(currentVar);
                    contentToNode.get(currentVar).visited=true;
                    visitedNodes++;
                }
            }
        }
        if(allNodesGTEKEdges)
            return null;
        else{
            HashMap<String,Integer> varToRegister = new HashMap<>();
            while(!stack.isEmpty()){
                String currentVar = stack.pop();
                Vertex currentVertex = contentToNode.get(currentVar);
                TreeSet<Integer> availableRegistersLocal = new TreeSet<>(availableRegisters);
                for(Vertex neighborVertex:currentVertex.edges){
                    String neighborVar = neighborVertex.getContent();
                    Integer neighborRegister = varToRegister.get(neighborVar);
                    if(neighborRegister!=null)
                        availableRegistersLocal.remove(neighborRegister);
                }
                varToRegister.put(currentVar,availableRegistersLocal.first());
            }
            return varToRegister;
        }
    }


    private class Vertex{
        private final String content;
        private final HashSet<Vertex> edges;

        private boolean visited;

        public Vertex(String s){
            content = s;
            edges = new HashSet<>();
            this.visited = false;
        }

        public String getContent(){
            return content;
        }

        public int unvisitedNeighbors(){
            int answer = 0;
            for(Vertex v:edges){
                if(!v.visited)
                    answer++;
            }
            return answer;
        }

        public void addEdge(Vertex v){
            edges.add(v);
        }
    }
}

