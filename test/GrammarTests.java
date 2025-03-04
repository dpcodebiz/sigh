import norswap.autumn.AutumnTestFixture;
import norswap.sigh.SighGrammar;
import norswap.sigh.ast.*;
import norswap.sigh.ast.base.TemplateTypeDeclarationNode;
import norswap.sigh.ast.base.TemplateTypeNode;
import norswap.sigh.ast.base.TupleLiteralNode;
import org.testng.annotations.Test;

import static java.util.Arrays.asList;
import static norswap.sigh.ast.BinaryOperator.*;

public class GrammarTests extends AutumnTestFixture {
    // ---------------------------------------------------------------------------------------------

    private final SighGrammar grammar = new SighGrammar();
    private final Class<?> grammarClass = grammar.getClass();

    // ---------------------------------------------------------------------------------------------

    private static IntLiteralNode intlit (long i) {
        return new IntLiteralNode(null, i);
    }

    private static FloatLiteralNode floatlit (double d) {
        return new FloatLiteralNode(null, d);
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testLiteralsAndUnary () {
        rule = grammar.expression;

        successExpect("42", intlit(42));
        successExpect("42.0", floatlit(42d));
        successExpect("\"hello\"", new StringLiteralNode(null, "hello"));
        successExpect("(42)", new ParenthesizedNode(null, intlit(42)));
        successExpect("[1, 2, 3]", new ArrayLiteralNode(null, asList(intlit(1), intlit(2), intlit(3))));
        successExpect("true", new ReferenceNode(null, "true"));
        successExpect("false", new ReferenceNode(null, "false"));
        successExpect("null", new ReferenceNode(null, "null"));
        successExpect("!false", new UnaryExpressionNode(null, UnaryOperator.NOT, new ReferenceNode(null, "false")));
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testNumericBinary () {
        successExpect("1 + 2", new BinaryExpressionNode(null, intlit(1), ADD, intlit(2)));
        successExpect("2 - 1", new BinaryExpressionNode(null, intlit(2), SUBTRACT,  intlit(1)));
        successExpect("2 * 3", new BinaryExpressionNode(null, intlit(2), MULTIPLY, intlit(3)));
        successExpect("2 / 3", new BinaryExpressionNode(null, intlit(2), DIVIDE, intlit(3)));
        successExpect("2 % 3", new BinaryExpressionNode(null, intlit(2), REMAINDER, intlit(3)));

        successExpect("1.0 + 2.0", new BinaryExpressionNode(null, floatlit(1), ADD, floatlit(2)));
        successExpect("2.0 - 1.0", new BinaryExpressionNode(null, floatlit(2), SUBTRACT, floatlit(1)));
        successExpect("2.0 * 3.0", new BinaryExpressionNode(null, floatlit(2), MULTIPLY, floatlit(3)));
        successExpect("2.0 / 3.0", new BinaryExpressionNode(null, floatlit(2), DIVIDE, floatlit(3)));
        successExpect("2.0 % 3.0", new BinaryExpressionNode(null, floatlit(2), REMAINDER, floatlit(3)));

        successExpect("2 * (4-1) * 4.0 / 6 % (2+1)", new BinaryExpressionNode(null,
            new BinaryExpressionNode(null,
                new BinaryExpressionNode(null,
                    new BinaryExpressionNode(null,
                        intlit(2),
                        MULTIPLY,
                        new ParenthesizedNode(null, new BinaryExpressionNode(null,
                            intlit(4),
                            SUBTRACT,
                            intlit(1)))),
                    MULTIPLY,
                    floatlit(4d)),
                DIVIDE,
                intlit(6)),
            REMAINDER,
            new ParenthesizedNode(null, new BinaryExpressionNode(null,
                intlit(2),
                ADD,
                intlit(1)))));
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testArrayStructAccess () {
        rule = grammar.expression;
        successExpect("[1][0]", new ArrayAccessNode(null,
            new ArrayLiteralNode(null, asList(intlit(1))), intlit(0)));
        successExpect("[1].length", new FieldAccessNode(null,
            new ArrayLiteralNode(null, asList(intlit(1))), "length"));
        successExpect("p.x", new FieldAccessNode(null, new ReferenceNode(null, "p"), "x"));
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testDeclarations() {
        rule = grammar.statement;

        successExpect("var x: Int = 1", new VarDeclarationNode(null,
            "x", new SimpleTypeNode(null, "Int"), intlit(1)));

        successExpect("struct P {}", new StructDeclarationNode(null, "P", asList()));

        successExpect("struct P { var x: Int; var y: Int }",
            new StructDeclarationNode(null, "P", asList(
                new FieldDeclarationNode(null, "x", new SimpleTypeNode(null, "Int")),
                new FieldDeclarationNode(null, "y", new SimpleTypeNode(null, "Int")))));

        successExpect("fun f (x: Int): Int { return 1 }",
            new FunDeclarationNode(null, "f",
                asList(new ParameterNode(null, "x", new SimpleTypeNode(null, "Int"))),
                new SimpleTypeNode(null, "Int"),
                new BlockNode(null, asList(new ReturnNode(null, intlit(1))))));
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testStatements() {
        rule = grammar.statement;

        successExpect("return", new ReturnNode(null, null));
        successExpect("return 1", new ReturnNode(null, intlit(1)));
        successExpect("print(1)", new ExpressionStatementNode(null,
            new FunCallNode(null, new ReferenceNode(null, "print"), asList(intlit(1)))));
        successExpect("{ return }", new BlockNode(null, asList(new ReturnNode(null, null))));


        successExpect("if true return 1 else return 2", new IfNode(null, new ReferenceNode(null, "true"),
            new ReturnNode(null, intlit(1)),
            new ReturnNode(null, intlit(2))));

        successExpect("if false return 1 else if true return 2 else return 3 ",
            new IfNode(null, new ReferenceNode(null, "false"),
                new ReturnNode(null, intlit(1)),
                new IfNode(null, new ReferenceNode(null, "true"),
                    new ReturnNode(null, intlit(2)),
                    new ReturnNode(null, intlit(3)))));

        successExpect("while 1 < 2 { return } ", new WhileNode(null,
            new BinaryExpressionNode(null, intlit(1), LOWER, intlit(2)),
            new BlockNode(null, asList(new ReturnNode(null, null)))));
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testHelloStatements() {
        rule = grammar.statement;

        successExpect("hello()", new ExpressionStatementNode(null,
            new FunCallNode(null, new ReferenceNode(null, "hello"), asList())));
    }

    @Test public void testTemplateSimpleDeclaration() {
        rule = grammar.fun_decl;

        // No template parameters involved
        successExpect("template<T1>" +
                "fun myFunction(a:Int):Int {" +
                "return a" +
                "}", new FunDeclarationNode(
                null,
                "myFunction",
                asList(new ParameterNode(null,"a", new SimpleTypeNode(null, "Int"))),
                new SimpleTypeNode(null, "Int"),
                new BlockNode(null, asList(new ReturnNode(null, new ReferenceNode(null, "a")))),
                asList(new TemplateTypeDeclarationNode(null, "T1"))
            )
        );
        // Testing only template parameters
        successExpect("template<T1>" +
            "fun myFunction(a:T1):Int {" +
                "return a" +
            "}", new FunDeclarationNode(
                null,
                "myFunction",
                asList(new ParameterNode(null,"a", new TemplateTypeNode(null, "T1"))),
                new SimpleTypeNode(null, "Int"),
                new BlockNode(null, asList(new ReturnNode(null, new ReferenceNode(null, "a")))),
                asList(new TemplateTypeDeclarationNode(null, "T1"))
            )
        );
        // Testing with template return
        successExpect("template<T1>" +
                "fun myFunction(a:T1):T1 {" +
                "return a" +
                "}", new FunDeclarationNode(
                null,
                "myFunction",
                asList(new ParameterNode(null,"a", new TemplateTypeNode(null, "T1"))),
                new TemplateTypeNode(null, "T1"),
                new BlockNode(null, asList(new ReturnNode(null, new ReferenceNode(null, "a")))),
                asList(new TemplateTypeDeclarationNode(null, "T1"))
            )
        );
    }

    @Test public void testTemplateComplexDeclaration() {
        rule = grammar.fun_decl;

        // Simple array parameters vanilla
        successExpect("template<T1>" +
                "fun myFunction(a:Int[]):Int {" +
                "return a" +
                "}", new FunDeclarationNode(
                null,
                "myFunction",
                asList(new ParameterNode(null,"a", new ArrayTypeNode(null, new SimpleTypeNode(null, "Int")))),
                new SimpleTypeNode(null, "Int"),
                new BlockNode(null, asList(new ReturnNode(null, new ReferenceNode(null, "a")))),
                asList(new TemplateTypeDeclarationNode(null, "T1"))
            )
        );
        // Nested array parameters vanilla
        successExpect("template<T1>" +
                "fun myFunction(a:Int[][]):Int {" +
                "return a" +
                "}", new FunDeclarationNode(
                null,
                "myFunction",
                asList(new ParameterNode(null,"a", new ArrayTypeNode(null, new ArrayTypeNode(null, new SimpleTypeNode(null, "Int"))))),
                new SimpleTypeNode(null, "Int"),
                new BlockNode(null, asList(new ReturnNode(null, new ReferenceNode(null, "a")))),
                asList(new TemplateTypeDeclarationNode(null, "T1"))
            )
        );
        // Simple array template parameters
        successExpect("template<T1>" +
                "fun myFunction(a:T1[]):Int {" +
                "return a" +
                "}", new FunDeclarationNode(
                null,
                "myFunction",
                asList(new ParameterNode(null,"a", new ArrayTypeNode(null, new TemplateTypeNode(null, "T1")))),
                new SimpleTypeNode(null, "Int"),
                new BlockNode(null, asList(new ReturnNode(null, new ReferenceNode(null, "a")))),
                asList(new TemplateTypeDeclarationNode(null, "T1"))
            )
        );
        // Nested array template parameters
        successExpect("template<T1>" +
                "fun myFunction(a:T1[][]):Int {" +
                "return a" +
                "}", new FunDeclarationNode(
                null,
                "myFunction",
                asList(new ParameterNode(null,"a", new ArrayTypeNode(null, new ArrayTypeNode(null, new TemplateTypeNode(null, "T1"))))),
                new SimpleTypeNode(null, "Int"),
                new BlockNode(null, asList(new ReturnNode(null, new ReferenceNode(null, "a")))),
                asList(new TemplateTypeDeclarationNode(null, "T1"))
            )
        );
        // Nested array template parameters with return array template type
        successExpect("template<T1>" +
                "fun myFunction(a:T1[][]):Int[] {" +
                "return a" +
                "}", new FunDeclarationNode(
                null,
                "myFunction",
                asList(new ParameterNode(null,"a", new ArrayTypeNode(null, new ArrayTypeNode(null, new TemplateTypeNode(null, "T1"))))),
                new ArrayTypeNode(null, new SimpleTypeNode(null, "Int")),
                new BlockNode(null, asList(new ReturnNode(null, new ReferenceNode(null, "a")))),
                asList(new TemplateTypeDeclarationNode(null, "T1"))
            )
        );
        // Nested array template parameters with return nested template type
        successExpect("template<T1>" +
                "fun myFunction(a:T1[][]):T1[][] {" +
                "return a" +
                "}", new FunDeclarationNode(
                null,
                "myFunction",
                asList(new ParameterNode(null,"a", new ArrayTypeNode(null, new ArrayTypeNode(null, new TemplateTypeNode(null, "T1"))))),
                new ArrayTypeNode(null, new ArrayTypeNode(null, new TemplateTypeNode(null, "T1"))),
                new BlockNode(null, asList(new ReturnNode(null, new ReferenceNode(null, "a")))),
                asList(new TemplateTypeDeclarationNode(null, "T1"))
            )
        );
    }

    @Test public void testTemplateFunctionCall() {
        rule = grammar.suffix_expression;

        successExpect("myFunction<Int, Int>(5, 5)",
            new FunCallNode(null,
                new ReferenceNode(null, "myFunction"),
                asList(intlit(5), intlit(5)),
                asList(new SimpleTypeNode(null, "Int"), new SimpleTypeNode(null, "Int"))
            )
        );
        successExpect("myFunction(5, 5)",
            new FunCallNode(null,
                new ReferenceNode(null, "myFunction"),
                asList(intlit(5), intlit(5))
            )
        );
        successExpect("myFunction<MyCustomTypeMaybeForLater>(5, 5)",
            new FunCallNode(null,
                new ReferenceNode(null, "myFunction"),
                asList(intlit(5), intlit(5)),
                asList(new SimpleTypeNode(null, "MyCustomTypeMaybeForLater"))
            )
        );
    }

    @Test
    public void testTemplateComplexTypeFunctionCall() {
        rule = grammar.suffix_expression;

        // Int array
        successExpect("myFunction<Int[], Int[]>([5], [5])",
            new FunCallNode(null,
                new ReferenceNode(null, "myFunction"),
                asList(new ArrayLiteralNode(null, asList(intlit(5))), new ArrayLiteralNode(null, asList(intlit(5)))),
                asList(new ArrayTypeNode(null, new SimpleTypeNode(null, "Int")), new ArrayTypeNode(null, new SimpleTypeNode(null, "Int")))
            )
        );

        // String array
        successExpect("myFunction<String[], String[]>([\"test\"], [\"test\"])",
            new FunCallNode(null,
                new ReferenceNode(null, "myFunction"),
                asList(
                    new ArrayLiteralNode(null,
                        asList(
                            new StringLiteralNode(null, "test")
                        )
                    ),
                    new ArrayLiteralNode(null,
                        asList(
                            new StringLiteralNode(null, "test")
                        )
                    )
                ),
                asList(new ArrayTypeNode(null, new SimpleTypeNode(null, "String")), new ArrayTypeNode(null, new SimpleTypeNode(null, "String")))
            )
        );
    }

    @Test
    public void testDotProduct() {
        rule = grammar.expression;

        // Int array
        successExpect("[1, 1] @ [1, 1]",
            new BinaryExpressionNode(
                null,
                new ArrayLiteralNode(null, asList(intlit(1), intlit(1))),
                DOT_PRODUCT,
                new ArrayLiteralNode(null, asList(intlit(1), intlit(1)))
            )
        );
    }

    @Test
    public void testArrayScalarProduct() {
        rule = grammar.expression;

        successExpect("2 * [1, 1]",
            new BinaryExpressionNode(
                null,
                intlit(2),
                MULTIPLY,
                new ArrayLiteralNode(null, asList(intlit(1), intlit(1)))
            )
        );

        successExpect("2 / [1, 1]",
            new BinaryExpressionNode(
                null,
                intlit(2),
                DIVIDE,
                new ArrayLiteralNode(null, asList(intlit(1), intlit(1)))
            )
        );

    }

    @Test public void testTupleVarDeclaration() {
        rule = grammar.statement;

        successExpect("var t:Tuple = (5, 6)",
            new VarDeclarationNode(null, "t", new SimpleTypeNode(null, "Tuple"),
                new TupleLiteralNode(null,
                    asList(
                        intlit(5),
                        intlit(6)
                    )
                )
            )
        );
        successExpect("var t:Tuple = (\"hello\", \"world\")",
            new VarDeclarationNode(null, "t", new SimpleTypeNode(null, "Tuple"),
                new TupleLiteralNode(null,
                    asList(
                        new StringLiteralNode(null, "hello"),
                        new StringLiteralNode(null, "world")
                    )
                )
            )
        );
        successExpect("var t:Tuple = (\"hello\", \"world\", 5)",
            new VarDeclarationNode(null, "t", new SimpleTypeNode(null, "Tuple"),
                new TupleLiteralNode(null,
                    asList(
                        new StringLiteralNode(null, "hello"),
                        new StringLiteralNode(null, "world"),
                        intlit(5)
                    )
                )
            )
        );
        successExpect("var t:Tuple = (5.0, -5, \"pizza\", (5==6))",
            new VarDeclarationNode(null, "t", new SimpleTypeNode(null, "Tuple"),
                new TupleLiteralNode(null,
                    asList(
                        floatlit(5.0f),
                        intlit(-5),
                        new StringLiteralNode(null, "pizza"),
                        new ParenthesizedNode(null,
                            new BinaryExpressionNode(null, intlit(5), EQUALITY, intlit(6))
                        )
                    )
                )
            )
        );
    }
}
