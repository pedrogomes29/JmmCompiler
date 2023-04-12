package pt.up.fe.comp2023;

import org.specs.comp.ollir.ClassUnit;
import pt.up.fe.comp.jmm.jasmin.JasminResult;
import pt.up.fe.comp.jmm.ollir.OllirResult;

public class JasminBackend implements pt.up.fe.comp.jmm.jasmin.JasminBackend {
    @Override
    public JasminResult toJasmin(OllirResult ollirResult) {
        ClassUnit classUnit = ollirResult.getOllirClass();
        OllirVisitorForJasmin gen = new OllirVisitorForJasmin();
        String jasmin = gen.visit(classUnit);
        JasminResult result = new JasminResult(ollirResult, jasmin, ollirResult.getReports());
        return result;
    }
}
