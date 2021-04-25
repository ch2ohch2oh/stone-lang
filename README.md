## AST
### An example of AST
`13 + x * 2` represented in AST.
```
               BinaryExpr(operator = +)
                   /               \
                  /                 \
     NumberLiteral(value = 13)  BinaryExpr(operator = *)
                                    /            \
                                   /              \
                       Name(name = x)        NumberLiteral(value = 2)
```

## Syntax of the stone language
```
primary   := "(" expr ")" | NUMBER | IDENTIFIER | STRING
factor    := "-" primary | primary
expr      := factory { OP factor }
block     := "{" [ statement ] { ";" | EOL } [ statement] } "}"
simple    := expr
statement := "if" expr block [ "else" block ]
           | "while" expr block
           | simple
program   := [ statement ] (";" | EOL) 
```