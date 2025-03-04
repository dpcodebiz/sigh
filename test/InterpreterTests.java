import norswap.autumn.AutumnTestFixture;
import norswap.autumn.Grammar;
import norswap.autumn.Grammar.rule;
import norswap.autumn.ParseResult;
import norswap.autumn.positions.LineMapString;
import norswap.sigh.SemanticAnalysis;
import norswap.sigh.SighGrammar;
import norswap.sigh.ast.SighNode;
import norswap.sigh.interpreter.Interpreter;
import norswap.sigh.interpreter.Null;
import norswap.uranium.Reactor;
import norswap.uranium.SemanticError;
import norswap.utils.IO;
import norswap.utils.TestFixture;
import norswap.utils.data.wrappers.Pair;
import norswap.utils.visitors.Walker;
import org.testng.annotations.Test;
import java.util.HashMap;
import java.util.Set;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertThrows;

public final class InterpreterTests extends TestFixture {

    // TODO peeling

    // ---------------------------------------------------------------------------------------------

    private final SighGrammar grammar = new SighGrammar();
    private final AutumnTestFixture autumnFixture = new AutumnTestFixture();

    {
        autumnFixture.runTwice = false;
        autumnFixture.bottomClass = this.getClass();
    }

    // ---------------------------------------------------------------------------------------------

    private Grammar.rule rule;

    // ---------------------------------------------------------------------------------------------

    private void check (String input, Object expectedReturn) {
        assertNotNull(rule, "You forgot to initialize the rule field.");
        check(rule, input, expectedReturn, null);
    }

    // ---------------------------------------------------------------------------------------------

    private void check (String input, Object expectedReturn, String expectedOutput) {
        assertNotNull(rule, "You forgot to initialize the rule field.");
        check(rule, input, expectedReturn, expectedOutput);
    }

    // ---------------------------------------------------------------------------------------------

    private void check (rule rule, String input, Object expectedReturn, String expectedOutput) {
        // TODO
        // (1) write proper parsing tests
        // (2) write some kind of automated runner, and use it here

        autumnFixture.rule = rule;
        ParseResult parseResult = autumnFixture.success(input);
        SighNode root = parseResult.topValue();

        Reactor reactor = new Reactor();
        Walker<SighNode> walker = SemanticAnalysis.createWalker(reactor);
        Interpreter interpreter = new Interpreter(reactor);
        walker.walk(root);
        reactor.run();
        Set<SemanticError> errors = reactor.errors();

        if (!errors.isEmpty()) {
            LineMapString map = new LineMapString("<test>", input);
            String report = reactor.reportErrors(it ->
                it.toString() + " (" + ((SighNode) it).span.startString(map) + ")");
            //            String tree = AttributeTreeFormatter.format(root, reactor,
            //                    new ReflectiveFieldWalker<>(SighNode.class, PRE_VISIT, POST_VISIT));
            //            System.err.println(tree);
            throw new AssertionError(report);
        }

        Pair<String, Object> result = IO.captureStdout(() -> interpreter.interpret(root));
        assertEquals(result.b, expectedReturn);
        if (expectedOutput != null) assertEquals(result.a, expectedOutput);
    }

    // ---------------------------------------------------------------------------------------------

    private void checkExpr (String input, Object expectedReturn, String expectedOutput) {
        rule = grammar.root;
        check("return " + input, expectedReturn, expectedOutput);
    }

    // ---------------------------------------------------------------------------------------------

    private void checkExpr (String input, Object expectedReturn) {
        rule = grammar.root;
        check("return " + input, expectedReturn);
    }

    // ---------------------------------------------------------------------------------------------

    private void checkThrows (String input, Class<? extends Throwable> expected) {
        assertThrows(expected, () -> check(input, null));
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testLiteralsAndUnary () {
        checkExpr("42", 42L);
        checkExpr("42.0", 42.0d);
        checkExpr("\"hello\"", "hello");
        checkExpr("(42)", 42L);
        checkExpr("[1, 2, 3]", new Object[]{1L, 2L, 3L});
        checkExpr("true", true);
        checkExpr("false", false);
        checkExpr("null", Null.INSTANCE);
        checkExpr("!false", true);
        checkExpr("!true", false);
        checkExpr("!!true", true);
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testNumericBinary () {
        checkExpr("1 + 2", 3L);
        checkExpr("2 - 1", 1L);
        checkExpr("2 * 3", 6L);
        checkExpr("2 / 3", 0L);
        checkExpr("3 / 2", 1L);
        checkExpr("2 % 3", 2L);
        checkExpr("3 % 2", 1L);

        checkExpr("1.0 + 2.0", 3.0d);
        checkExpr("2.0 - 1.0", 1.0d);
        checkExpr("2.0 * 3.0", 6.0d);
        checkExpr("2.0 / 3.0", 2d / 3d);
        checkExpr("3.0 / 2.0", 3d / 2d);
        checkExpr("2.0 % 3.0", 2.0d);
        checkExpr("3.0 % 2.0", 1.0d);

        checkExpr("1 + 2.0", 3.0d);
        checkExpr("2 - 1.0", 1.0d);
        checkExpr("2 * 3.0", 6.0d);
        checkExpr("2 / 3.0", 2d / 3d);
        checkExpr("3 / 2.0", 3d / 2d);
        checkExpr("2 % 3.0", 2.0d);
        checkExpr("3 % 2.0", 1.0d);

        checkExpr("1.0 + 2", 3.0d);
        checkExpr("2.0 - 1", 1.0d);
        checkExpr("2.0 * 3", 6.0d);
        checkExpr("2.0 / 3", 2d / 3d);
        checkExpr("3.0 / 2", 3d / 2d);
        checkExpr("2.0 % 3", 2.0d);
        checkExpr("3.0 % 2", 1.0d);

        checkExpr("2 * (4-1) * 4.0 / 6 % (2+1)", 1.0d);
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testOtherBinary () {
        checkExpr("true  && true",  true);
        checkExpr("true  || true",  true);
        checkExpr("true  || false", true);
        checkExpr("false || true",  true);
        checkExpr("false && true",  false);
        checkExpr("true  && false", false);
        checkExpr("false && false", false);
        checkExpr("false || false", false);

        checkExpr("1 + \"a\"", "1a");
        checkExpr("\"a\" + 1", "a1");
        checkExpr("\"a\" + true", "atrue");

        checkExpr("1 == 1", true);
        checkExpr("1 == 2", false);
        checkExpr("1.0 == 1.0", true);
        checkExpr("1.0 == 2.0", false);
        checkExpr("true == true", true);
        checkExpr("false == false", true);
        checkExpr("true == false", false);
        checkExpr("1 == 1.0", true);
        checkExpr("[1] == [1]", false);

        checkExpr("1 != 1", false);
        checkExpr("1 != 2", true);
        checkExpr("1.0 != 1.0", false);
        checkExpr("1.0 != 2.0", true);
        checkExpr("true != true", false);
        checkExpr("false != false", false);
        checkExpr("true != false", true);
        checkExpr("1 != 1.0", false);

        checkExpr("\"hi\" != \"hi2\"", true);
        checkExpr("[1] != [1]", true);

         // test short circuit
        checkExpr("true || print(\"x\") == \"y\"", true, "");
        checkExpr("false && print(\"x\") == \"y\"", false, "");
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testVarDecl () {
        check("var x: Int = 1; return x", 1L);
        check("var x: Float = 2.0; return x", 2d);

        check("var x: Int = 0; return x = 3", 3L);
        check("var x: String = \"0\"; return x = \"S\"", "S");

        // implicit conversions
        check("var x: Float = 1; x = 2; return x", 2.0d);
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testRootAndBlock () {
        rule = grammar.root;
        check("return", null);
        check("return 1", 1L);
        check("return 1; return 2", 1L);

        check("print(\"a\")", null, "a\n");
        check("print(\"a\" + 1)", null, "a1\n");
        check("print(\"a\"); print(\"b\")", null, "a\nb\n");

        check("{ print(\"a\"); print(\"b\") }", null, "a\nb\n");

        check(
            "var x: Int = 1;" +
            "{ print(\"\" + x); var x: Int = 2; print(\"\" + x) }" +
            "print(\"\" + x)",
            null, "1\n2\n1\n");
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testCalls () {
        rule = grammar.root;

        check(
            "fun add (a: Int, b: Int): Int { return a + b } " +
                "return add(4, 7)",
            11L);

        HashMap<String, Object> point = new HashMap<>();
        point.put("x", 1L);
        point.put("y", 2L);

        check(
            "struct Point { var x: Int; var y: Int }" +
                "return $Point(1, 2)",
            point);

        check("var str: String = null; return print(str + 1)", "null1", "null1\n");
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testArrayStructAccess () {
        checkExpr("[1][0]", 1L);
        checkExpr("[1.0][0]", 1d);
        checkExpr("[1, 2][1]", 2L);

        // TODO check that this fails (& maybe improve so that it generates a better message?)
        // or change to make it legal (introduce a top type, and make it a top type array if thre
        // is no inference context available)
        // checkExpr("[].length", 0L);
        checkExpr("[1].length", 1L);
        checkExpr("[1, 2].length", 2L);

        checkThrows("var array: Int[] = null; return array[0]", NullPointerException.class);
        checkThrows("var array: Int[] = null; return array.length", NullPointerException.class);

        check("var x: Int[] = [0, 1]; x[0] = 3; return x[0]", 3L);
        checkThrows("var x: Int[] = []; x[0] = 3; return x[0]",
            ArrayIndexOutOfBoundsException.class);
        checkThrows("var x: Int[] = null; x[0] = 3",
            NullPointerException.class);

        check(
            "struct P { var x: Int; var y: Int }" +
                "return $P(1, 2).y",
            2L);

        checkThrows(
            "struct P { var x: Int; var y: Int }" +
                "var p: P = null;" +
                "return p.y",
            NullPointerException.class);

        check(
            "struct P { var x: Int; var y: Int }" +
                "var p: P = $P(1, 2);" +
                "p.y = 42;" +
                "return p.y",
            42L);

        checkThrows(
            "struct P { var x: Int; var y: Int }" +
                "var p: P = null;" +
                "p.y = 42",
            NullPointerException.class);
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testIfWhile () {
        check("if (true) return 1 else return 2", 1L);
        check("if (false) return 1 else return 2", 2L);
        check("if (false) return 1 else if (true) return 2 else return 3 ", 2L);
        check("if (false) return 1 else if (false) return 2 else return 3 ", 3L);

        check("var i: Int = 0; while (i < 3) { print(\"\" + i); i = i + 1 } ", null, "0\n1\n2\n");
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testInference () {
        check("var array: Int[] = []", null);
        check("var array: String[] = []", null);
        check("fun use_array (array: Int[]) {} ; use_array([])", null);
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testTypeAsValues () {
        check("struct S{} ; return \"\"+ S", "S");
        check("struct S{} ; var type: Type = S ; return \"\"+ type", "S");
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testUnconditionalReturn()
    {
        check("fun f(): Int { if (true) return 1 else return 2 } ; return f()", 1L);
    }

    // ---------------------------------------------------------------------------------------------

    @Test public void testHelloBuiltIn() {
        rule = grammar.root;

        check("hello()", null,"Hello world !\n");
        checkThrows("hello('test')", AssertionError.class);
    }

    // ---------------------------------------------------------------------------------------------

    @Test
    public void testSimpleTemplateDeclarations () {
        // OK
        rule = grammar.root;

        // No template parameters usage
        check(
            "template<A, B>" +
                "fun add (a: Int, b: Int): Int { return a + b } ",
            null
            );

        // Template params
        check(
            "template<A, B>" +
                "fun add (a: A, b: B): Int { return a + b } ",
            null
        );

        // Template return
        check(
            "template<A, B>" +
                "fun add (a: Int, b: Int): A { return a + b } ",
            null
        );

        // All
        check(
            "template<A, B>" +
                "fun add (a: A, b: A): B { return a + b } ",
            null
        );
    }

    @Test
    public void TestTemplateCallErrors() {
        rule = grammar.root;

        // Checking template parameter type not provided
        checkThrows(
            "template<A, B> fun add (a: A, b: B): Int { return a + b }; " +
                "return add<String, String>(1, 1);",
            AssertionError.class
        );

        // Checking mismatch template type and argument type
        checkThrows(
            "template<A, B> fun add (a: A, b: B): Int { return a + b }; " +
                "return add<String, String>(1, 1);",
            AssertionError.class
        );

        // Checking too many template parameter types provided
        checkThrows(
            "template<A> fun add (a: A, b: Int): Int { return a + b }; " +
                "return add<Int, Int>(1, 1);",
            AssertionError.class
        );
    }

    @Test
    public void TestSimpleTemplateCalls () {
        // OK
        rule = grammar.root;

        // No template parameters usage
        check(
            "template<A, B>" +
                "fun add (a: Int, b: Int): Int { return a + b };" +
                "return add<Int, Int>(1, 1)",
            2L
        );

        // Template params
        check(
            "template<A, B>" +
                "fun add (a: A, b: B): Int { return a + b };" +
                "return add<Int, Int>(1,1)",
            2L
        );

        // Template return
        check(
            "template<A, B>" +
                "fun add (a: Int, b: Int): A { return a + b } ",
            null
        );

        // All
        check(
            "template<A, B>" +
                "fun add (a: A, b: A): B { return a + b };" +
                "return add<Int, Int>(1, 1)",
            2L
        );

        // All and more template arguments than function arguments
        check(
            "template<A, B>" +
                "fun double (a: A): B { return a + a };" +
                "return double<Int, Int>(1)",
            2L
        );
    }

    @Test
    public void TestSimpleTypesTemplateCalls() {
        // OK
        rule = grammar.root;

        // Int sum
        check(
            "template<A, B>" +
                "fun add (a: A, b: B): B { return a + b };" +
                "return add<Int, Int>(1, 1)",
            2L
        );

        // String concat
        check(
            "template<A, B>" +
                "fun add (a: A, b: B): B { return a + b };" +
                "return add<String, String>(\"Gauthier\", \"ArrayOperator\")",
            "GauthierArrayOperator"
        );

        // Float
        check(
            "template<A, B>" +
                "fun add (a: A, b: B): B { return a + b };" +
                "return add<Float, Float>(1.0, 1.0)",
            2.0
        );

        // Bool
        check(
            "template<A, B>" +
                "fun logic (a: A, b: B): B { return a && b };" +
                "return logic<Bool, Bool>(true, false)",
            false
        );
    }

    @Test
    public void TestComplexCalls() {
        rule = grammar.root;

        // TODO fix being able to assign a value to any type with templates ?

        // Deep Array access inside Type
        // TODO fix the C type not being handled properly
        /*check(
            "template<A, B, C>" +
                "fun getFirstEntryOfA (a: A, b: B): C { return a[0] };" +
                "var t:Int[] = getFirstEntryOfA<Int[], Int[], Int[]>([1], [1])" +
                "return t",
            1L
        );*/

        // Array access sum
        check(
            "template<A, B>" +
                "fun sumArray (a: A, b: B): Int { return a[0] + b[0] };" +
                "return sumArray<Int[], Int[]>([1], [1])",
            2L
        );

        // Array deep depth template type
        check(
            "template<A, B>" +
                "fun sumDeep (a: A, b: B): Int { return a[0][0] + b[0] };" +
                "return sumDeep<Int[][], Int[]>([[1]], [1])",
            2L
        );
    }

    @Test
    public void TestComplexTemplateDeclarations() {
        // OK
        rule = grammar.root;

        // Multiple template declarations
        check(
            "template<A, B>" +
                "fun add (a: A, b: B): B { return a + b };" +
                "template<A, B>" +
                "fun add2 (a: A, b: B): B { return a + b};",
            null
        );

        // Template declaration inside scope
        check(
            "fun test() {" +
                    "template<A, B>" +
                    "fun add (a: A, b: B): B { return a + b };" +
                    "template<A, B>" +
                    "fun add2 (a: A, b: B): B { return a + b};" +
                "}",
            null
        );
    }

    @Test
    public void TestComplexTemplateCalls() {
        // OK
        rule = grammar.root;

        // Multiple template declarations + mixed calls
        check(
            "template<A, B>" +
                "fun add (a: A, b: B): B { return a + b };" +
                "template<A, B>" +
                "fun add2 (a: A, b: B): B { return a + b};" +
                "return add<Int, Int>(1, 1) + add2<Int, Int>(1, 1)",
                4L
        );

        // Mixing template function return operations
        check(
            "template<A, B>" +
                "fun add (a: A, b: B): B { return a + b };" +
                "return add<Int, Int>(1, 1) + add<Int, Int>(1, 1) * 2",
            6L
        );

        check(
            "template<T1, T2>\n" +
                "fun sum(a:T1, b:T2):T1 {\n" +
                "    return a + b\n" +
                "}\n" +
                "\n" +
                "var result:Int = sum<Int, Int>(5, 5)\n" +
                "var result2:String = sum<String, String>(\"Hello\", \" world!\")\n" +
                "\n" +
                "print(\"\" + result)\n" +
                "print(\"\" + result2)",
            null
        );

    }
    @Test
    public void TestTemplateIntegration() {
        // OK
        rule = grammar.root;

        check(
            "template<T1, T2>\n" +
                "fun sum(a:T1, b:T2):T1 {\n" +
                "    return a + b\n" +
                "}\n" +
                "\n" +
                "var result:Int = sum<Int, Int>(5, 5)\n" +
                "var result2:String = sum<String, String>(\"Hello\", \" world!\")\n" +
                "\n" +
                "print(\"\" + result)\n" +
                "print(\"\" + result2)",
            null
        );

        check("template<T1, T2>\n" +
            "fun sum(a:T1, b:T2):T1 {\n" +
            "    return a + b\n" +
            "}\n" +
            "\n" +
            "template<T1, T2>\n" +
            "fun mult_array_first_elements(a:T1, b:T2):Int {\n" +
            "    return a[0] * b[0] * ([1, 0, 0] @ [2, 0, 0])\n" +
            "}\n" +
            "\n" +
            "var result:Int = sum<Int, Int>(5, 5)\n" +
            "var result2:String = sum<String, String>(\"Hello\", \" world!\")\n" +
            "\n" +
            "print(\"\" + result)\n" +
            "print(\"\" + result2)\n" +
            "\n" +
            "print(\"Let's do some more complex maths!!\")\n" +
            "\n" +
            "var a:Int = [1, 2, 3] @ [1, 0, 0]\n" +
            "var b:Int[] = [1, 0, 0]\n" +
            "\n" +
            "print(mult_array_first_elements<Int[], Int[]>([b @ [1, 0, 0], 0, 0], [3, 0, 0]))\n",
            null);

        check("template<T1, T2>\n" +
            "fun sum(a:T1, b:T2):T1 {\n" +
            "    return a + b\n" +
            "}\n" +
            "\n" +
            "template<T1, T2, R>\n" +
            "fun mult_array_first_elements(a:T1, b:T2):R {\n" +
            "    return a[0] * b[0] * ([1, 0, 0] @ [2, 0, 0])\n" +
            "}\n" +
            "\n" +
            "var result:Int = sum<Int, Int>(5, 5)\n" +
            "var result2:String = sum<String, String>(\"Hello\", \" world!\")\n" +
            "\n" +
            "print(\"\" + result)\n" +
            "print(\"\" + result2)\n" +
            "\n" +
            "print(\"Let's do some more complex maths!!\")\n" +
            "\n" +
            "var a:Int = [1, 2, 3] @ [1, 0, 0]\n" +
            "var b:Int[] = [1, 0, 0]\n" +
            "\n" +
            "fun bloop():Int[] {\n" +
            "    return [1, 1, 1]\n" +
            "}\n" +
            "\n" +
            "var prefix:String = sum<String, String>(\"The big number\", \" of this universe is probably :\");\n" +
            "var universe:Float = mult_array_first_elements<Float[], Int[], Float>([bloop() @ [1.0, 0.0, 0.0], 0.0, 0.0], [3, 0, 0]);\n" +
            "var inside_universe:Bool = (universe == 6.0);\n" +
            "\n" +
            "print(mult_array_first_elements<Int[], Int[], Int>([bloop() @ [1, 0, 0], 0, 0], [3, 0, 0]));\n" +
            "print(\"\" + (universe == 6));\n" +
            "print(\"\" + universe);\n" +
            "print(\"After some very deep computations, \" + prefix + mult_array_first_elements<Int[], Int[], Float>([bloop() @ [1, 0, 0], 0, 0], [3, 0, 0]));\n" +
            "\n" +
            "print(\"Therefore, are we leaving inside a simulation? \" + inside_universe)"
        , null);
    }

    @Test
    public void TestMultipleCallsInScope() {
        rule = grammar.root;

        check(
            "template<A, B>" +
                "fun add (a: A, b: B): A { return a + b };" +
                "return \"The result is : \" + add<Int, Int>(5, 5) + \" for value \" + add<String, String>(\"5\", \" and 5\")",
            "The result is : 10 for value 5 and 5"
        );
    }

    @Test
    public void TestTempor() {
        rule = grammar.root;

        // No template parameters usage
        check(
            "template<A, B>" +
                "fun add (a: A, b: B): Int { return a + b };" +
                "return add<Int, Int>(1, 1)",
            2L
        );
    }

    @Test
    public void testSimpleDotProduct () {
        rule = grammar.root;

        check(
            "return [1, 1] @ [1, 1]",
            2L
        );

    }

    @Test
    public void testDotProductFails () {
        rule = grammar.root;

        checkThrows(
            "return [] @ []",
            AssertionError.class
        );
        checkThrows(
            "return [[]] @ []",
            AssertionError.class
        );
        checkThrows(
            "return [] @ [[]]",
            AssertionError.class
        );

        checkThrows(
            "return 1 @ []",
            AssertionError.class
        );
        checkThrows(
            "return [] @ 1",
            AssertionError.class
        );
    }

    @Test
    public void testDotProductComplex() {

        rule = grammar.root;

        check(
            "var a:Int[] = [1, 1]" +
                "var b:Int[] = [1, 1]" +
                "return a @ b",
            2L
        );

        check(
            "var a:Int = [1, 1] @ [1, 1];" +
                "var b:Int = [1, 1] @ [1, 1]" +
                "return a + b",
            4L
        );

        check(
            "var a:Int = 1;" +
                "var b:Int = 1;" +
                "var c:Int[] = [a, a]" +
                "var d:Int[] = [b, b]" +
                "return c @ d",
            2L
        );
    }

    @Test
    public void testDotProductFloat() {

        rule = grammar.root;

        check(
            "var a:Float[] = [1.0, 1.0]" +
                "var b:Float[] = [1.0, 1.0]" +
                "return a @ b",
            2.0d
        );

        check(
            "var a:Float = [1.0, 1.0] @ [1.0, 1.0];" +
                "var b:Float = [1.0, 1.0] @ [1.0, 1.0]" +
                "return a + b",
            4.0d
        );

        check(
            "var a:Float = 1.0;" +
                "var b:Float = 1.0;" +
                "var c:Float[] = [a, a]" +
                "var d:Float[] = [b, b]" +
                "return c @ d",
            2.0d
        );
    }

    @Test
    public void testDotProductFloatMix() {

        rule = grammar.root;

        check(
            "var a:Float[] = [1.0, 1]" +
                "var b:Int[] = [1, 1]" +
                "return a @ b",
            2.0d
        );

        check(
            "var a:Float = [1.0, 1.0] @ [1.0, 1];" +
                "var b:Float = [1.0, 1.0] @ [1, 1]" +
                "return a + b",
            4.0d
        );

        check(
            "var a:Float = 1.0;" +
                "var b:Int = 1;" +
                "var c:Float[] = [a, a]" +
                "var d:Float[] = [b, b]" +
                "return c @ d",
            2.0d
        );
    }

    @Test
    public void testArrayScalarProduct() {
        rule = grammar.root;

        check("return 2 * [1, 1]",
            new Long[]{ 2L, 2L }
        );
        check("return 2 / [1, 1]",
            new Double[]{ 2.0d, 2.0d }
        );
        check("return [1, 1] / 2",
            new Double[]{ 0.5d, 0.5d }
        );
        check("return [1, 1] / 2.0",
            new Double[]{ 0.5d, 0.5d }
        );
        check("return [1.0, 1.0] / 2.0",
            new Double[]{ 0.5d, 0.5d }
        );
    }

    @Test
    public void testArrayScalarProductErrors() {
        rule = grammar.root;

        checkThrows("return \"test\" * [1, 1]",
            AssertionError.class);
        checkThrows("return \"test\" / [1, 1]",
            AssertionError.class);
        checkThrows("return [1, 1] * \"test\"",
            AssertionError.class);
        checkThrows("return [1,1] / \"test\"",
            AssertionError.class);
        checkThrows("return [1,1] + \"test\"",
            AssertionError.class);
        checkThrows("return [\"test\",\"test\"] * 2",
            AssertionError.class);
        checkThrows("return [[]] * 2",
            AssertionError.class);
        checkThrows("return [[]] / 2",
            AssertionError.class);
        checkThrows("return 2 * [[]]",
            AssertionError.class);
        checkThrows("return 2 / [[]]",
            AssertionError.class);
    }

    @Test
    public void testReportExamples() {
        rule = grammar.root;

        // Mono typed
        check("template<T1> \n" +
            "    fun sum(a:T1, b:T1):T1 {\n" +
            "        return a+b\n" +
            "    }\n" +
            "    \n" +
            "    print(sum<Int>(1,2)) // 3\n" +
            "    print(sum<Float>(1.0,2.0)) // 3.0\n" +
            "    print(\"Hello \" + sum<String>(\"Nicolas\",\"Laurent\")) // Hello NicolasLaurent",
            null,
            "3\n3.0\nHello NicolasLaurent\n"
        );

        // Poly typed
        check("template<R, T1, T2> \n" +
            "    fun complexFunction(a:T1, b:T2):R {\n" +
            "        return a && b\n" +
            "    }\n" +
            "    \n" +
            "    print(complexFunction<Bool, Bool, Bool>(true, false)) // false\n",
            null,
            "false\n");

        // Array support
        check(
            "template<T1, R> \n" +
                "fun addFirstElements(a:T1, b:T1):R {\n" +
                "        return a[0] + b[0];\n" +
                "    }\n" +
                "    print(addFirstElements<Int[], Int>([1], [1])); // 2\n",
            null,
            "2\n"
        );

        // Array support
        check(
            "// Specify the array type inside the function definition\n" +
                "    template<T1, T2, T3> \n" +
                "    fun addDeep(a:T1, b:T2):T3 {\n" +
                "        return a[0][0] + b[0]\n" +
                "    }\n" +
                "    \n" +
                "    print(addDeep<Int[][], Int[], Int>([[1], [1]], [1])) // 2",
            null,
            "2\n"
        );

        // Array dot product
        check("var a:Int[] = [1, 2, 3]\n" +
                "    var b:Int[] = [1, 2, 3]\n" +
                "    var c:Float[] = [1.0, 2.0, 3.0]\n" +
                "    \n" +
                "    // '@' operator\n" +
                "    var d:Int = a @ b // 14\n" +
                "    var e:Int = b @ a // 14\n" +
                "    var f:Float = a @ c // 14.0\n" +
                "print(\"\" + d);" +
                "print(\"\" + e);" +
                "print(\"\" + f);",
            null,
            "14\n14\n14.0\n");

        // Array scalar product
        check("var a:Int[] = [1, 2, 3]\n" +
            "    var b:Float[] = [1.0, 2.0, 3.0]\n" +
            "    \n" +
            "    // '*' operator\n" +
            "    var c:Int[] = 5 * a // [5, 10, 15]\n" +
            "return c",
            new Long[] { 5L, 10L, 15L }
        );

        check("var a:Int[] = [1, 2, 3]\n" +
                "    var b:Float[] = [1.0, 2.0, 3.0]\n" +
                "    \n" +
                "    // '*' operator\n" +
                "    var d:Float[] = 5.0 * a // [5.0, 10.0, 15.0]\n" +
                "    \n" +
                "return d",
            new Double[] { 5.0d, 10.0d, 15.0d }
        );

        check("var a:Int[] = [1, 2, 3]\n" +
                "    var b:Float[] = [1.0, 2.0, 3.0]\n" +
                "    \n" +
                "    // '*' operator\n" +
                "    var c:Int[] = 5 * a // [5, 10, 15]\n" +
                "    \n" +
                "    // '/' operator\n" +
                "    var e:Float[] = 5 / c // [1.0, 0.5, 0.3333...]\n" +
                "return e",
            new Double[] { 1.0d, 0.5d, ((double) 1/3) }
        );

        check("var a:Int[] = [1, 2, 3]\n" +
                "    var b:Float[] = [1.0, 2.0, 3.0]\n" +
                "    \n" +
                "    // '*' operator\n" +
                "    var c:Int[] = 5 * a // [5, 10, 15]\n" +
                "    var f:Float[] = c / 5 // [1.0, 2.0, 3.0]\n" +
                "return f",
            new Double[] { 1.0d, 2.0d, 3.0d }
        );

    }

    // NOTE(norswap): Not incredibly complete, but should cover the basics.
}
