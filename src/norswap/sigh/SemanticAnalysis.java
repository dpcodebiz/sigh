package norswap.sigh;

import norswap.autumn.positions.Span;
import norswap.sigh.ast.*;

import norswap.sigh.ast.base.TemplateTypeDeclarationNode;
import norswap.sigh.ast.base.TemplateTypeNode;
import norswap.sigh.scopes.DeclarationContext;
import norswap.sigh.scopes.DeclarationKind;
import norswap.sigh.scopes.RootScope;
import norswap.sigh.scopes.Scope;
import norswap.sigh.scopes.SyntheticDeclarationNode;
import norswap.sigh.types.*;
import norswap.uranium.Attribute;
import norswap.uranium.Reactor;
import norswap.uranium.Rule;
import norswap.uranium.SemanticError;
import norswap.utils.visitors.ReflectiveFieldWalker;
import norswap.utils.visitors.Walker;
import java.sql.Array;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.lang.String.format;
import static norswap.sigh.ast.BinaryOperator.*;
import static norswap.utils.Util.cast;
import static norswap.utils.Vanilla.forEachIndexed;
import static norswap.utils.Vanilla.list;
import static norswap.utils.visitors.WalkVisitType.POST_VISIT;
import static norswap.utils.visitors.WalkVisitType.PRE_VISIT;

/**
 * Holds the logic implementing semantic analyzis for the language, including typing and name
 * resolution.
 *
 * <p>The entry point into this class is {@link #createWalker(Reactor)}.
 *
 * <h2>Big Principles
 * <ul>
 *     <li>Every {@link DeclarationNode} instance must have its {@code type} attribute to an
 *     instance of {@link Type} which is the type of the value declared (note that for struct
 *     declaration, this is always {@link TypeType}.</li>
 *
 *     <li>Additionally, {@link StructDeclarationNode} (and default
 *     {@link SyntheticDeclarationNode} for types) must have their {@code declared} attribute set to
 *     an instance of the type being declared.</li>
 *
 *     <li>Every {@link ExpressionNode} instance must have its {@code type} attribute similarly
 *     set.</li>
 *
 *     <li>Every {@link ReferenceNode} instance must have its {@code decl} attribute set to the the
 *     declaration it references and its {@code scope} attribute set to the {@link Scope} in which
 *     the declaration it references lives. This speeds up lookups in the interpreter and simplifies the compiler.</li>
 *
 *     <li>For the same reasons, {@link VarDeclarationNode} and {@link ParameterNode} should have
 *     their {@code scope} attribute set to the scope in which they appear (this also speeds up the
 *     interpreter).</li>
 *
 *     <li>All statements introducing a new scope must have their {@code scope} attribute set to the
 *     corresponding {@link Scope} (only {@link RootNode}, {@link BlockNode} and {@link
 *     FunDeclarationNode} (for parameters)). These nodes must also update the {@code scope}
 *     field to track the current scope during the walk.</li>
 *
 *     <li>Every {@link TypeNode} instance must have its {@code value} set to the {@link Type} it
 *     denotes.</li>
 *
 *     <li>Every {@link ReturnNode}, {@link BlockNode} and {@link IfNode} must have its {@code
 *     returns} attribute set to a boolean to indicate whether its execution causes
 *     unconditional exit from the surrounding function or main script.</li>
 *
 *     <li>The rules check typing constraints: assignment of values to variables, of arguments to
 *     parameters, checking that if/while conditions are booleans, and array indices are
 *     integers.</li>
 *
 *     <li>The rules also check a number of other constraints: that accessed struct fields exist,
 *     that variables are declared before being used, etc...</li>
 * </ul>
 */
public final class SemanticAnalysis
{
    // =============================================================================================
    // region [Initialization]
    // =============================================================================================

    private final Reactor R;

    /** Current scope. */
    private Scope scope;

    /** Current context for type inference (currently only to infer the type of empty arrays). */
    private SighNode inferenceContext;

    /** Index of the current function argument. */
    private int argumentIndex;

    // ---------------------------------------------------------------------------------------------

    private SemanticAnalysis(Reactor reactor) {
        this.R = reactor;
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Call this method to create a tree walker that will instantiate the typing rules defined
     * in this class when used on an AST, using the given {@code reactor}.
     */
    public static Walker<SighNode> createWalker (Reactor reactor)
    {
        ReflectiveFieldWalker<SighNode> walker = new ReflectiveFieldWalker<>(
            SighNode.class, PRE_VISIT, POST_VISIT);

        SemanticAnalysis analysis = new SemanticAnalysis(reactor);

        // expressions
        walker.register(IntLiteralNode.class,           PRE_VISIT,  analysis::intLiteral);
        walker.register(FloatLiteralNode.class,         PRE_VISIT,  analysis::floatLiteral);
        walker.register(StringLiteralNode.class,        PRE_VISIT,  analysis::stringLiteral);
        walker.register(ReferenceNode.class,            PRE_VISIT,  analysis::reference);
        walker.register(ConstructorNode.class,          PRE_VISIT,  analysis::constructor);
        walker.register(ArrayLiteralNode.class,         PRE_VISIT,  analysis::arrayLiteral);
        walker.register(ParenthesizedNode.class,        PRE_VISIT,  analysis::parenthesized);
        walker.register(FieldAccessNode.class,          PRE_VISIT,  analysis::fieldAccess);
        walker.register(ArrayAccessNode.class,          PRE_VISIT,  analysis::arrayAccess);
        walker.register(FunCallNode.class,              PRE_VISIT,  analysis::funCall);
        walker.register(UnaryExpressionNode.class,      PRE_VISIT,  analysis::unaryExpression);
        walker.register(BinaryExpressionNode.class,     PRE_VISIT,  analysis::binaryExpression);
        walker.register(AssignmentNode.class,           PRE_VISIT,  analysis::assignment);

        // types
        walker.register(SimpleTypeNode.class,           PRE_VISIT,  analysis::simpleType);
        walker.register(ArrayTypeNode.class,            PRE_VISIT,  analysis::arrayType);
        walker.register(TemplateTypeNode.class,         PRE_VISIT,  analysis::templateType);

        // declarations & scopes
        walker.register(RootNode.class,                 PRE_VISIT,  analysis::root);
        walker.register(BlockNode.class,                PRE_VISIT,  analysis::block);
        walker.register(VarDeclarationNode.class,       PRE_VISIT,  analysis::varDecl);
        walker.register(TemplateTypeDeclarationNode.class, PRE_VISIT,  analysis::templateTypeDeclaration);
        walker.register(FieldDeclarationNode.class,     PRE_VISIT,  analysis::fieldDecl);
        walker.register(ParameterNode.class,            PRE_VISIT,  analysis::parameter);
        walker.register(FunDeclarationNode.class,       PRE_VISIT,  analysis::funDecl);
        walker.register(StructDeclarationNode.class,    PRE_VISIT,  analysis::structDecl);

        walker.register(RootNode.class,                 POST_VISIT, analysis::popScope);
        walker.register(BlockNode.class,                POST_VISIT, analysis::popScope);
        walker.register(FunDeclarationNode.class,       POST_VISIT, analysis::popScope);

        // statements
        walker.register(ExpressionStatementNode.class,  PRE_VISIT,  node -> {});
        walker.register(IfNode.class,                   PRE_VISIT,  analysis::ifStmt);
        walker.register(WhileNode.class,                PRE_VISIT,  analysis::whileStmt);
        walker.register(ReturnNode.class,               PRE_VISIT,  analysis::returnStmt);

        walker.registerFallback(POST_VISIT, node -> {});

        return walker;
    }

    // endregion
    // =============================================================================================
    // region [Expressions]
    // =============================================================================================

    private void intLiteral (IntLiteralNode node) {
        R.set(node, "type", IntType.INSTANCE);
    }

    // ---------------------------------------------------------------------------------------------

    private void floatLiteral (FloatLiteralNode node) {
        R.set(node, "type", FloatType.INSTANCE);
    }

    // ---------------------------------------------------------------------------------------------

    private void stringLiteral (StringLiteralNode node) {
        R.set(node, "type", StringType.INSTANCE);
    }

    // ---------------------------------------------------------------------------------------------

    // TODO ------------------------------------------------------------------------------
    // TODO take into account other types of expressions (not only binary expression node)
    // TODO ------------------------------------------------------------------------------
    private boolean involvesUninitializedTemplateParameter(SighNode node, Scope scope) {

        // Preparing nodes to check
        Deque<SighNode> referencesToCheck = new ArrayDeque<>();

        // Adding first element
        referencesToCheck.add(node);

        // Doing a DFS to find first uninitialized template parameter
        while (!referencesToCheck.isEmpty()) {

            // Poping node
            SighNode n = referencesToCheck.remove();

            if (n instanceof ReturnNode) {
                referencesToCheck.add(((ReturnNode) n).expression);

                continue;
            }

            // BinaryExpressionNode
            if (n instanceof BinaryExpressionNode) {
                referencesToCheck.add(((BinaryExpressionNode) n).left);
                referencesToCheck.add(((BinaryExpressionNode) n).right);

                continue;
            }

            // ArrayAccess
            if (n instanceof ArrayAccessNode) {
                referencesToCheck.add(((ArrayAccessNode) n).array);

                continue;
            }

            // ReferenceNode
            if (n instanceof ReferenceNode) {

                // Looking for nodes
                DeclarationContext context = scope.lookup(((ReferenceNode) n).name);

                // There is another rule handling this case
                if (context == null) continue;
                DeclarationNode declarationNode = context.declaration;

                // Adding to nodes to check
                referencesToCheck.add(declarationNode);

                continue;
            }

            // ParameterNode
            if (n instanceof ParameterNode) {
                TypeNode t = ((ParameterNode) n).type;

                // TemplateTypeNode
                if (t instanceof TemplateTypeNode) {

                    // Getting node declaration
                    DeclarationContext context = scope.lookup(((ParameterNode) n).name);

                    // There is another rule handling this case
                    if (context == null) return false;
                    SighNode declarationNode = context.scope.node;

                    // Checking if parameter has been defined in a templated function
                    if (declarationNode instanceof FunDeclarationNode) {

                        // Checking if actually a template parameter of given function declaration
                        if (((FunDeclarationNode) declarationNode).isTemplateType(t)) {

                            // Getting entry
                            TemplateTypeDeclarationNode typeDeclarationNode = null;
                            for (TemplateTypeDeclarationNode templateParameter : ((FunDeclarationNode) declarationNode).templateParameters) {
                                if (!templateParameter.name.equals(((TemplateTypeNode) t).name)) continue;

                                // Assigning entry
                                typeDeclarationNode = templateParameter;
                            }

                            // Skip if not found actually
                            if (typeDeclarationNode == null) continue;

                            // Adding to nodes to check
                            referencesToCheck.add(typeDeclarationNode);
                        }

                    }

                }

                continue;
            }

            // --------------------
            // Checking if template type
            // --------------------
            if (n instanceof TemplateTypeDeclarationNode) {

                // Continue if not initialized
                if (((TemplateTypeDeclarationNode) n).value != null) continue;

                return true;
            }

        }

        return false;
    }

    // ---------------------------------------------------------------------------------------------

    private void reference (ReferenceNode node)
    {
        final Scope scope = this.scope;

        // Try to lookup immediately. This must succeed for variables, but not necessarily for
        // functions or types. By looking up now, we can report looked up variables later
        // as being used before being defined.
        DeclarationContext maybeCtx = scope.lookup(node.name);

        if (maybeCtx != null) {
            R.set(node, "decl",  maybeCtx.declaration);
            R.set(node, "scope", maybeCtx.scope);

            R.rule(node, "type")
            .using(maybeCtx.declaration, "type")
            .by(Rule::copyFirst);
            return;
        }

        // Re-lookup after the scopes have been built.
        R.rule(node.attr("decl"), node.attr("scope"))
        .by(r -> {
            DeclarationContext ctx = scope.lookup(node.name);
            DeclarationNode decl = ctx == null ? null : ctx.declaration;

            if (ctx == null) {
                r.errorFor("Could not resolve: " + node.name,
                    node, node.attr("decl"), node.attr("scope"), node.attr("type"));
            }
            else {
                r.set(node, "scope", ctx.scope);
                r.set(node, "decl", decl);

                if (decl instanceof VarDeclarationNode)
                    r.errorFor("Variable used before declaration: " + node.name,
                        node, node.attr("type"));
                else
                    R.rule(node, "type")
                    .using(decl, "type")
                    .by(Rule::copyFirst);
            }
        });
    }

    // ---------------------------------------------------------------------------------------------

    private void constructor (ConstructorNode node)
    {
        R.rule()
        .using(node.ref, "decl")
        .by(r -> {
            DeclarationNode decl = r.get(0);

            if (!(decl instanceof StructDeclarationNode)) {
                String description =
                        "Applying the constructor operator ($) to non-struct reference for: "
                        + decl;
                r.errorFor(description, node, node.attr("type"));
                return;
            }

            StructDeclarationNode structDecl = (StructDeclarationNode) decl;

            Attribute[] dependencies = new Attribute[structDecl.fields.size() + 1];
            dependencies[0] = decl.attr("declared");
            forEachIndexed(structDecl.fields, (i, field) ->
                dependencies[i + 1] = field.attr("type"));

            R.rule(node, "type")
            .using(dependencies)
            .by(rr -> {
                Type structType = rr.get(0);
                Type[] params = IntStream.range(1, dependencies.length).<Type>mapToObj(rr::get)
                        .toArray(Type[]::new);
                rr.set(0, new FunType(structType, params));
            });
        });
    }

    // ---------------------------------------------------------------------------------------------

    private void arrayLiteral (ArrayLiteralNode node)
    {
        if (node.components.size() == 0) { // []
            // Empty array: we need a type int to know the desired type.

            final SighNode context = this.inferenceContext;

            if (context instanceof VarDeclarationNode)
                R.rule(node, "type")
                .using(context, "type")
                .by(Rule::copyFirst);
            else if (context instanceof FunCallNode) {
                R.rule(node, "type")
                .using(((FunCallNode) context).function.attr("type"), node.attr("index"))
                .by(r -> {
                    FunType funType = r.get(0);
                    r.set(0, funType.paramTypes[(int) r.get(1)]);
                });
            }
            return;
        }

        Attribute[] dependencies =
            node.components.stream().map(it -> it.attr("type")).toArray(Attribute[]::new);

        R.rule(node, "type")
        .using(dependencies)
        .by(r -> {
            Type[] types = IntStream.range(0, dependencies.length).<Type>mapToObj(r::get)
                    .distinct().toArray(Type[]::new);

            int i = 0;
            Type supertype = null;
            for (Type type: types) {
                if (type instanceof VoidType)
                    // We report the error, but compute a type for the array from the other elements.
                    r.errorFor("Void-valued expression in array literal", node.components.get(i));
                else if (supertype == null)
                    supertype = type;
                else {
                    supertype = commonSupertype(supertype, type);
                    if (supertype == null) {
                        r.error("Could not find common supertype in array literal.", node);
                        return;
                    }
                }
                ++i;
            }

            if (supertype == null)
                r.error(
                    "Could not find common supertype in array literal: all members have Void type.",
                    node);
            else
                r.set(0, new ArrayType(supertype));
        });
    }

    // ---------------------------------------------------------------------------------------------

    private void parenthesized (ParenthesizedNode node)
    {
        R.rule(node, "type")
        .using(node.expression, "type")
        .by(Rule::copyFirst);
    }

    // ---------------------------------------------------------------------------------------------

    private void fieldAccess (FieldAccessNode node)
    {
        R.rule()
        .using(node.stem, "type")
        .by(r -> {
            Type type = r.get(0);

            if (type instanceof ArrayType) {
                if (node.fieldName.equals("length"))
                    R.rule(node, "type")
                    .by(rr -> rr.set(0, IntType.INSTANCE));
                else
                    r.errorFor("Trying to access a non-length field on an array", node,
                        node.attr("type"));
                return;
            }
            
            if (!(type instanceof StructType)) {
                r.errorFor("Trying to access a field on an expression of type " + type,
                        node,
                        node.attr("type"));
                return;
            }

            StructDeclarationNode decl = ((StructType) type).node;

            for (DeclarationNode field: decl.fields)
            {
                if (!field.name().equals(node.fieldName)) continue;

                R.rule(node, "type")
                .using(field, "type")
                .by(Rule::copyFirst);

                return;
            }

            String description = format("Trying to access missing field %s on struct %s",
                    node.fieldName, decl.name);
            r.errorFor(description, node, node.attr("type"));
        });
    }

    // ---------------------------------------------------------------------------------------------

    private void arrayAccess (ArrayAccessNode node)
    {
        R.rule()
        .using(node.index, "type")
        .by(r -> {
            Type type = r.get(0);
            if (!(type instanceof IntType))
                r.error("Indexing an array using a non-Int-valued expression", node.index);
        });

        // Checking if any template parameters involved here
        boolean check = involvesUninitializedTemplateParameter(node, scope);

        R.rule(node, "type")
        .using(node.array, "type")
        .by(check
            ? r -> {
                Type type = r.get(0);

                // Needed in order to properly get the reference once calling the function
                type = type instanceof TemplateType ? ((TemplateType) type).getTemplateTypeReference() : type;

                r.set(0, type);
            }
            : r -> {
                Type type = r.get(0);

                // Needed in order to properly get the reference once calling the function
                type = type instanceof TemplateType ? ((TemplateType) type).getTemplateTypeReference() : type;

                if (type instanceof ArrayType)
                    r.set(0, ((ArrayType) type).componentType);
                else
                    r.error("Trying to index a non-array expression of type " + type, node);
            }
        );
    }

    // ---------------------------------------------------------------------------------------------

    private void funCall (FunCallNode node)
    {
        this.inferenceContext = node;

        // TODO check template arguments
        int depsSize = node.arguments.size() + 1 + (node.template_arguments != null ? node.template_arguments.size() + 1 : 0);
        Attribute[] dependencies = new Attribute[depsSize];
        dependencies[0] = node.function.attr("type");
        forEachIndexed(node.arguments, (i, arg) -> {
            dependencies[i+1] = arg.attr("type");
            R.set(arg, "index", i);
        });

        // Adding template arguments to dependencies
        int offset = node.arguments.size() + 1;
        int finalOffset = offset;

        if (node.template_arguments != null) {
            forEachIndexed(node.template_arguments, (i, arg) -> {
                dependencies[finalOffset+i] = arg.attr("value");
                R.set(arg, "index", i);
            });
        }

        offset += node.template_arguments != null ? node.template_arguments.size() : 0;
        DeclarationContext functionDeclarationContext = null;
        DeclarationNode functionDeclarationNode = null;
        String funName = "";

        // Getting proper function reference
        if (node.function instanceof ReferenceNode) {
            funName = ((ReferenceNode) node.function).name;
        } else if (node.function instanceof ConstructorNode) {
            funName = ((ConstructorNode) node.function).ref.name;
        }

        if (node.template_arguments != null) {

            functionDeclarationContext = scope.lookup(funName);
            functionDeclarationNode = functionDeclarationContext != null ? functionDeclarationContext.declaration : new FunDeclarationNode(new Span(0, 0), "", new ArrayList<>(), null, new BlockNode(null, new ArrayList<>()));

            // Setting dependencie
            dependencies[offset] = new Attribute(functionDeclarationNode, "type");
        }

        R.rule(node, "type")
        .using(dependencies)
        .by(r -> {
            Type maybeFunType = r.get(0);

            if (!(maybeFunType instanceof FunType)) {
                r.error("trying to call a non-function expression: " + node.function, node.function);
                return;
            }

            FunType funType = cast(maybeFunType);
            r.set(0, funType.returnType);

            Type[] params = funType.paramTypes;
            List<ExpressionNode> args = node.arguments;

            if (params.length != args.size())
                r.errorFor(format("wrong number of arguments, expected %d but got %d",
                        params.length, args.size()),
                    node);

            int checkedArgs = Math.min(params.length, args.size());

            // Assigning template arguments to template types
            int dependenciesSize = r.dependencies.length;
            Object funNode = r.dependencies[dependenciesSize-1].node;
            if (funNode instanceof FunDeclarationNode) {
                FunDeclarationNode funDeclarationNode = (FunDeclarationNode) r.dependencies[dependenciesSize-1].node;

                List<TemplateTypeDeclarationNode> template_params = funDeclarationNode.templateParameters;
                List<TypeNode> template_arguments = node.template_arguments;

                // Checking provided number of template args
                if (template_params !=null && template_arguments != null && template_params.size() != template_arguments.size()) {
                    r.errorFor(format("wrong number of template arguments, expected %d but got %d",
                        template_params.size(), template_arguments.size()), node);
                } else if (template_params != null && template_arguments == null && template_params.size() != 0) {
                    r.errorFor(format("wrong number of template arguments, expected %d but got %d",
                        template_params.size(), 0), node);
                } else {
                    node.setTemplateTypeReferences(template_arguments, template_params, funType.paramTypes);
                }
            }

            for (int i = 0; i < checkedArgs; ++i) {
                Type argType = r.get(i + 1);
                Type paramType =
                    (funType.paramTypes[i] instanceof TemplateType)
                        ? node.templateTypeReferences.get(i).value
                        : funType.paramTypes[i];
                if (!isAssignableTo(argType, paramType)) {
                    if (funType.paramTypes[i] instanceof TemplateType) {
                        if (paramType == null) {
                            r.errorFor(format(
                                    "Missing type of template parameter %s. Suggested template parameter type : %s",
                                    funType.paramTypes[i].name(), argType),
                                node.arguments.get(i));
                        } else {
                            r.errorFor(format(
                                    "Mismatch between argument type %s (argument [%d]) and provided type of template parameter %s (expected %s)",
                                    argType, i, funType.paramTypes[i].name(), paramType), node.arguments.get(i));
                        }
                    } else {
                        r.errorFor(format(
                                "incompatible argument provided for argument %d: expected %s but got %s",
                                i, paramType, argType),
                            node.arguments.get(i));
                    }
                }

            }
        });
    }

    // ---------------------------------------------------------------------------------------------

    private void unaryExpression (UnaryExpressionNode node)
    {
        assert node.operator == UnaryOperator.NOT; // only one for now
        R.set(node, "type", BoolType.INSTANCE);

        R.rule()
        .using(node.operand, "type")
        .by(r -> {
            Type opType = r.get(0);
            if (!(opType instanceof BoolType))
                r.error("Trying to negate type: " + opType, node);
        });
    }

    // endregion
    // =============================================================================================
    // region [Binary Expressions]
    // =============================================================================================

    private void binaryExpression (BinaryExpressionNode node)
    {

        if (node.operator == DOT_PRODUCT) {
            if (node.left instanceof ArrayLiteralNode) {
                if (((ArrayLiteralNode) node.left).components.size() == 0) {
                    R.error(new SemanticError("Trying to dot_product with empty arrays", null, null));
                    return;
                }
            }

            if (node.right instanceof ArrayLiteralNode) {
                if (((ArrayLiteralNode) node.right).components.size() == 0) {
                    R.error(new SemanticError("Trying to dot_product with empty arrays", null, null));
                    return;
                }
            }
        } else if (node.operator == MULTIPLY || node.operator == DIVIDE) {
            if (node.left instanceof ArrayLiteralNode) {
                if (((ArrayLiteralNode) node.left).components.size() == 0) {
                    R.error(new SemanticError("Expected a scalar but got a string", null, null));
                    return;
                } else if (((ArrayLiteralNode) node.left).components.get(0) instanceof StringLiteralNode) {
                    R.error(new SemanticError("Expected an int or a float array but got a string array", null, null));
                } else if (((ArrayLiteralNode) node.left).components.get(0) instanceof ArrayLiteralNode) {
                    R.error(new SemanticError("Scalar product with multidimensional array is not supported yet", null, null));
                }
                if (node.right instanceof StringLiteralNode) {
                    R.error(new SemanticError("Expected a scalar but got a string", null, null));
                    return;
                }
            }

            if (node.right instanceof ArrayLiteralNode) {
                if (((ArrayLiteralNode) node.right).components.size() == 0) {
                    R.error(new SemanticError("Expected a scalar but got a string", null, null));
                    return;
                } else if (((ArrayLiteralNode) node.right).components.get(0) instanceof StringLiteralNode) {
                    R.error(new SemanticError("Expected an int or a float array but got a string array", null, null));
                } else if (((ArrayLiteralNode) node.right).components.get(0) instanceof ArrayLiteralNode) {
                    R.error(new SemanticError("Scalar product with multidimensional array is not supported yet", null, null));
                }
                if (node.left instanceof StringLiteralNode) {
                    R.error(new SemanticError("Expected a scalar but got a string", null, null));
                    return;
                }
            }
        }

        // Checking if any template parameters involved here
        boolean check = involvesUninitializedTemplateParameter(node, scope);

        Scope s = scope;

        R.rule(node, "scope")
        .by(rule -> {
            rule.set(0, s);
        });

        R.rule(node, "type")
        .using(node.left.attr("type"), node.right.attr("type"))
        .by(check ? (r -> {
            // If template type, just push the template type
            // TODO actually, I think this might not behave as expected if the two template types are different we'll see in walker
            Type left  = r.get(0);
            Type right = r.get(1);
            r.set(0, (left instanceof TemplateType) ? left : right);
        }) : (r -> {
            Type left  = r.get(0);
            Type right = r.get(1);

            // Needed in order to be able to process the binary arithmetic properly
            if (left instanceof TemplateType) left = ((TemplateType) left).getTemplateTypeReference();
            if (right instanceof TemplateType) right = ((TemplateType) right).getTemplateTypeReference();
            if (node.operator == ADD && (left instanceof StringType || right instanceof StringType))
                r.set(0, StringType.INSTANCE);
            else if (isArrayArithmetic(node.operator, left, right))
                binaryArrayArithmetic(r, node, left, right);
            else if (isArithmetic(node.operator))
                binaryArithmetic(r, node, left, right);
            else if (isComparison(node.operator))
                binaryComparison(r, node, left, right);
            else if (isLogic(node.operator))
                binaryLogic(r, node, left, right);
            else if (isEquality(node.operator))
                binaryEquality(r, node, left, right);

        }));
    }

    // ---------------------------------------------------------------------------------------------

    private boolean isArithmetic (BinaryOperator op) {
        return op == ADD || op == MULTIPLY || op == SUBTRACT || op == DIVIDE || op == REMAINDER;
    }

    private boolean isComparison (BinaryOperator op) {
        return op == GREATER || op == GREATER_EQUAL || op == LOWER || op == LOWER_EQUAL;
    }

    private boolean isLogic (BinaryOperator op) {
        return op == OR || op == AND;
    }

    private boolean isEquality (BinaryOperator op) {
        return op == EQUALITY || op == NOT_EQUALS;
    }

    private boolean isArrayArithmetic (BinaryOperator op, Type left, Type right) {
        boolean arrayInvolved = left instanceof ArrayType || right instanceof ArrayType;
        return op == DOT_PRODUCT || (op == MULTIPLY && arrayInvolved) || (op == DIVIDE && arrayInvolved);
    }

    // ---------------------------------------------------------------------------------------------

    private void binaryArithmetic (Rule r, BinaryExpressionNode node, Type left, Type right)
    {
        if (left instanceof IntType)
            if (right instanceof IntType)
                r.set(0, IntType.INSTANCE);
            else if (right instanceof FloatType)
                r.set(0, FloatType.INSTANCE);
            else
                r.error(arithmeticError(node, "Int", right), node);
        else if (left instanceof FloatType)
            if (right instanceof IntType || right instanceof FloatType)
                r.set(0, FloatType.INSTANCE);
            else
                r.error(arithmeticError(node, "Float", right), node);
        else if (left instanceof TemplateType) {
            r.set(0, TemplateType.class);
        } else
            r.error(arithmeticError(node, left, right), node);
    }

    // ---------------------------------------------------------------------------------------------

    private static String arithmeticError (BinaryExpressionNode node, Object left, Object right) {
        return format("Trying to %s %s with %s", node.operator.name().toLowerCase(), left, right);
    }

    // ---------------------------------------------------------------------------------------------

    private void binaryComparison (Rule r, BinaryExpressionNode node, Type left, Type right)
    {
        r.set(0, BoolType.INSTANCE);

        if (!(left instanceof IntType) && !(left instanceof FloatType))
            r.errorFor("Attempting to perform arithmetic comparison on non-numeric type: " + left,
                node.left);
        if (!(right instanceof IntType) && !(right instanceof FloatType))
            r.errorFor("Attempting to perform arithmetic comparison on non-numeric type: " + right,
                node.right);
    }

    // ---------------------------------------------------------------------------------------------

    private void binaryEquality (Rule r, BinaryExpressionNode node, Type left, Type right)
    {
        r.set(0, BoolType.INSTANCE);

        if (!isComparableTo(left, right))
            r.errorFor(format("Trying to compare incomparable types %s and %s", left, right),
                node);
    }

    // ---------------------------------------------------------------------------------------------

    private void binaryLogic (Rule r, BinaryExpressionNode node, Type left, Type right)
    {
        r.set(0, BoolType.INSTANCE);

        if (!(left instanceof BoolType))
            r.errorFor("Attempting to perform binary logic on non-boolean type: " + left,
                node.left);
        if (!(right instanceof BoolType))
            r.errorFor("Attempting to perform binary logic on non-boolean type: " + right,
                node.right);
    }

    // ---------------------------------------------------------------------------------------------

    private boolean isArrayLegalForDotProduct(ArrayType array) {
        Type type = array.componentType;

        if (type instanceof IntType || type instanceof FloatType) {
            return true;
        } else {
            return false;
        }
    }

    private void binaryArrayArithmetic (Rule r, BinaryExpressionNode node, Type left, Type right)
    {
        switch (node.operator) {
            case DOT_PRODUCT:
            {
                if (left instanceof ArrayType && right instanceof ArrayType) {
                    Type leftType = ((ArrayType) left).componentType;
                    Type rightType = ((ArrayType) right).componentType;

                    boolean leftLegal = isArrayLegalForDotProduct((ArrayType) left);
                    boolean rightLegal = isArrayLegalForDotProduct((ArrayType) right);

                    if (!leftLegal) {
                        r.error("Left handside of a dot product operator can only be Int[] or Float[]", node);
                    }

                    if (!rightLegal) {
                        r.error("Right handside of a dot product operator can only be Int[] or Float[]", node);
                    }

                    if (leftLegal && rightLegal) {
                        r.set(0, IntType.INSTANCE);
                    }

                } else {
                    r.error(arithmeticError(node, left, right), node);
                }
                break;
            }
            case MULTIPLY:
            case DIVIDE: {
                if (left instanceof ArrayType) {
                    if (right instanceof IntType || right instanceof FloatType) {
                        r.set(0,
                            new ArrayType(
                                left instanceof IntType
                                ? IntType.INSTANCE
                                : FloatType.INSTANCE
                            )
                        );
                    } else {
                        r.error(arithmeticError(node, left, right), node);
                    }
                }
                if (right instanceof ArrayType) {
                    if (left instanceof IntType || left instanceof FloatType) {
                        r.set(0,
                            new ArrayType(
                                left instanceof IntType
                                    ? IntType.INSTANCE
                                    : FloatType.INSTANCE
                            )
                        );
                    } else {
                        r.error(arithmeticError(node, left, right), node);
                    }
                }

                break;
            }
        }

    }

    // ---------------------------------------------------------------------------------------------

    private void assignment (AssignmentNode node)
    {
        R.rule(node, "type")
        .using(node.left.attr("type"), node.right.attr("type"))
        .by(r -> {
            Type left  = r.get(0);
            Type right = r.get(1);

            r.set(0, r.get(0)); // the type of the assignment is the left-side type

            if (node.left instanceof ReferenceNode
            ||  node.left instanceof FieldAccessNode
            ||  node.left instanceof ArrayAccessNode) {
                if (!isAssignableTo(right, left))
                    r.errorFor("Trying to assign a value to a non-compatible lvalue.", node);
            }
            else
                r.errorFor("Trying to assign to an non-lvalue expression.", node.left);
        });
    }

    // endregion
    // =============================================================================================
    // region [Types & Typing Utilities]
    // =============================================================================================

    private void simpleType (SimpleTypeNode node)
    {
        final Scope scope = this.scope;

        R.rule()
        .by(r -> {
            // type declarations may occur after use
            DeclarationContext ctx = scope.lookup(node.name);
            DeclarationNode decl = ctx == null ? null : ctx.declaration;

            if (ctx == null)
                r.errorFor("could not resolve: " + node.name,
                    node,
                    node.attr("value"));

            else if (!isTypeDecl(decl))
                r.errorFor(format(
                    "%s did not resolve to a type declaration but to a %s declaration",
                    node.name, decl.declaredThing()),
                    node,
                    node.attr("value"));

            else
                R.rule(node, "value")
                .using(decl, "declared")
                .by(Rule::copyFirst);
        });
    }

    // ---------------------------------------------------------------------------------------------

    private void arrayType (ArrayTypeNode node)
    {
        R.rule(node, "value")
        .using(node.componentType, "value")
        .by(r -> r.set(0, new ArrayType(r.get(0))));
    }

    // ---------------------------------------------------------------------------------------------

    private void templateType(TemplateTypeNode node) {
        final Scope scope = this.scope;

        R.rule()
        .by(r -> {
            // type declarations may occur after use
            DeclarationContext ctx = scope.lookup(node.name);
            DeclarationNode decl = ctx == null ? null : ctx.declaration;

            if (ctx == null)
                r.errorFor("could not resolve: " + node.name,
                    node,
                    node.attr("value"));

            else if (!isTypeDecl(decl))
                r.errorFor(format(
                        "[Template] %s did not resolve to a type declaration but to a %s declaration",
                        node.name, decl.declaredThing()),
                    node,
                    node.attr("value"));

            else
                R.rule(node, "value")
                    .using(decl, "declared")
                    .by(Rule::copyFirst);
        });
    }

    // ---------------------------------------------------------------------------------------------

    private static boolean isTypeDecl (DeclarationNode decl)
    {
        // Taking into account template type nodes
        if (decl instanceof TemplateTypeDeclarationNode) return true;

        if (decl instanceof StructDeclarationNode) return true;
        if (!(decl instanceof SyntheticDeclarationNode)) return false;
        SyntheticDeclarationNode synthetic = cast(decl);
        return synthetic.kind() == DeclarationKind.TYPE;
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Indicates whether a value of type {@code a} can be assigned to a location (variable,
     * parameter, ...) of type {@code b}.
     */
    private static boolean isAssignableTo (Type a, Type b)
    {
        if (a instanceof VoidType || b instanceof VoidType)
            return false;

        if (a instanceof IntType && b instanceof FloatType)
            return true;

        if (a instanceof ArrayType)
            return b instanceof ArrayType
                && isAssignableTo(((ArrayType)a).componentType, ((ArrayType)b).componentType);

        return a instanceof NullType && b.isReference() || a.equals(b);
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Indicate whether the two types are comparable.
     */
    private static boolean isComparableTo (Type a, Type b)
    {
        if (a instanceof VoidType || b instanceof VoidType)
            return false;

        return a.isReference() && b.isReference()
            || a.equals(b)
            || a instanceof IntType && b instanceof FloatType
            || a instanceof FloatType && b instanceof IntType;
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Returns the common supertype between both types, or {@code null} if no such supertype
     * exists.
     */
    private static Type commonSupertype (Type a, Type b)
    {
        if (a instanceof VoidType || b instanceof VoidType)
            return null;
        if (isAssignableTo(a, b))
            return b;
        if (isAssignableTo(b, a))
            return a;
        else
            return null;
    }

    // endregion
    // =============================================================================================
    // region [Scopes & Declarations]
    // =============================================================================================

    private void popScope (SighNode node) {
        scope = scope.parent;
    }

    // ---------------------------------------------------------------------------------------------

    private void root (RootNode node) {
        assert scope == null;
        scope = new RootScope(node, R);
        R.set(node, "scope", scope);
    }

    // ---------------------------------------------------------------------------------------------

    private void block (BlockNode node) {
        scope = new Scope(node, scope);
        R.set(node, "scope", scope);

        Attribute[] deps = getReturnsDependencies(node.statements);
        R.rule(node, "returns")
        .using(deps)
        .by(r -> r.set(0, deps.length != 0 && Arrays.stream(deps).anyMatch(r::get)));
    }

    // ---------------------------------------------------------------------------------------------

    private void varDecl (VarDeclarationNode node)
    {
        this.inferenceContext = node;

        scope.declare(node.name, node);
        R.set(node, "scope", scope);

        R.rule(node, "type")
        .using(node.type, "value")
        .by(Rule::copyFirst);

        // Types
        Attribute expectedAttribute = node.type.attr("value");
        Attribute actualAttribute = node.initializer.attr("type");

        // Fixing tests in @testTemplateSimpleUsageTypes
        Object n = node.initializer;
        if (n instanceof FunCallNode) {

            // Casting
            FunCallNode funCallNode = (FunCallNode) n;

            // Reference to function
            if (funCallNode.function instanceof ReferenceNode) {

                ReferenceNode referenceNode = ((ReferenceNode) funCallNode.function);

                // Getting function node
                DeclarationContext declarationContext = scope.lookup(referenceNode.name);

                // Function not found
                if (declarationContext == null) {
                    // TODO throw error
                    return;
                }

                // Getting return type
                FunDeclarationNode funDeclarationNode = (FunDeclarationNode) declarationContext.declaration;
                TypeNode returnType = funDeclarationNode.returnType;
                boolean isReturnTemplateType = funDeclarationNode.isTemplateType(returnType);

                // Skip if not template type
                if (isReturnTemplateType) {

                    // Getting inferred return type in this context
                    TypeNode typeNode = funDeclarationNode.getInferredTemplateParameterTypeNode(returnType.contents(), funCallNode.template_arguments);

                    if (typeNode != null) {
                        actualAttribute = new Attribute(typeNode, "value");
                    }
                }

            }

        }

        R.rule()
        .using(expectedAttribute, actualAttribute)
        .by(r -> {
            Type expected = r.get(0);
            Type actual = r.get(1);

            if (!isAssignableTo(actual, expected))
                r.error(format(
                    "incompatible initializer type provided for variable `%s`: expected %s but got %s",
                    node.name, expected, actual),
                    node.initializer);
        });
    }

    // ---------------------------------------------------------------------------------------------

    private void templateTypeDeclaration (TemplateTypeDeclarationNode node)
    {
        scope.declare(node.name, node);
        R.set(node, "type", TypeType.INSTANCE);
        R.set(node, "declared", new TemplateType(node));
    }

    // ---------------------------------------------------------------------------------------------

    private void fieldDecl (FieldDeclarationNode node)
    {
        R.rule(node, "type")
        .using(node.type, "value")
        .by(Rule::copyFirst);
    }

    // ---------------------------------------------------------------------------------------------

    private void parameter (ParameterNode node)
    {
        R.set(node, "scope", scope);
        scope.declare(node.name, node); // scope pushed by FunDeclarationNode

        R.rule(node, "type")
        .using(node.type, "value")
        .by(Rule::copyFirst);
    }

    // ---------------------------------------------------------------------------------------------

    private void funDecl (FunDeclarationNode node)
    {
        // Preparing scope
        scope.declare(node.name, node);
        scope = new Scope(node, scope);

        // Setting scope
        R.set(node, "scope", scope);

        // Getting template types
        List<TemplateTypeDeclarationNode> templateTypes = node.templateParameters;

        // Filtering parameters to check at runtime
        boolean is_template_return_type = node.isTemplateType(node.returnType);
        //List<ParameterNode> parameterNodesFiltered = node.parameters.stream().filter((param) -> node.isTemplateType(param.type)).collect(Collectors.toList());
        List<ParameterNode> parameterNodes = node.parameters;

        // Checking parameters type
        Attribute[] dependencies = new Attribute[parameterNodes.size() + (is_template_return_type ? 0 : 1)];

        // Checking if return type is in template
        if (!is_template_return_type) {
            dependencies[0] = node.returnType.attr("value");
        }

        forEachIndexed(parameterNodes, (i, param) ->
            dependencies[i + (is_template_return_type ? 0 : 1)] = param.attr("type"));

        // Applying rule
        if (dependencies.length > 0) {
            R.rule(node, "type")
            .using(dependencies)
            .by (r -> {
                Type[] paramTypes = new Type[parameterNodes.size()];
                for (int i = 0; i < paramTypes.length; ++i)
                    paramTypes[i] = r.get(i + (is_template_return_type ? 0 : 1));


                r.set(0, new FunType(r.get(0), paramTypes));
            });
        }

        // Checking return type based on whether we're using a template identifier
        if (is_template_return_type) {
            R.rule()
            .using(node.block.attr("returns"), node.returnType.attr("value"))
            .by(r -> {
                boolean returns = r.get(0);
                Type returnType = r.get(1);
                if (!returns && !(returnType instanceof VoidType))
                    r.error("Missing return in function.", node);
                // NOTE: The returned value presence & type is checked in returnStmt().
            });
        } else {
            R.rule()
            .using(node.block.attr("returns"), node.returnType.attr("value"))
            .by(r -> {
                boolean returns = r.get(0);
                Type returnType = r.get(1);
                if (!returns && !(returnType instanceof VoidType))
                    r.error("Missing return in function.", node);
                // NOTE: The returned value presence & type is checked in returnStmt().
            });
        }

    }

    // ---------------------------------------------------------------------------------------------

    private void structDecl (StructDeclarationNode node) {
        scope.declare(node.name, node);
        R.set(node, "type", TypeType.INSTANCE);
        R.set(node, "declared", new StructType(node));
    }

    // endregion
    // =============================================================================================
    // region [Other Statements]
    // =============================================================================================

    private void ifStmt (IfNode node) {
        R.rule()
        .using(node.condition, "type")
        .by(r -> {
            Type type = r.get(0);
            if (!(type instanceof BoolType)) {
                r.error("If statement with a non-boolean condition of type: " + type,
                    node.condition);
            }
        });

        Attribute[] deps = getReturnsDependencies(list(node.trueStatement, node.falseStatement));
        R.rule(node, "returns")
        .using(deps)
        .by(r -> r.set(0, deps.length == 2 && Arrays.stream(deps).allMatch(r::get)));
    }

    // ---------------------------------------------------------------------------------------------

    private void whileStmt (WhileNode node) {
        R.rule()
        .using(node.condition, "type")
        .by(r -> {
            Type type = r.get(0);
            if (!(type instanceof BoolType)) {
                r.error("While statement with a non-boolean condition of type: " + type,
                    node.condition);
            }
        });
    }

    // ---------------------------------------------------------------------------------------------

    private void returnStmt (ReturnNode node)
    {
        R.set(node, "returns", true);

        FunDeclarationNode function = currentFunction();
        if (function == null) // top-level return
            return;

        // Checking if any template parameters involved here
        boolean check = involvesUninitializedTemplateParameter(node, scope);

        if (node.expression == null)
            R.rule()
            .using(function.returnType, "value")
            .by(r -> {
               Type returnType = r.get(0);
               if (!(returnType instanceof VoidType))
                   r.error("Return without value in a function with a return type.", node);
            });
        else
            R.rule()
            .using(function.returnType.attr("value"), node.expression.attr("type"))
            .by(r -> {

                Type formal = r.get(0);
                Type actual = r.get(1);

                // Needed in order to ensure that we can declare a return statement with a template type without
                // having to call the function (template <A,B> fun(a:Int):A { return a })
                formal = formal instanceof TemplateType ? ((TemplateType) formal).getTemplateTypeReference() : formal;

                if (formal instanceof VoidType)
                    r.error("Return with value in a Void function.", node);
                else if (check) { // Assignment can only be checked whilst walking
                    return;
                } else if (formal instanceof TemplateType) { // Allowing (template <A,B> fun(a:Int):A { return a })
                    return;
                } else if (!isAssignableTo(actual, formal)) {
                    r.errorFor(format(
                        "Incompatible return type, expected %s but got %s", formal, actual),
                        node.expression);
                }
            });
    }

    // ---------------------------------------------------------------------------------------------

    private FunDeclarationNode currentFunction()
    {
        Scope scope = this.scope;
        while (scope != null) {
            SighNode node = scope.node;
            if (node instanceof FunDeclarationNode)
                return (FunDeclarationNode) node;
            scope = scope.parent;
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------

    private boolean isReturnContainer (SighNode node) {
        return node instanceof BlockNode
            || node instanceof IfNode
            || node instanceof ReturnNode;
    }

    // ---------------------------------------------------------------------------------------------

    /** Get the depedencies necessary to compute the "returns" attribute of the parent. */
    private Attribute[] getReturnsDependencies (List<? extends SighNode> children) {
        return children.stream()
            .filter(Objects::nonNull)
            .filter(this::isReturnContainer)
            .map(it -> it.attr("returns"))
            .toArray(Attribute[]::new);
    }

    // endregion
    // =============================================================================================

    private List<Type> getTypes(List<TypeNode> typeNodes) {
        return typeNodes.stream().map($ -> $.getType()).collect(Collectors.toList());
    }


    // =============================================================================================
}