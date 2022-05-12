package norswap.sigh.ast;

import norswap.autumn.positions.Span;
import norswap.utils.Util;
import java.util.List;

public final class DegExpression extends ExpressionNode
{
    public final ExpressionNode left, right;
    public final ArrayOp2 op;


    public DegExpression (Span span, Object left,Object op, Object right) {
        super(span);
        this.left =  Util.cast(left, ExpressionNode.class);
        this.op = Util.cast(op, ArrayOp2.class);
        this.right = Util.cast(right, ExpressionNode.class);

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

