parser grammar CoomParser;

options { tokenVocab=CoomLexer; }

coomFile
  : sep* statement (sep+ statement)* sep* EOF
  ;

statement
  : productDecl
  | structureDecl
  | enumerationDecl
  | behaviorDecl
  ;

sep
  : NEWLINE
  ;

// -------------------- product --------------------

// product { SundayBike sundayBike }
productDecl
  : PRODUCT LBRACE sep* productLine* RBRACE
  ;

productLine
  : (numDecl | multiplicityDecl | primitiveAttrDecl | typedAttrDecl) stmtEnd
  ;

// -------------------- structure --------------------

// structure SundayBike { ... }
structureDecl
  : STRUCTURE IDENT LBRACE sep* structureLine* RBRACE
  ;

structureLine
  : (numDecl | multiplicityDecl | primitiveAttrDecl | typedAttrDecl) stmtEnd
  ;

// -------------------- enumeration --------------------

// enumeration TopTubeBag { ... }
enumerationDecl
  : ENUMERATION IDENT LBRACE sep* enumerationLine* RBRACE
  ;

enumerationLine
  : (enumAttributeDecl | enumValueWithAttrsDecl | enumBareValueDecl) stmtEnd
  ;

// attribute num /mm length
enumAttributeDecl
  : ATTRIBUTE NUMTYPE unitSpec? IDENT
  ;

// short = ( 550 )
enumValueWithAttrsDecl
  : IDENT ASSIGN LPAREN valueList? RPAREN
  ;

// bare enum value
enumBareValueDecl
  : IDENT
  ;

// -------------------- behavior --------------------

// behavior SundayBike { ... }
behaviorDecl
  : BEHAVIOR IDENT? LBRACE sep* behaviorStmt* RBRACE
  ;

behaviorStmt
  : explanationStmt stmtEnd
  | defaultStmt stmtEnd
  | requireStmt stmtEnd
  | implyStmt stmtEnd
  | conditionStmt stmtEnd
  | combinationsBlock
  | sep
  ;

explanationStmt
  : EXPLANATION STRING
  ;

// default reach = 470
defaultStmt
  : DEFAULT expr
  ;

// require (topTubeBag.length + 30) < effectiveTopTubeLength
requireStmt
  : REQUIRE expr
  ;

// imply effectiveTopTubeLength = reach + (stack / tan(seatTubeAngle))
implyStmt
  : IMPLY expr
  ;

// condition <expr>
conditionStmt
  : CONDITION expr
  ;

// combinations(a,b){ allow(x,y) }
combinationsBlock
  : COMBINATIONS LPAREN identList? RPAREN LBRACE sep* allowStmt* RBRACE
  ;

allowStmt
  : ALLOW LPAREN allowRow? RPAREN stmtEnd
  ;

allowRow
  : allowItem (COMMA? allowItem)*
  ;

allowItem
  : value
  | tupleValue
  ;

tupleValue
  : LPAREN valueList? RPAREN
  ;

// -------------------- shared decls --------------------

// Type name  (also used for enum-typed attrs by heuristic in visitors)
typedAttrDecl
  : IDENT IDENT
  ;

// num 0-20 length
// num /mm reach
// num reach
primitiveAttrDecl
  : (BOOLTYPE | STRINGTYPE) IDENT
  ;

numDecl
  : NUMTYPE unitSpec? numRange? IDENT
  ;

numRange
  : INT DASH INT
  ;

unitSpec
  : SLASH IDENT
  ;

// 0..1 Lid lid  OR  0..* Lid lid
multiplicityDecl
  : INT DOTDOT (INT | STAR) IDENT IDENT
  ;

// lists
identList
  : path (COMMA? path)*
  ;

valueList
  : value (COMMA? value)*
  ;

value
  : IDENT
  | INT
  | STRING
  | ANY
  ;

// Pragmatic expression: one "text token" bounded by NEWLINE/SEMI/RBRACE
expr
  : EXPR_TEXT
  ;

// statement terminator: one or more NEWLINEs and/or optional semicolon
stmtEnd
  : SEMI? NEWLINE+
  | SEMI
  | NEWLINE+
  ;

path
  : IDENT (DOT IDENT)*
  ;
