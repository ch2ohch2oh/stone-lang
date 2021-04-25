package stone.ast;

import java.util.List;

public class WhileStatement extends ASTList {
    public WhileStatement(List<ASTree> children) {
        super(children);
    }

    public ASTree condition() {
        return child(0);
    }

    public ASTree body() {
        return child(1);
    }

    @Override
    public String toString() {
        return "(while " + condition() + " " + body() + ")";
    }

}
