package com.kpi.fict;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Compiler {

    private enum Command {
        MOV, ADD, ADC, SUB, SBB, CMP, JMP, JE,
        JNE, JG, JL, JGE, JLE, INC, DEC, PUSH,
        POP, INVOKE, INCLUDE, INCLUDE_LIB, RETURN, CALL
    }

    private enum Data {
        DB(1), DW(2), DD(4), DF(6), DQ(8), DT(10);
        final int size;
        Data(int size) {
            this.size = size;
        }
    }

    private enum Register {
        EAX, EBX, ECX, EDX, EBP, ESP, EDI, ESI
    }

    private class Procedure {
        final Node node;
        final String name;
        final List<String> params;
        final List<String> directives = new ArrayList<>();

        public Procedure(Node node, String name, List<String> params) {
            this.node = node;
            this.name = name;
            this.params = params;
        }

        void addDirective(String directive) {
            directives.add(directive);
        }

        void addDirectives(Collection<String> directives) {
            this.directives.addAll(directives);
        }
    }

    private class Condition {
        final Command command;
        final String operand1, operand2;
        final List<String> directives = new ArrayList<>();

        public Condition(Command command, String operand1, String operand2) {
            this.command = command;
            this.operand1 = operand1;
            this.operand2 = operand2;
        }
    }

    private class Variable {
        Data type;
        final String name;

        public Variable(String name) {
            this.name = name;
        }

        public Variable(Data type, String name) {
            this.type = type;
            this.name = name;
        }

        public void setType(Data type) {
            this.type = type;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj.getClass() != this.getClass()) {
                return false;
            }

            return ((Variable) obj).name.equals(this.name);
        }
    }

    private class Constant {
        final Data type;
        final String name;
        final String value;
        final boolean isArray;

        public Constant(Data type, String name, String value, boolean isArray) {
            this.type = type;
            this.name = name;
            this.value = value;
            this.isArray = isArray;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj.getClass() != this.getClass()) {
                return false;
            }

            return ((Constant) obj).name.equals(this.name);
        }
    }

    private final Node root;
    private final Set<Variable> variables = new HashSet<>();
    private final Set<Constant> constants = new HashSet<>();
    private final Set<Procedure> procedures = new HashSet<>();
    private final List<String> program = new ArrayList<>();

    private final Map<Token, Command> JUMP_COMMANDS = new HashMap<>() {{
       put(Token.EQUAL, Command.JE);
       put(Token.NOT_EQUAL, Command.JNE);
       put(Token.LESS, Command.JL);
       put(Token.MORE, Command.JG);
       put(Token.LESS_EQUAL, Command.JLE);
       put(Token.MORE_EQUAL, Command.JGE);
    }};

    private final Map<Token, Command> MATH_COMMANDS = new HashMap<>() {{
       put(Token.PLUS, Command.ADD);
       put(Token.MINUS, Command.SUB);
    }};

    public Compiler(Node root) {
        this.root = root;
    }

    public void compile() {
        initConfiguration();
        compile(root);
        constants();
        variables();
    }

    private void compile(Node node) {
        if (node.type == Token.PROGRAM) {
            List<Node> children = node.getChildren();
            children.forEach(this::compile);
        } else if (node.type == Token.PROCEDURE) {
            List<Node> children = node.getChildren();
            String name = children.get(0).value;

            Procedure procedure = new Procedure(node, name, children.get(1).getChildren().stream().map(node1 -> node1.value).toList());
            procedures.add(procedure);
            addVariables(children.get(1));

            directives(procedure, children.subList(2, children.size()));
            int insertIndex = IntStream.range(0, program.size())
                    .filter(index -> program.get(index).equals(".code"))
                    .findAny()
                    .getAsInt() + 1;

            if (name.equals("main")) {
                program.addAll(procedure.directives);
                program.add("\n");
            } else {
                program.addAll(insertIndex, procedure.directives);
                program.add(insertIndex + procedure.directives.size(), "\n");
            }

        } else if (node.type == Token.IF) {
            Node compare = node.getChild();
            if (compare.type == Token.AND || compare.type == Token.OR) {

            } else if (JUMP_COMMANDS.containsKey(compare.type)) {
                Command jump = JUMP_COMMANDS.get(compare.type);

            }
        } else if (node.type == Token.ELSE_IF) {

        } else if (node.type == Token.ELSE) {

        } else if (node.type == Token.DECLARATION) {
            declaration(node);
        } else if (node.type == Token.CALCULATION) {

        } else if (node.type == Token.INITIALIZATION) {
            initialization(node);
        } else if (node.type == Token.RETURN) {

        } else if (node.type == Token.FUNCTION) {
            if (!node.value.equals("printf")) {
                throw new RuntimeException("Unknown function " + node.value);
            }
            program.add(printf(node));
        }
    }

    private void directives(Procedure procedure, List<Node> directives) {
        List<String> commands = new ArrayList<>();

        int insertLine = 0;
        if (!procedure.name.equals("main")) {
            commands.add(procedure.name + " proc");
            commands.add("push ebp");
            commands.add("mov ebp, esp");

            insertLine += 3;

            int jumpCount = jumpCount(procedure.node);
            Map<String, Register> registers = new HashMap<>();

            for (int i = 0; i < procedure.params.size(); i++) {
                Register register = Register.values()[i];

                commands.add(String.format("mov %s, [ebp+%d]", register, 8 + 4 * i));
                registers.put(procedure.params.get(i), register);
                insertLine++;
            }

            int currentJump = 0;
            for (Node node : directives) {
                List<Node> children = node.getChildren();
                if (node.type == Token.IF || node.type == Token.ELSE_IF) {
                    Node conditionNode = children.get(0);
                    currentJump = multiCondition(procedure,
                            conditionNode,
                            children.subList(1, children.size()),
                            registers,
                            commands,
                            currentJump,
                            insertLine);

                    insertLine += 2;

                } else if (node.type == Token.ELSE) {
                    List<String> internalCommands = new ArrayList<>();
                    for (Node child : children) {
                        if (child.type == Token.CALCULATION) {
                            internalCommands.addAll(calculation(child, registers));
                        } else if (child.type == Token.INITIALIZATION) {
                            internalCommands.addAll(initialization(node));
                        } else if (child.type == Token.DECLARATION) {
                            declaration(node);
                        } else if (child.type == Token.RETURN) {
                            Node value = child.getChild();
                            if (value.type != Token.ID) {
                                internalCommands.add("mov eax, " + value.value);
                            }
                            internalCommands.add("pop ebp");
                            internalCommands.add("ret");
                        }
                    }

                    commands.addAll(insertLine, internalCommands);
                    int finalCurrentJump = currentJump;
                    commands.addAll(
                            IntStream.range(0, commands.size())
                                    .filter(index -> commands.get(index).contains("@" + procedure.name + (finalCurrentJump - 1)))
                                    .findAny()
                                    .getAsInt() + 1,
                            internalCommands);
                }
            }

            commands.add(procedure.name + " endp");
            procedure.addDirectives(commands);

        } else {
            commands.add("main:");
            insertLine = 1;

            for (Node node : directives) {
                if (node.type == Token.DECLARATION) {
                    declaration(node);
                } else if (node.type == Token.INITIALIZATION) {
                    List<String> initCommands = initialization(node);
                    commands.addAll(initCommands);
                    insertLine += initCommands.size();

                } else if (node.type == Token.ELSE) {
                    int currentJump = 0;
                    List<Node> children = node.getChildren();


                    for (Node child : children) {
                        List<Node> nodes = child.getChildren();

                        if (child.type == Token.INITIALIZATION) {
                            List<String> initCommands = initialization(child);
                            commands.addAll(initCommands);
                            insertLine += initCommands.size();

                        } else if (child.type == Token.FUNCTION && child.value.equals("printf")) {
                            commands.add(printf(child));

                        } else if (child.type == Token.IF || child.type == Token.ELSE_IF) {
                            Node conditionNode = nodes.get(0);

                            currentJump = multiCondition(procedure,
                                    conditionNode,
                                    nodes.subList(1, child.getChildren().size()),
                                    new HashMap<>(),
                                    commands,
                                    currentJump,
                                    insertLine);

                            insertLine += 2;
                        } else if (child.type == Token.ELSE) {
                            List<String> internalCommands = new ArrayList<>();
                            for (Node node1 : nodes) {
                                if (node1.type == Token.CALCULATION) {
                                    internalCommands.addAll(calculation(child, new HashMap<>()));
                                } else if (node1.type == Token.INITIALIZATION) {
                                    internalCommands.addAll(initialization(node1));
                                } else if (node1.type == Token.DECLARATION) {
                                    declaration(node1);
                                } else if (node1.type == Token.RETURN) {
                                    Node value = node1.getChild();
                                    if (value.type != Token.ID) {
                                        internalCommands.add("mov eax, " + value.value);
                                    }
                                } else if (node1.type == Token.FUNCTION && node1.value.equals("printf")) {
                                    internalCommands.add(printf(node1));
                                }
                            }

                            commands.addAll(insertLine + 1, internalCommands);
                        }
                    }
                }
            }

            commands.add("@end:");
            commands.add("invoke ExitProcess, 0");
            commands.add("end main");

            procedure.addDirectives(commands);
            insertLine = 0;
        }
    }

    private int multiCondition(Procedure procedure,
                                Node conditionNode,
                                List<Node> nodes,
                                Map<String, Register> registers,
                                List<String> commands,
                                int currentJump,
                                int currentLine) {
        List<String> internalCommands = new ArrayList<>();

        if (conditionNode.type == Token.AND || conditionNode.type == Token.OR) {
            List<Node> conditions = conditionNode.getChildren();
            List<String> trueDirectives = new ArrayList<>();

            for (Node condChild : conditions) {
                String jump = "@" + procedure.name + currentJump++;

                List<Node> operators = condChild.getChildren();
                if (operators.stream().allMatch(oper -> oper.type == Token.ID)) {
                    registers.put(operators.get(1).value, Register.EAX);
                }

                Condition condition = condition(condChild, jump, nodes, registers);

                internalCommands.add(String.format("cmp %s, %s",
                        registers.containsKey(condition.operand1)
                                ? registers.get(condition.operand1)
                                : condition.operand1,
                        registers.containsKey(condition.operand2)
                                ? registers.get(condition.operand2)
                                : condition.operand2
                ));

                internalCommands.add(String.format("%s %s", condition.command, jump));
                internalCommands.add(jump + ":");

                if (trueDirectives.isEmpty()) {
                    trueDirectives.addAll(condition.directives);
                }
            }

            internalCommands.addAll(trueDirectives);

        } else {
            String jump = "@" + procedure.name + currentJump;
            Condition condition = condition(conditionNode, jump, nodes, registers);

            List<Node> operators = conditionNode.getChildren();

            if (registers.containsKey(operators.get(1).value)) {
                internalCommands.add(String.format("mov %s, %s",
                        registers.get(operators.get(1).value),
                        operators.get(1).value));
            }

            internalCommands.add(String.format("cmp %s, %s",
                    registers.containsKey(condition.operand1)
                            ? registers.get(condition.operand1)
                            : condition.operand1,
                    registers.containsKey(condition.operand2)
                            ? registers.get(condition.operand2)
                            : condition.operand2
            ));

            internalCommands.add(String.format("%s @%s",
                    JUMP_COMMANDS.get(conditionNode.type),
                    procedure.name + currentJump++));

            internalCommands.add(jump + ":");
            internalCommands.addAll(condition.directives);
        }

        if (procedure.name.equals("main")) {
            internalCommands.add("jump @end");
        }
        commands.addAll(currentLine, internalCommands);

        return currentJump;
    }

    private Condition condition(Node conditionNode, String jump, List<Node> nodes, Map<String, Register> registers) {
        List<Node> operands = conditionNode.getChildren();

        if (operands.stream().allMatch(oper -> oper.type == Token.ID)) {
            registers.put(operands.get(1).value, Register.EAX);
        }

        Condition condition = new Condition(JUMP_COMMANDS.get(conditionNode.type),
                operands.get(0).value,
                operands.get(1).value);

        List<String> directives = new ArrayList<>();
        for (Node node : nodes) {
            if (node.type == Token.CALCULATION) {
                directives.addAll(calculation(node, registers));
            } else if (node.type == Token.INITIALIZATION) {
                directives.addAll(initialization(node));
            } else if (node.type == Token.DECLARATION) {
                declaration(node);
            } else if (node.type == Token.RETURN) {
                directives.add("pop ebp");
                directives.add("ret");
            } else if (node.type == Token.FUNCTION && node.value.equals("printf")) {
                directives.add(printf(node));
            }
        }

        condition.directives.addAll(directives);
        return condition;
    }

    private List<String> calculation(Node node, Map<String, Register> registers) {
        List<String> directives = new ArrayList<>();
        List<Node> children = node.getChildren();

        Variable var = new Variable(children.get(0).value);
        addVariableIfAbsent(var);

        Node operation = children.get(1);
        Node operand1 = operation.getChildren().get(0);
        Node operand2 = operation.getChildren().get(1);

        directives.add(String.format("mov %s, %s",
                var.name,
                registers.containsKey(operand1.value)
                        ? registers.get(operand1.value)
                        : operand1.value
        ));

        directives.add(String.format("%s %s, %s",
                MATH_COMMANDS.get(operation.type),
                var.name,
                registers.containsKey(operand2.value)
                        ? registers.get(operand2.value)
                        : operand2.value
        ));

        directives.add("mov eax, " + var.name);

        return directives;
    }

    private void addVariables(Node params) {
        List<Node> nodes = params.getChildren();
        for (Node node : nodes) {
            String name = node.value;
            if (!name.equals("argc") && !name.equals("**argv")) {
                Variable var = new Variable(Data.DD, name);
                addVariableIfAbsent(var);
            }
        }
    }

    private void declaration(Node node) {
        String name = node.getChildren().get(0).value;
        Variable var = new Variable(Data.DD, name);
        addVariableIfAbsent(var);
    }

    private List<String> initialization(Node node) {
        List<String> directives = new ArrayList<>();
        List<Node> children = node.getChildren();
        String name = children.get(0).value;
        Variable var = getVariable(name);

        if (var == null) {
            throw new RuntimeException(String.format("Variable %s hasn't been declared", name));
        }

        Node value = children.get(1);

        if (value.type == Token.FUNCTION) {
            directives.addAll(function(value));
            directives.add(String.format("mov %s, eax", name));

        } else {
            directives.add(String.format("mov %s, %s", name, value.type == Token.CHARACTER ? "'" + value.value + "'" : value.value));
        }

        return directives;
    }

    private List<String> function(Node node) {
        List<String> push = new ArrayList<>();
        String name = node.value;

        if (procedures.stream().map(proc -> proc.name).noneMatch(name1 -> name1.equals(name))) {
            throw new RuntimeException("Unknown procedure " + name);
        }

        for (Node child : node.getChildren().get(0).getChildren()) {
            if (child.type == Token.ID && !variables.contains(new Variable(child.value))) {
                throw new RuntimeException(String.format("Variable %s hasn't been declared", child.value));
            }
            push.add("push " + child.value);
        }

        push.add("call " + name);

        return push;
    }

    private String printf(Node node) {
        List<Node> params = node.getChild().getChildren();
        try {
            if (params.get(0).type != Token.STRING) {
                throw new RuntimeException("Illegal arguments format for procedure MessageBoxA");
            }

            String message = params.get(0).value;
            List<Constant> messages = constants.stream()
                    .filter(mess -> mess.name.matches("message\\d+"))
                    .toList();


            int messageNumber = messages.isEmpty() ?
                    1 :
                    messages.stream()
                            .mapToInt(mess -> Integer.parseInt(mess.name.substring(7)))
                            .max()
                            .getAsInt() + 1;

            Constant constantMessage = new Constant(Data.DB, "message" + messageNumber, message, true);
            constants.add(constantMessage);

            int arguments = 0;
            String[] words = params.get(0).value.split("\\s");

            for (int i = 0; i < words.length; i++) {
                switch (words[i]) {
                    case "%s":
                    case "%c":
                    case "%d":
                        arguments++;
                }
            }

            if (params.size() != arguments + 1) {
                throw new RuntimeException("Illegal arguments count for function printf");
            }

            String paramsText = constantMessage.name + ", " +
                    params.subList(1, params.size())
                        .stream()
                        .map(param -> {
                            if (param.type == null) {
                                return param.value;
                            } else {
                                return switch (param.type) {
                                    case STRING -> "\"" + param.value + "\"";
                                    case CHARACTER -> "'" + param.value + "'";
                                    default -> param.value;
                                };
                            }
                        })
                        .collect(Collectors.joining(", "));

            return String.format("invoke crt_printf, ADDR %s", paramsText);

        } catch (IndexOutOfBoundsException ex) {
            throw new RuntimeException("Function printf mast take more than 0 arguments");
        }
    }

    private void initConfiguration() {
        List<String> configs = new ArrayList<>() {{
           add(".586");
           add(".model flat, c");
           add("option casemap:none");
           add("include ..\\masm32\\include\\user32.inc");
           add("include ..\\masm32\\include\\kernel32.inc");
           add("include ..\\masm32\\include\\windows.inc");
           add("include ..\\masm32\\include\\msvcrt.inc\n");
           add("includelib ..\\masm32\\lib\\kernel32.lib");
           add("includelib..\\masm32\\lib\\user32.lib");
           add("includelib ..\\masm32\\lib\\msvcrt.lib\n");
           add(".const");
           add(".data");
           add(".code");
        }};

        program.addAll(configs);
    }

    private void constants() {
        List<String> constants = new ArrayList<>();
        for (Constant constant : this.constants) {
            constants.add(String.format("\t%s %s %s, 0",
                    constant.name,
                    String.valueOf(constant.type).toLowerCase(),
                    constant.isArray ? "\"" + constant.value + "\"" : constant.value));
        }
        constants.add("\n");

        int insertIndex = IntStream.range(0, program.size())
                .filter(index -> program.get(index).equals(".const"))
                .findAny()
                .getAsInt() + 1;

        program.addAll(insertIndex, constants);
    }

    private void variables() {
        List<String> variables = new ArrayList<>();
        for (Variable variable : this.variables) {
            if (variable.type != null) {
                variables.add(String.format("\t%s %s ?", variable.name, variable.type));
            }
        }
        variables.add("\n");

        int insertIndex = IntStream.range(0, program.size())
                .filter(index -> program.get(index).equals(".data"))
                .findAny()
                .getAsInt()
                + 1;

        program.addAll(insertIndex, variables);
    }

    public String getProgram() {
        return String.join("\n", program);
    }

    public Node getRoot() {
        return root;
    }

    private Data getDataType(Node node) {
        long value;
        if (node.type == Token.DECIMAL) {
            value = Long.parseLong(node.value);
        } else if (node.type == Token.OCTAL) {
            value = Long.parseLong(node.value, 8);
        } else if (node.type == Token.HEXADEMICAL) {
            value = Long.parseLong(node.value, 16);
        } else {
            value = node.value.charAt(0);
        }

        for (Data type : Data.values()) {
            if (Math.pow(2, 8 * type.size) >= value) {
                return type;
            }
        }

        throw new RuntimeException(String.format("Value %d is too large", value));
    }

    private Variable getVariable(String name) {
        Optional<Variable> optVar = variables.stream().filter(var -> var.name.equals(name)).findAny();
        return optVar.orElse(null);
    }

    private Constant getConstant(String name) {
        Optional<Constant> optConst = constants.stream().filter(cons -> cons.name.equals(name)).findAny();
        return optConst.orElse(null);
    }

    private void addVariableIfAbsent(Variable var) {
        if (variables.stream().noneMatch(var1 -> var1.equals(var))) {
            variables.add(var);
        }
    }

    private void addConstantIfAbsent(Constant constant) {
        if (constants.stream().noneMatch(const1 -> const1.equals(constant))) {
            constants.add(constant);
        }
    }

    private int jumpCount(Node node) {
        if (Parser.COMPARE.contains(node.type) || node.type == Token.ELSE) {
            return 1;
        }

        int count = 0;

        for (Node child : node.getChildren()) {
            count += jumpCount(child);
        }

        return count;
    }
}