package norswap.sigh.ast;

public enum ArrayOp {
    DOTP("@");


    public final String string;

    ArrayOp (String string) {
        this.string = string;
    }
}
