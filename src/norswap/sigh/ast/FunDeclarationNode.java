package norswap.sigh.ast;

import norswap.autumn.positions.Span;
import norswap.sigh.ast.base.TemplateTypeDeclarationNode;
import norswap.sigh.ast.base.TemplateTypeNode;
import norswap.sigh.types.IntType;
import norswap.sigh.types.TemplateType;
import norswap.sigh.types.Type;
import norswap.utils.Util;
import sun.java2d.pipe.SpanShapeRenderer.Simple;
import java.util.ArrayList;
import java.util.List;

public class FunDeclarationNode extends DeclarationNode
{
    public final String name;
    public final List<ParameterNode> parameters;
    public final TypeNode returnType;
    public final BlockNode block;
    public final List<TemplateTypeDeclarationNode> templateParameters;

    @SuppressWarnings("unchecked")
    public FunDeclarationNode
            (Span span, Object name, Object parameters, Object returnType, Object block) {
        super(span);

        this.name = Util.cast(name, String.class);
        this.parameters = Util.cast(parameters, List.class);
        this.returnType = returnType == null
            ? new SimpleTypeNode(new Span(span.start, span.start), "Void")
            : Util.cast(returnType, TypeNode.class);
        this.block = Util.cast(block, BlockNode.class);
        this.templateParameters = new ArrayList<>();

        return;
    }

    @SuppressWarnings("unchecked")
    public FunDeclarationNode
        (Span span, Object name, Object parameters, Object returnType, Object block, Object templateParameters) {
        super(span);

        // Setting data
        this.name = Util.cast(name, String.class);
        this.block = Util.cast(block, BlockNode.class);
        this.templateParameters = Util.cast(templateParameters, List.class);

        // Checking parameters if they have to be recast to template parameter type
        List<ParameterNode> parametersList = Util.cast(parameters, List.class);

        for (ParameterNode parameterNode : parametersList) {

            // Checking if parameter type is a template parameter (simple)
            if (parameterNode.type instanceof SimpleTypeNode) {

                // Getting type node
                SimpleTypeNode typeNode = Util.cast(parameterNode.type, SimpleTypeNode.class);

                // Updating parameter type
                parameterNode.type = convertSimpleTypeToTemplateType(typeNode);

            } else if (parameterNode.type instanceof ArrayTypeNode) { // (array)

                // Getting array base type
                ArrayTypeNode typeNode = Util.cast(parameterNode.type, ArrayTypeNode.class);

                convertBaseArrayTypeToTemplateType(typeNode);
            }
        }
        this.parameters = Util.cast(parameters, List.class);

        // Checking if return type is a template parameter
        if (returnType == null) {
            this.returnType = new SimpleTypeNode(new Span(span.start, span.start), "Void");
        } else if (returnType instanceof TemplateTypeNode) { // Corner case when providing a template type nod
            this.returnType = Util.cast(returnType, TemplateTypeNode.class);
        } else if (returnType instanceof ArrayTypeNode) {

            // Casting
            ArrayTypeNode returnTypeNode = Util.cast(returnType, ArrayTypeNode.class);

            // Converting base type to template type (if necessary)
            convertBaseArrayTypeToTemplateType(returnTypeNode);

            // Setting return type
            this.returnType = Util.cast(returnTypeNode, TypeNode.class);

        } else {
            // Casting to simple type node
            SimpleTypeNode returnTypeNode = Util.cast(returnType, SimpleTypeNode.class);

            this.returnType = Util.cast(convertSimpleTypeToTemplateType(returnTypeNode), TypeNode.class);
        }


        return;
    }

    /**
     * Updates the base type of the nested array to the template type matching
     * the template parameter
     * @param typeNode
     */
    private void convertBaseArrayTypeToTemplateType(ArrayTypeNode typeNode) {
        ArrayTypeNode parentTypeNode = typeNode;
        TypeNode typeIterator = typeNode.componentType;

        while (typeIterator instanceof ArrayTypeNode) {
            parentTypeNode = (ArrayTypeNode) typeIterator;
            typeIterator = ((ArrayTypeNode) typeIterator).componentType;
        }

        // Skip base type already instance of template type node
        if (typeIterator instanceof TemplateTypeNode) return;

        // Updating base type
        SimpleTypeNode baseTypeNode = Util.cast(typeIterator, SimpleTypeNode.class);
        parentTypeNode.componentType = convertSimpleTypeToTemplateType(baseTypeNode);

        return;
    }

    /**
     * Returns whether the given type node is actually a template type of this function
     * @param node
     * @return
     */
    public boolean isTemplateType(TypeNode node) {

        if (node instanceof TemplateTypeNode) return isTemplateType((TemplateTypeNode) node);
        if (node instanceof SimpleTypeNode) return isTemplateType((SimpleTypeNode) node);
        if (node instanceof ArrayTypeNode) return isTemplateType(getBaseTypeNode((ArrayTypeNode) node));

        return false;
    }

    /**
     * Returns whether the given type node is actually a template type
     * @param node
     * @return
     */
    public boolean isTemplateType(SimpleTypeNode node) {
        return isTemplateType(node.name);
    }

    /**
     * Returns whether the given type node is actually a template type
     * @param node
     * @return
     */
    public boolean isTemplateType(TemplateTypeNode node) {
        return isTemplateType(node.name);
    }

    /**
     * Returns whether the given type is actually a template type
     * @param typeNodeName
     * @return
     */
    public boolean isTemplateType(String typeNodeName) {

        // Iterating through all template parameters
        for (TemplateTypeDeclarationNode templateParameter : templateParameters) {
            if (templateParameter.name.equals(typeNodeName))
                return true;
        }

        return false;
    }

    /**
     * Returns the base type node of an array type node
     * @param node
     * @return
     */
    public TypeNode getBaseTypeNode(ArrayTypeNode node) {
        TypeNode typeIterator = node.componentType;

        while (typeIterator instanceof ArrayTypeNode) {
            typeIterator = ((ArrayTypeNode) typeIterator).componentType;
        }

        return typeIterator;
    }

    /**
     * Returns the template type node associated to the simple type node if template parameter found
     * @param node
     * @return
     */
    private TypeNode convertSimpleTypeToTemplateType(SimpleTypeNode node) {
        return (isTemplateType(node)) ? new TemplateTypeNode(node) : node;
    }

    /**
     * Returns the type inferred for the given template parameter on a potential function call
     * @param templateParameter
     * @param templateArguments
     * @return
     */
    public TypeNode getInferredTemplateParameterTypeNode(TemplateTypeDeclarationNode templateParameter, List<TypeNode> templateArguments) {

        // Template parameters
        TemplateTypeDeclarationNode foundTemplateType;
        int index = 0;

        // Finding corresponding template parameter
        for (TemplateTypeDeclarationNode parameter : templateParameters) {

            if (parameter == templateParameter) {
                break;
            }

            index++;
        }

        return templateArguments.get(index);
    }

    /**
     * Returns the type inferred for the given template parameter on a potential function call
     * @param parameterName
     * @param templateArguments
     * @return
     */
    public TypeNode getInferredTemplateParameterTypeNode(String parameterName, List<TypeNode> templateArguments) {

        // Finding parameter
        for (TemplateTypeDeclarationNode parameter : templateParameters) {
            if (parameter.name.equals(parameterName)) {
                return getInferredTemplateParameterTypeNode(parameter, templateArguments);
            }
        }

        return null;
    }

    /**
     * Returns the type inferred for the given template parameter on a potential function call
     * @param templateParameter
     * @param templateArguments
     * @return
     */
    public Type getInferredTemplateParameterType(TemplateTypeDeclarationNode templateParameter, List<Type> templateArguments) {

        // Template parameters
        TemplateTypeDeclarationNode foundTemplateType;
        int index = 0;

        // Finding corresponding template parameter
        for (TemplateTypeDeclarationNode parameter : templateParameters) {

            if (parameter == templateParameter) {
                break;
            }

            index++;
        }

        return templateArguments.get(index);
    }

    /**
     * Returns the type inferred for the given template parameter on a potential function call
     * @param parameterName
     * @param templateArguments
     * @return
     */
    public Type getInferredTemplateParameterType(String parameterName, List<Type> templateArguments) {

        // Finding parameter
        for (TemplateTypeDeclarationNode parameter : templateParameters) {
            if (parameter.name.equals(parameterName)) {
                return getInferredTemplateParameterType(parameter, templateArguments);
            }
        }

        return null;
    }

    @Override public String name () {
        return name;
    }

    @Override public String contents () {
        return "fun " + name;
    }

    @Override public String declaredThing () {
        return "function";
    }
}
