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

    public boolean equals(Object b) {
        if (b instanceof TemplateTypeReference) {
            boolean valueEqual = false;
            boolean nodeEqual = false;

            if (this.value == null && ((TemplateTypeReference) b).value == null) {
                valueEqual = true;
            } else if (this.value != null && ((TemplateTypeReference) b).value != null) {
                valueEqual = value.equals(((TemplateTypeReference) b).value);
            }

            if (this.declarationNode == null && ((TemplateTypeReference) b).declarationNode == null) {
                nodeEqual = true;
            } else if (this.declarationNode != null && ((TemplateTypeReference) b).declarationNode != null) {
                nodeEqual = declarationNode.equals(((TemplateTypeReference) b).declarationNode);
            }

            return valueEqual && nodeEqual;
        }
        else
            return false;
    }
}
