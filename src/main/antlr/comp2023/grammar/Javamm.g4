grammar Javamm;

@header {
    package pt.up.fe.comp2023;
}

INTEGER : [0-9]+ ;
ID : [a-zA-Z_][a-zA-Z_0-9]* ;

WS : [ \t\n\r\f]+ -> skip ;

program
    : (importDeclaration)* classDeclaration EOF
    ;

importDeclaration :
    'import' ((path+= ID)'.')* className=ID ';'
    ;

classDeclaration :
    'class' className=ID ( 'extends' classParentName=ID )? '{'
        (varDeclaration)* ( methodDeclaration )*
    '}'
    ;


varDeclaration :
        type varName=ID ';'
    ;


methodDeclaration :
    ('public')? type functionName=ID '(' ( type arguments+=ID ( ',' type arguments+=ID )* )? ')' '{'
        (varDeclaration)* ( statement )* 'return' expression ';'
    '}' #normalMethod
    | ('public')? 'static' 'void' functionName='main' '(' 'String' '[' ']' argument=ID ')' '{'
        (varDeclaration)* ( statement )*
    '}' #staticMethod
    ;


type locals[boolean isArray=false]
    : value='int' ('['']'{$isArray=true;})?
    | value='boolean'
    | value='int'
    | value='String'
    | value = ID
    ;

statement
    : '{' ( statement )* '}' #BlockOfStatements
    | 'if' '(' expression ')' thenDo=statement 'else' elseDo=statement #IfStatement
    | 'while' '(' expression ')' repeat=statement #WhileStatement
    | expression ';' #ExpressionStatement
    | var = ID '=' expression ';' #Assignment
    | array = ID '[' expression ']' '=' expression ';' #ArrayAssignment
    ;


expression
    :
    op='!' expression #Negation
    | expression op=('*' | '/') expression #BinaryOp
    | expression op=('+' | '-') expression #BinaryOp
    | expression op='<' expression #BinaryOp
    | expression op='&&' expression #BinaryOp
    | expression '[' expression ']' #ArrayAccess
    | expression '.' 'length' #Length
    | expression '.' methodName=ID '(' ( expression ( ',' expression )* )? ')' #MethodCall
    | 'new' 'int' '[' expression ']' #ArrayConstructor
    | 'new' className=ID '(' ')' #Constructor
    | '(' expression ')' #Grouping
    | value=INTEGER #Integer
    | value='true' #True
    | value='false' #False
    | value=ID #Identifier
    | value='this' #This
    ;
