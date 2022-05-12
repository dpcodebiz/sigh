package norswap.sigh.ast;

import norswap.autumn.positions.Span;
import norswap.utils.Util;
import java.util.List;

public final class DotPrdExpression extends ExpressionNode
{
    public final ArrayLiteralNode left, right;
    public final ArrayOp op;


    public DotPrdExpression (Span span, Object left,Object op, Object right) {
        super(span);
        this.left =  Util.cast(left, ArrayLiteralNode.class);
        this.op = Util.cast(op, ArrayOp.class);
        this.right = Util.cast(right, ArrayLiteralNode.class);

    }

    @Override public String contents ()
    {
        String candidate = String.format("%s %s %s",
            left.contents(), op.string, right.contents());

        return candidate.length() <= contentsBudget()
            ? candidate
            : String.format("(?) %s (?)", op.string);
    }
}

