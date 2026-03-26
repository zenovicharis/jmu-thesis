lexer grammar CoomLexer;

PRODUCT       : 'product';
STRUCTURE     : 'structure';
ENUMERATION   : 'enumeration';
BEHAVIOR      : 'behavior';

ATTRIBUTE     : 'attribute';
NUMTYPE       : 'num' ('.' [#0-9]+)?;
BOOLTYPE      : 'bool';
STRINGTYPE    : 'string';

EXPLANATION   : 'explanation';
DEFAULT       : 'default' -> pushMode(EXPR);
REQUIRE       : 'require' -> pushMode(EXPR);
IMPLY         : 'imply' -> pushMode(EXPR);
CONDITION     : 'condition' -> pushMode(EXPR);

COMBINATIONS  : 'combinations';
ALLOW         : 'allow';

LBRACE        : '{';
RBRACE        : '}';
LPAREN        : '(';
RPAREN        : ')';
ASSIGN        : '=';
COMMA         : ',';
SLASH         : '/';
SEMI          : ';';
DOT           : '.';

DASH          : '-';
DOTDOT        : '..';
STAR          : '*';

IDENT         : [A-Za-z_][A-Za-z_0-9]*;
INT           : [0-9]+;
ANY           : '-*-';

STRING
  : '"' ( '\\"' | ~["\\] )* '"'
  ;

// Comments and whitespace
LINE_COMMENT  : '//' ~[\r\n]* -> skip;

// NEWLINE is a real token, used by the parser.
NEWLINE       : ('\r'? '\n')+ ;

// Skip spaces/tabs only (NOT newlines)
WS            : [ \t]+ -> skip;

// -------------------- Lexer mode for expressions --------------------
mode EXPR;

// Capture full expression text (including spaces) up to line/statement end.
EXPR_TEXT
  : ~[;\r\n}]+ -> popMode
  ;

// Pass through terminators so the parser can see them.
EXPR_NEWLINE  : ('\r'? '\n')+ -> popMode, type(NEWLINE);
EXPR_SEMI     : ';' -> popMode, type(SEMI);
EXPR_RBRACE   : '}' -> popMode, type(RBRACE);
