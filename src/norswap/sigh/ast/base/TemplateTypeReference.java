package norswap.sigh.ast.base;

import norswap.sigh.types.Type;

public final class TemplateTypeReference
{
    public Type value;
    public TemplateTypeDeclarationNode declarationNode;

    public TemplateTypeReference () {
        this.value = null;
        this.declarationNode = null;
    }

    public void clear() {
        this.value = null;
        this.declarationNode = null;
    }

}
