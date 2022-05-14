package norswap.sigh.ast;

import norswap.autumn.positions.Span;
import norswap.sigh.ast.base.TemplateTypeDeclarationNode;
import norswap.sigh.ast.base.TemplateTypeNode;
import norswap.sigh.ast.base.TemplateTypeReference;
import norswap.sigh.scopes.Scope;
import norswap.sigh.types.TemplateType;
import norswap.sigh.types.Type;
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
            TemplateTypeReference templateTypeReference = this.templateTypeReferences.stream().filter(templateTypeReference1 -> templateTypeReference1.declarationNode.name.equals(returnTypeName)).findAny().get();
            Type returnType = templateTypeReference.value;

            return returnType;
        }

        return null;
    }

    /**
     * Clears all types assigned to template parameters
     */
    public void clearTemplateParametersValue() {
        templateTypeReferences.clear();
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

        // Map ParamType to TemplateArg
        HashMap<String, TypeNode> paramToArg = new HashMap<>();

        // Assigning the type to each template parameter
        int index = 0;
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
