package pt.up.fe.comp2023.ollir;

import pt.up.fe.comp.jmm.analysis.table.Type;

public class OllirUtils {
    public static String typeToOllir(Type type){
        String typeName = type.getName();
        switch(typeName){
            case "int":
                typeName="i32";
                break;
            case "boolean":
                typeName="bool";
                break;
            case "void":
                typeName="V";
                break;
            default:
                break; //already the right name (classname)
        }
        if(type.isArray()){
            return "array." + typeName;
        }
        else
            return typeName;
    }
}
