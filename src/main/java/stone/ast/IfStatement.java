package stone.ast;

import java.util.List;

public class IfStatement extends ASTList {
    public IfStatement(List<ASTree> children) {
        super(children);
    }

    public ASTree condition() {
        return child(0);
    }

    public ASTree thenBlock() {
        return child(1);
    }

    public ASTree elseBlocK() {
        return numChildren() > 2 ? child(2) : null;
    }

    @Override
    public String toString() {
        return "(if " + condition() + " " + thenBlock() + " else " + elseBlocK() + ")";
    }
}
