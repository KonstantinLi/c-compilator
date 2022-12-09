package com.kpi.fict;

import java.util.*;

public class Parser {
    private static final Set<Token> DATA_TYPES = new HashSet<>() {{
       add(Token.INT);
       add(Token.CHAR);
       add(Token.UNSIGNED_CHAR);
       add(Token.VOID);
    }};

    private static final Set<Token> OPERATIONS = new HashSet<>() {{
       add(Token.PLUS);
       add(Token.MINUS);
    }};

    private static final Set<Token> LITERALS = new HashSet<>() {{
       add(Token.DECIMAL);
       add(Token.OCTAL);
       add(Token.HEXADEMICAL);
       add(Token.STRING);
       add(Token.CHARACTER);
    }};

    private static final Set<Token> LOGICAL = new HashSet<>() {{
        add(Token.AND);
        add(Token.OR);
    }};

    public static final Set<Token> COMPARE = new HashSet<>() {{
       add(Token.EQUAL);
       add(Token.NOT_EQUAL);
       add(Token.LESS);
       add(Token.LESS_EQUAL);
       add(Token.MORE);
       add(Token.MORE_EQUAL);
    }};

    private final Lexer lexer;
    private final Stack<String> bracketStack = new Stack<>();
    private Node root;
    private Token token;
    private String value;

    public Parser(String program) {
        lexer = new Lexer(program);
    }

    public void parse(String programName) {
        root = new Node(Token.PROGRAM, programName);

        while (token != Token.EOF) {

            if (token == null) {
                nextToken();
            } else if (token == Token.INCLUDE ||
                    token == Token.COMMENT ||
                    token == Token.MULTI_COMMENT) {
                root.addChild(new Node(token, value));
                nextToken();
            } else if (DATA_TYPES.contains(token)) {
                Token typeValue = token;
                nextToken();

                if (token == Token.ID) {
                    String idValue = value;
                    nextToken();

                    if (token == Token.LBRA) {
                        nextToken();

                        List<Node> variables = parameters();
                        Node parameters = new Node(Token.PARAMETERS);
                        parameters.addChildren(variables);

                        nextToken();

                        if (token == Token.LPAR) {
                            bracketStack.clear();
                            bracketStack.push("{");
                            List<Node> statements = new ArrayList<>();

                            nextToken();
                            while (!bracketStack.empty()) {
                                Node node = closeIfBracket();
                                if (node != null && node.type != Token.RPAR) {
                                    statements.add(node);
                                }
                            }
                            nextToken();

                            Node procedure = new Node(Token.PROCEDURE);
                            procedure.addChild(new Node(typeValue, idValue));
                            procedure.addChild(parameters);
                            procedure.addChildren(statements);

                            root.addChild(procedure);
                        }
                    }
                }
            } else {
                throw new RuntimeException("Include statement or procedure were expected at line " + lexer.line);
            }
        }
    }

    private Node closeIfBracket() {
        if ((token == Token.RPAR || token == Token.EOF) && !bracketStack.empty() && bracketStack.peek().equals("{")) {
            bracketStack.pop();
            return new Node(token);
        }

        return statement(1);
    }

    private Node statement(int level) {
        Node statement = null;

        if (token == Token.IF) {
            statement = new Node(Token.IF);
            nextToken();

            if (token == Token.LBRA) {
                Node condition = condition();
                statement.addChild(condition);
                nextToken();

                if (token == Token.LPAR) {
                    bracketStack.push("{");
                    List<Node> statements = new ArrayList<>();
                    while (token != Token.RPAR || bracketStack.size() != level + 1) {
                        nextToken();
                        statements.add(statement(level + 1));
                    }
                    bracketStack.pop();
                    nextToken();
                    statement.addChildren(statements);
                } else {
                    throw new RuntimeException("'{' was expected at line " + lexer.line);
                }
            } else {
                throw new RuntimeException("Condition was expected at line " + lexer.line);
            }

        } else if (token == Token.ELSE) {
            nextToken();
            if (token == Token.IF) {
                statement = statement(level);
                statement.setType(Token.ELSE_IF);
            } else if (token == Token.LPAR) {
                bracketStack.push("{");
                statement = new Node(Token.ELSE);
                List<Node> statements = new ArrayList<>();
                nextToken();

                while (token != Token.RPAR || bracketStack.size() != level + 1) {
                    statements.add(statement(level + 1));
                }
                bracketStack.pop();
                nextToken();
                statement.addChildren(statements);
            }

        } else if (token == Token.ID ||
                token == Token.RETURN ||
                DATA_TYPES.contains(token)) {
            return expression();
        } else if (token == Token.SEMICOLON) {
            nextToken();
        } else if (token != Token.RPAR && token != Token.EOF) {
            throw new RuntimeException("Unknown statement at line " + lexer.line);
        }

        return statement;
    }

    private Node condition() {
        Node condition = null;
        Node multipleCondition = null;

        while (token != Token.RBRA) {
            nextToken();

            if (token == Token.ID || LITERALS.contains(token)) {
                Node operator1 = new Node(token, value);
                nextToken();
                if (COMPARE.contains(token)) {
                    Node compare = new Node(token);
                    nextToken();
                    if (token == Token.ID || LITERALS.contains(token)) {
                        Node operator2 = new Node(token, value);

                        condition = compare;
                        condition.addChild(operator1);
                        condition.addChild(operator2);

                    } else {
                        throw new RuntimeException("Illegal condition at line " + lexer.line);
                    }

                } else {
                    throw new RuntimeException("Illegal condition at line " + lexer.line);
                }
            } else if (LOGICAL.contains(token) && condition != null) {
                multipleCondition = new Node(token);
                multipleCondition.addChild(condition);
                multipleCondition.addChild(condition());
            }
        }

        return multipleCondition != null ? multipleCondition : condition;
    }

    private Node expression() {
        if (token == Token.ID) {
            Node variable = new Node(token, value);
            nextToken();

            Node value;

            if (token == Token.ASSIGNMENT) {
                nextToken();

                if (token == Token.ID || LITERALS.contains(token)) {
                    value = new Node(token, this.value);
                    nextToken();

                    if (token == Token.SEMICOLON) {
                        return initialization(variable, value);
                    } else if (OPERATIONS.contains(token)) {
                        return calculation(variable, value);
                    } else if (token == Token.LBRA) {
                        nextToken();

                        List<Node> variables = parameters();
                        Node parameters = new Node(Token.PARAMETERS);
                        parameters.addChildren(variables);

                        nextToken();

                        if (token != Token.SEMICOLON) {
                            throw new RuntimeException("';' was expected at line " + lexer.line);
                        }

                        Node initialization = new Node(Token.INITIALIZATION);
                        Node function = new Node(Token.FUNCTION, value.value);

                        function.addChild(parameters);

                        initialization.addChild(variable);
                        initialization.addChild(function);

                        return initialization;
                    } else {
                        throw new RuntimeException("Unknown expression at line " + lexer.line);
                    }
                } else {
                    throw new RuntimeException("Unknown expression at line " + lexer.line);
                }
            } else if (token == Token.LBRA) {
                nextToken();
                List<Node> variables = parameters();
                Node parameters = new Node(Token.PARAMETERS);
                parameters.addChildren(variables);

                nextToken();

                if (token != Token.SEMICOLON) {
                    throw new RuntimeException("';' was expected at line " + lexer.line);
                }

                Node function = new Node(Token.FUNCTION, variable.value);
                function.addChild(parameters);

                return function;
            } else {
                throw new RuntimeException("Unknown expression at line " + lexer.line);
            }
        } else if (DATA_TYPES.contains(token)) {
            return declaration();
        } else if (token == Token.RETURN) {
            nextToken();
            if (token == Token.ID || LITERALS.contains(token)) {
                Node returnNode = new Node(Token.RETURN);
                returnNode.addChild(new Node(token, value));
                nextToken();

                if (token != Token.SEMICOLON) {
                    throw new RuntimeException("';' was expected at line " + lexer.line);
                }

                nextToken();

                return returnNode;

            } else {
                throw new RuntimeException("Illegal return expression at line " + lexer.line);
            }
        } else {
            throw new RuntimeException("Unknown expression at line " + lexer.line);
        }
    }

    private Node declaration() {
        Node declaration = new Node(Token.DECLARATION);
        Token variableType = token;
        nextToken();

        if (token == Token.ID) {
            Node variable = new Node(variableType, value);

            nextToken();
            if (token != Token.SEMICOLON) {
                throw new RuntimeException("';' was expected at line " + lexer.line);
            }

            declaration.addChild(variable);
            return declaration;
        } else {
            throw new RuntimeException("Unknown declaration at line " + lexer.line);
        }
    }

    private Node initialization(Node variable, Node value) {
        Node initialization = new Node(Token.INITIALIZATION);
        initialization.addChild(variable);
        initialization.addChild(value);

        return initialization;
    }

    private Node calculation(Node variable, Node operator1) {
        Node calculation = new Node(Token.CALCULATION);
        calculation.addChild(variable);
        Node operation = null, operator2 = null;

        while (token != Token.SEMICOLON) {
            if (token == Token.ID || LITERALS.contains(token)) {
                operator2 = new Node(token, value);
            } else if (OPERATIONS.contains(token)) {
                operation = new Node(token);
            } else {
                throw new RuntimeException("Unknown operation at line " + lexer.line);
            }

            if (operation != null && operator1 != null && operator2 != null) {
                operation.addChild(operator1);
                operation.addChild(operator2);
                calculation.addChild(operation);

                nextToken();

                if (token != Token.SEMICOLON) {
                    throw new RuntimeException("';' was expected at line " + lexer.line);
                }

                return calculation;
            }
            nextToken();
        }

        return null;
    }

    private List<Node> parameters() {
        List<Node> variables = new ArrayList<>();

        Token variableType = null;
        String variableValue = null;
        while (token != Token.RBRA) {
            if (LITERALS.contains(token)) {
                variableType = token;
                variableValue = value;
            } else if (DATA_TYPES.contains(token)) {
                variableType = token;
            } else {
                switch (token) {
                    case ID -> variableValue = value;
                    case COMMA -> {
                        variableType = null;
                        variableValue = null;
                    }
                    case EOF -> throw new RuntimeException("Close bracket not found at line" + lexer.line);
                }
            }

            if (variableValue != null) {
                variables.add(new Node(variableType, variableValue));
                variableType = null;
                variableValue = null;
            }

            nextToken();
        }

        return variables;
    }

    public Node getRoot() {
        return root;
    }

    public static void printStructure(Node root) {
        printNode(root, 0);
    }

    private static void printNode(Node node, int level) {
        System.out.println("\t".repeat(level) + node);
        for (Node child : node.getChildren()) {
            if (child != null) {
                printNode(child, level + 1);
            }
        }
    }

    private void nextToken() {
        lexer.nextToken();
        token = lexer.token;
        value = lexer.value;
    }

    public void setCharacter(Node node, char character) {
        if (node.type == Token.ID && node.value.equals("argv[1][0]")) {
            node.type = Token.CHARACTER;
            node.value = String.valueOf(character);
        }

        List<Node> children = node.getChildren();
        children.forEach(child -> this.setCharacter(child, character));
    }
}

