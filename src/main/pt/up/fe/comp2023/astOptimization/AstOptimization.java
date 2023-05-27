package pt.up.fe.comp2023.astOptimization;


import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp2023.constFolding.JmmVisitorForConstFolding;
import pt.up.fe.comp2023.constPropagation.JmmVisitorForConstPropagation;

public class AstOptimization {
    public void optimize(JmmSemanticsResult jmmSemanticsResult){
        JmmVisitorForConstFolding jmmVisitorForConstFolding = new JmmVisitorForConstFolding();
        jmmVisitorForConstFolding.visit(jmmSemanticsResult.getRootNode());
        JmmVisitorForConstPropagation jmmVisitorForConstPropagation = new JmmVisitorForConstPropagation();
        jmmVisitorForConstPropagation.visit(jmmSemanticsResult.getRootNode());
    }
}
