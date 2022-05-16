package norswap.sigh.ast;

import com.sun.org.apache.xpath.internal.operations.Bool;
import norswap.autumn.positions.Span;
import norswap.sigh.ast.base.TemplateTypeDeclarationNode;
import norswap.sigh.ast.base.TemplateTypeNode;
import norswap.sigh.ast.base.TemplateTypeReference;
import norswap.sigh.scopes.Scope;
import norswap.sigh.types.*;
import norswap.utils.Util;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class FunCallNode extends ExpressionNode
{
    public final ExpressionNode function;
    public final List<ExpressionNode> arguments;
    public List<TypeNode> template_arguments;
    public List<TemplateTypeReference> templateTypeReferences;
    public HashMap<String, TypeNode> templateArgsMap;

    @SuppressWarnings("unchecked")
    public FunCallNode (Span span, Object function, Object arguments) {
        super(span);
        this.function = Util.cast(function, ExpressionNode.class);
        this.arguments = Util.cast(arguments, List.class);
        this.templateTypeReferences = new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    public FunCallNode(Span span, Object function, Object arguments, Object template_arguments) {
        super(span);

        // Setting up data
        this.function = Util.cast(function, ExpressionNode.class);
        this.arguments = Util.cast(arguments, List.class);

        // Setting up template arguments
        this.template_arguments = (template_arguments == null) ? new ArrayList<>() : Util.cast(template_arguments, List.class);
        this.templateTypeReferences = new ArrayList<>();
        this.templateArgsMap = new HashMap<>();

        if (this.template_arguments != null) {
            for (int i = 0; i < this.template_arguments.size(); i++) {
                this.templateTypeReferences.add(new TemplateTypeReference());
            }
        }
    }

    public Type getReturnType(Scope scope) {

        DeclarationNode decl = scope.lookupLocal(this.function.contents());
        FunDeclarationNode funDecl = (FunDeclarationNode) decl;

        TypeNode returnTypeNode = ((FunDeclarationNode) decl).returnType;
        if (((FunDeclarationNode) decl).returnType instanceof TemplateTypeNode) {
            String returnTypeName = ((TemplateTypeNode) returnTypeNode).name;
            TemplateTypeReference templateTypeReference = this.templateTypeReferences.stream().filter(templateTypeReference1 -> templateTypeReference1.declarationNode.name.equals(returnTypeName)).findAny().orElse(null);
            Type returnType;
            if (templateTypeReference != null) {
                returnType = templateTypeReference.value;
            } else {
                TypeNode typeNode = this.templateArgsMap.get(returnTypeName);
                returnType = inferType(typeNode);
            }

            return returnType;
        }

        return null;
    }

    public Type inferType(TypeNode typeNode) {
        if (typeNode instanceof SimpleTypeNode) {
            switch (((SimpleTypeNode) typeNode).name) {
                case "Bool": {
                    return BoolType.INSTANCE;
                }
                case "Float": {
                    return FloatType.INSTANCE;
                }
                case "Int": {
                    return IntType.INSTANCE;
                }
                case "String": {
                    return StringType.INSTANCE;
                }
            }
        } else if (typeNode instanceof ArrayTypeNode) {
            return new ArrayType(inferType(typeNode));
        }

        throw new Error("Unsupported return type for template");
    }

    /**
     * Clears all types assigned to template parameters
     */
    public void clearTemplateParametersValue() {
        templateTypeReferences.clear();
        templateArgsMap.clear();
    }

    /**
     * Assigns a type to each template parameter
     * @param templateArgs
     */
    public void setTemplateTypeReferences(List<TypeNode> templateArgs, List<TemplateTypeDeclarationNode> declarationNodes, Type[] paramTypes) {

        // Clearing up template parameters
        clearTemplateParametersValue();

        // Skip if no template args
        if (templateArgs == null) return;
        int index = 0;

        // Map ParamType to TemplateArg
        for (TypeNode templateArg : templateArgs) {
            templateArgsMap.put(declarationNodes.get(index).name, templateArg);
            index++;
        }

        // Assigning the type to each template parameter
        index = 0;
        for (Type paramType : paramTypes) {

            if (!(paramType instanceof TemplateType)) continue;

            TemplateTypeDeclarationNode declarationNode = declarationNodes.stream().filter($ -> $.name.equals(paramType.name())).findFirst().get();
            int templateParameterIndex = declarationNodes.indexOf(declarationNode);
            TypeNode templateArg = templateArgs.get(templateParameterIndex);

            templateTypeReferences.add(new TemplateTypeReference());
            templateTypeReferences.get(index).value = templateArg.getType();
            templateTypeReferences.get(index).declarationNode = declarationNode;

            index++;
        }

    }

    @Override public String contents ()
    {
        // TODO show up template arguments here
        String args = arguments.size() == 0 ? "()" : "(...)";
        return function.contents() + args;
    }
}
