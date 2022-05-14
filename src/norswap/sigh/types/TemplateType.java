package norswap.sigh.types;

import norswap.sigh.ast.ArrayAccessNode;
import norswap.sigh.ast.SighNode;
import norswap.sigh.ast.base.TemplateTypeDeclarationNode;
import norswap.sigh.scopes.Scope;
import norswap.uranium.Reactor;

public final class TemplateType extends Type
{
    public final TemplateTypeDeclarationNode node;

    public TemplateType (TemplateTypeDeclarationNode node) {
        this.node = node;
    }

    @Override public String name() {
        return node.name();
    }

    @Override public boolean equals (Object o) {
        return this == o;
    }

    @Override public int hashCode () {
        return node.hashCode();
    }

    public Type getTemplateTypeReference(Reactor reactor) {

        Scope scope = reactor.get(this, "declared");

        return null;
    }

    public Type getTemplateTypeReference() {
        return this.node.value != null ? this.node.value : this;
    }

    public Type getTemplateTypeAccess(ArrayAccessNode node, ArrayType arrayType) {
        SighNode iterator = node;
        int depth = 0;

        while (iterator instanceof ArrayAccessNode) {
            iterator = ((ArrayAccessNode) iterator).array;
            depth++;
        }

        Type typeIterator = arrayType;

        int currentDepth = 0;
        while (currentDepth != depth) {
            typeIterator = ((ArrayType) typeIterator).componentType;
            currentDepth++;
        }

        return typeIterator;
    }

    private Type getTemplateTypeAccessReference(int depth) {
        if (this.node.value != null) {
            Type val = this.node.value;
            if (val instanceof ArrayType) {
                ArrayType iterator = (ArrayType) val;

                for (int i = 0; i<depth-1; i++) {
                    iterator = (ArrayType) iterator.componentType;
                }

                return iterator.componentType;
            } else {
                return val;
            }
        } else {
            return this;
        }
    }
}
