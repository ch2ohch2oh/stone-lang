package stone;

import stone.ast.ASTLeaf;
import stone.ast.ASTList;
import stone.ast.ASTree;

import java.io.Reader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * The structure of the sub-classes is shown in fig 17.1 in the book.
 */
public class Parser {

    protected static abstract class Element {
        protected abstract void parse(Lexer lexer, List<ASTree> res) throws ParseException;
        protected abstract boolean match(Lexer lexer) throws ParseException;
    }

    protected static class Tree extends Element {
        protected Parser parser;
        protected Tree(Parser p) {
            parser = p;
        }

        @Override
        protected void parse(Lexer lexer, List<ASTree> res) throws ParseException {
            res.add(parser.parse(lexer));
        }

        @Override
        protected boolean match(Lexer lexer) throws ParseException {
            return parser.match(lexer);
        }
    }

    protected static class OrTree extends Element {
        protected Parser [] parsers;
        protected OrTree(Parser [] p) {
            parsers = p;
        }

        @Override
        protected void parse(Lexer lexer, List<ASTree> res) throws ParseException {
            Parser p = choose(lexer);
            if(p == null) {
                throw new ParseException(lexer.peek(0));
            } else {
                res.add(p.parse(lexer));
            }
        }

        @Override
        protected boolean match(Lexer lexer) throws ParseException {
            return choose(lexer) != null;
        }

        protected Parser choose(Lexer lexer) throws ParseException {
            for(Parser p: parsers) {
                if(p.match(lexer)) {
                    return p;
                }
            }
            return null;
        }

        /**
         * TODO: this is awkward implementation
         * @param p
         */
        protected void insert(Parser p) {
            Parser [] newParsers = new Parser[parsers.length + 1];
            newParsers[0] = p;
            System.arraycopy(parsers, 0, newParsers, 1, parsers.length);
            parsers = newParsers;
        }
    }

    protected static class Repeat extends Element {
        protected Parser parser;
        protected boolean onlyOnce;
        protected Repeat(Parser p, boolean once) {
            parser = p;
            onlyOnce = once;
        }

        @Override
        protected void parse(Lexer lexer, List<ASTree> res) throws ParseException {
            while(parser.match(lexer)) {
                ASTree t = parser.parse(lexer);
                if(t.getClass() != ASTList.class || t.numChildren() > 0) {
                    res.add(t);
                }
                if(onlyOnce) {
                    break;
                }
            }
        }

        @Override
        protected boolean match(Lexer lexer) throws ParseException {
            return parser.match(lexer);
        }
    }

    protected static abstract class AToken extends Element {
        protected Factory factory;
        protected AToken(Class<? extends ASTLeaf> type) {
            if(type == null) {
                type = ASTLeaf.class;
            }
            factory = factory.get(type, Token.class);
        }

        @Override
        protected void parse(Lexer lexer, List<ASTree> res) throws ParseException {
            Token t = lexer.read();
            if(test(t)) {
                ASTree leaf = factory.make(t);
                res.add(leaf);
            } else {
                throw new ParseException(t);
            }
        }

        @Override
        protected boolean match(Lexer lexer) throws ParseException {
            return test(lexer.peek(0));
        }

        protected abstract boolean test(Token t);
    }

    protected static class IdToken extends AToken {
        HashSet<String> reserved;

        public IdToken(Class<? extends ASTLeaf> type, HashSet<String> reserved) {
            super(type);
            this.reserved = reserved;
            if(this.reserved == null) {
                this.reserved = new HashSet<>();
            }
        }

        @Override
        protected boolean test(Token t) {
            return t.isIdentifier() && !reserved.contains(t.getText());
        }
    }

    protected static class NumToken extends AToken {
        public NumToken(Class<? extends ASTLeaf> type) {
            super(type);
        }

        @Override
        protected boolean test(Token t) {
            return t.isNumber();
        }
    }
    protected static class StrToken extends AToken {
        public StrToken(Class<? extends ASTLeaf> type) {
            super(type);
        }

        @Override
        protected boolean test(Token t) {
            return t.isString();
        }
    }

    protected static class Leaf extends Element {
        protected String [] tokens;

        public Leaf(String[] tokens) {
            this.tokens = tokens;
        }

        @Override
        protected void parse(Lexer lexer, List<ASTree> res) throws ParseException {
            Token t = lexer.read();
            if(t.isIdentifier()) {
                for(String token : tokens) {
                    if(token.equals(t.getText())) {
                        find(res, t);
                        return;
                    }
                }
            }
            if(tokens.length > 0) {
                throw new ParseException(tokens[0] + " expected.", t);
            } else {
                throw new ParseException(t);
            }
        }

        /**
         * Why is this function named `find`?
         * @param res
         * @param t
         */
        protected void find(List<ASTree> res, Token t) {
            res.add(new ASTLeaf(t));
        }

        @Override
        protected boolean match(Lexer lexer) throws ParseException {
            Token t = lexer.peek(0);
            if(t.isIdentifier()) {
                for(String token: tokens) {
                    if(token.equals(t.getText())) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    protected static class Skip extends Leaf {
        public Skip(String[] tokens) {
            super(tokens);
        }

        @Override
        protected void find(List<ASTree> res, Token t) {
        }
    }

    public static class Precedence {
        int value;
        boolean leftAssoc;

        public Precedence(int value, boolean leftAssoc) {
            this.value = value;
            this.leftAssoc = leftAssoc;
        }
    }

    public static class Operators extends HashMap<String, Precedence> {
        public static boolean LEFT = true;
        public static boolean RIGHT = false;
        public void add(String name, int prec, boolean leftAssoc) {
            put(name, new Precedence(prec, leftAssoc));
        }
    }

    protected static class Expr extends Element {
        protected Factory factory;
        protected Operators ops;
        protected Parser factor;

        public Expr(Class<? extends ASTree> className, Parser factor, Operators ops) {
            this.factory = Factory.getForASTList(className);
            this.ops = ops;
            this.factor = factor;
        }

        @Override
        protected void parse(Lexer lexer, List<ASTree> res) throws ParseException {
            ASTree right = factor.parse(lexer);
            Precedence prec;
            while((prec = nextOperator(lexer)) != null) {
                right = doShift(lexer, right, prec.value);
            }
            res.add(right);
        }

        private ASTree doShift(Lexer lexer, ASTree left, int prec) throws ParseException {
            ArrayList<ASTree> list = new ArrayList<>();
            list.add(left);
            list.add(new ASTLeaf(lexer.read()));
            ASTree right = factor.parse(lexer);
            Precedence next;
            while((next = nextOperator(lexer)) != null && rightIsExpr(prec, next)) {
                right = doShift(lexer, right, next.value);
            }
            list.add(right);
            return factory.make(list);
        }

        private Precedence nextOperator(Lexer lexer) throws ParseException {
            Token t = lexer.peek(0);
            if(t.isIdentifier()) {
                return ops.get(t.getText());
            } else {
                return null;
            }
        }

        private static boolean rightIsExpr(int prec, Precedence nextPrec) {
            if(nextPrec.leftAssoc) {
                return prec < nextPrec.value;
            } else {
                return prec <= nextPrec.value;
            }
        }
        @Override
        protected boolean match(Lexer lexer) throws ParseException {
            return factor.match(lexer);
        }
    }

    public static final String factoryName = "create";

    /**
     * What does this class do?
     */
    protected static abstract class Factory {
        protected abstract ASTree make0(Object arg) throws Exception;

        protected ASTree make(Object arg) {
            try {
                return make0(arg);
            } catch (IllegalArgumentException e1) {
                throw e1;
            } catch (Exception e2) {
                throw new RuntimeException(e2);
            }
        }

        protected static Factory getForASTList(Class<? extends ASTree> className) {
            Factory f = get(className, List.class);
            if(f == null) {
                f = new Factory() {
                    @Override
                    protected ASTree make0(Object arg) throws Exception {
                        List<ASTree> results = (List<ASTree>)arg;
                        if(results.size() == 1) {
                            return results.get(0);
                        } else {
                            return new ASTList(results);
                        }
                    }
                };
            }
            return f;
        }

        protected static Factory get(Class<? extends ASTree> className, Class<?> argType) {
            if(className == null) {
                return null;
            }
            try {
                final Method m = className.getMethod(factoryName, new Class<?>[] {argType});
                return new Factory() {
                    @Override
                    protected ASTree make0(Object arg) throws Exception {
                        return (ASTree) m.invoke(null, arg);
                    }
                };
            } catch (NoSuchMethodException e) {}
            try {
                final Constructor<? extends ASTree> c = className.getConstructor(argType);
                return new Factory() {
                    @Override
                    protected ASTree make0(Object arg) throws Exception {
                        return c.newInstance(arg);
                    }
                };
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }
    }

    protected List<Element> elements;
    protected Factory factory;

    public Parser(Class<? extends ASTree> className) {
        reset(className);
    }

    protected Parser(Parser p) {
        elements = p.elements;
        factory = p.factory;
    }

    public ASTree parse(Lexer lexer) throws ParseException {
        ArrayList<ASTree> results = new ArrayList<>();
        for(Element e : elements) {
            e.parse(lexer, results);
        }
        return factory.make(results);
    }

    protected boolean match(Lexer lexer) throws ParseException {
        if(elements.size() == 0) {
            return true;
        } else {
            Element e = elements.get(0);
            return e.match(lexer);
        }
    }

    public static Parser rule() {
        return rule(null);
    }

    public static Parser rule(Class<? extends ASTree> className) {
        return new Parser(className);
    }

    public Parser reset() {
        elements = new ArrayList<Element>();
        return this;
    }

    public Parser reset(Class<? extends ASTree> className) {
        elements = new ArrayList<>();
        factory = Factory.getForASTList(className);
        return this;
    }

    public Parser number() {
        return number(null);
    }

    public Parser number(Class<? extends ASTLeaf> className) {
        elements.add(new NumToken(className));
        return this;
    }

    public Parser identifier(HashSet<String> reserved) {
        return identifier(null, reserved);
    }

    public Parser identifier(Class<? extends ASTLeaf> className,
                             HashSet<String> reserved) {
        elements.add(new IdToken(className, reserved));
        return this;
    }

    public Parser string() {
        return string(null);
    }

    public Parser string(Class<? extends ASTLeaf> className) {
        elements.add(new StrToken(className));
        return this;
    }

    public Parser token(String... pat) {
        elements.add(new Leaf((pat)));
        return this;
    }

    public Parser sep(String... pat) {
        elements.add(new Skip((pat)));
        return this;
    }

    public Parser ast(Parser p) {
        elements.add(new Tree(p));
        return this;
    }

    public Parser or(Parser... p) {
        elements.add(new OrTree(p));
        return this;
    }

    public Parser maybe(Parser p) {
        Parser p2 = new Parser(p);
        p2.reset();
        elements.add(new OrTree(new Parser [] {p, p2}));
        return this;
    }

    public Parser option(Parser p) {
        elements.add(new Repeat(p, true));
        return this;
    }

    public Parser repeat(Parser p) {
        elements.add(new Repeat(p, false));
        return this;
    }

    public Parser expression(Parser subexpr, Operators ops) {
        elements.add(new Expr(null, subexpr, ops));
        return this;
    }

    public Parser expression(Class<? extends ASTree> className, Parser subexp, Operators ops) {
        elements.add(new Expr(className, subexp, ops));
        return this;
    }

    public Parser insertChoice(Parser p) {
        Element e = elements.get(0);
        if(e instanceof OrTree) {
            ((OrTree)e).insert(p);
        } else {
            Parser otherwise = new Parser(this);
            reset(null);
            or(p, otherwise);
        }
        return this;
    }
}
