package com.kpi.fict;

import java.util.HashMap;
import java.util.Map;

public class Lexer {
    public static final Map<Character, Token> SYMBOLS = new HashMap<>() {{
       put('{', Token.LPAR);
       put('}', Token.RPAR);
       put('(', Token.LBRA);
       put(')', Token.RBRA);
       put('+', Token.PLUS);
       put('-', Token.MINUS);
       put('=', Token.ASSIGNMENT);
       put(',', Token.COMMA);
       put(';', Token.SEMICOLON);
    }};

    public static final Map<String, Token> KEY_WORDS = new HashMap<>() {{
       put("if", Token.IF);
       put("else", Token.ELSE);
       put("return", Token.RETURN);
       put("char", Token.CHAR);
       put("unsigned char", Token.UNSIGNED_CHAR);
       put("int", Token.INT);
       put("void", Token.VOID);
    }};

    public static final Map<String, Token> LOGICAL = new HashMap<>() {{
       put("==", Token.EQUAL);
       put("!=", Token.NOT_EQUAL);
       put("!", Token.NOT);
       put("<", Token.LESS);
       put(">", Token.MORE);
       put("<=", Token.LESS_EQUAL);
       put(">=", Token.MORE_EQUAL);
       put("&&", Token.AND);
       put("||", Token.OR);
    }};

    private final String program;
    private final char[] chars;
    private char ch;
    private int index = 0;
    public int line = 1;

    public Token token;
    public String value;

    public Lexer(String program) {
        if (program.length() == 0) {
            throw new RuntimeException("Empty program");
        }

        this.program = program;
        chars = program.toCharArray();
        ch = chars[0];
    }

    private void nextChar() {
        ch = index < chars.length - 1 ? chars[++index] : Character.MIN_VALUE;
    }

    public void nextToken() {
        token = null;
        value = null;

        while (token == null) {
            if (isEOF()) {
                token = Token.EOF;

            } else if (String.valueOf(ch).isBlank() || ch == '"' || ch == '\'') {
                if (ch == '\n') line++;
                nextChar();

            } else if (index < chars.length - 1 && LOGICAL.containsKey("" + ch + chars[index + 1])) {
                value = "" + ch + chars[index + 1];
                token = LOGICAL.get(value);
                nextChar();
                nextChar();

            } else if (LOGICAL.containsKey(String.valueOf(ch))) {
                value = String.valueOf(ch);
                token = LOGICAL.get(value);
                nextChar();

            } else if (SYMBOLS.containsKey(ch)) {
                token = SYMBOLS.get(ch);
                value = String.valueOf(ch);
                nextChar();

            } else if (ch == '#') {
                int includeEndIndex = program.indexOf(">", index + 1);
                if (includeEndIndex < 0) {
                    throw new RuntimeException("Illegal include at line " + line);
                }

                String include = program.substring(index, includeEndIndex + 1);
                String[] includeParts = include.split(" ");

                if (!(includeParts.length == 2 &&
                        includeParts[0].equals("#include") &&
                        includeParts[1].matches("<\\w+.h>"))) {
                    throw new RuntimeException("Illegal include at line " + line);
                }

                value = includeParts[1].substring(1, includeParts[1].indexOf("."));
                token = Token.INCLUDE;

                index = includeEndIndex;
                nextChar();

            } else if (index > 0 && chars[index - 1] == '\'') {
                nextChar();

                if (ch != '\'') {
                    throw new RuntimeException(
                            String.format("Illegal character literal '%s' at line %d",
                                    program.substring(index - 1, program.indexOf("'", index)), line));
                }

                token = Token.CHARACTER;
                value = String.valueOf(chars[index - 1]);

            } else if (index > 0 && chars[index - 1] == '"') {
                int closeQuotesIndex = program.indexOf("\"", index);
                if (closeQuotesIndex < 0) {
                    throw new RuntimeException("Close double quotes not found at line " + line);
                }
                value = closeQuotesIndex - index == 1 ? "" : program.substring(index, closeQuotesIndex);
                token = Token.STRING;

                index = closeQuotesIndex;
                line += value.chars().filter(ch -> ch == '\n').count();
                nextChar();

            } else if (Character.isDigit(ch) || Character.isLetter(ch) || ch == '*') {
                StringBuilder word = new StringBuilder();

                while (((Character.isLetter(ch) ||
                        Character.isDigit(ch) ||
                        ch == '_' ||
                        ch == '*' ||
                        ch == '[' ||
                        ch == ']') && index < chars.length) ||
                        index < chars.length - 4 && program.startsWith("unsigned char", index - 8)) {
                    word.append(ch);
                    nextChar();
                }

                value = word.toString();

                if (isHex(value)) {
                    token = Token.HEXADEMICAL;
                } else if (isOct(value)) {
                    token = Token.OCTAL;
                } else if (isDecimal(value)) {
                    token = Token.DECIMAL;
                } else if (!isValidNumber(value)) {
                    int expressionEndIndex = program.indexOf(" ", index + 1);
                    throw new RuntimeException(
                            String.format("Illegal expression %s at line %d",
                                    program.substring(index - value.length(), expressionEndIndex < 0 ? chars.length : expressionEndIndex),
                                    line));
                } else {
                    token = KEY_WORDS.getOrDefault(value, Token.ID);
                }

            } else if (ch == '/' && index < chars.length - 1) {
                if (chars[index + 1] == '/') {
                    int endCommentIndex = program.indexOf("\n", index + 1);
                    String comment;

                    if (endCommentIndex < 0) {
                        comment = program.substring(index, chars.length);
                        index = chars.length;
                    } else {
                        comment = program.substring(index, endCommentIndex);
                        index = endCommentIndex;
                        ch = chars[index];
                    }

                    token = Token.COMMENT;
                    value = comment;

                } else if (chars[index + 1] == '*') {
                    int endMultiCommentIndex = program.indexOf("*/", index + 1);

                    if (endMultiCommentIndex < 0) {
                        throw new RuntimeException("Illegal multi-comment syntax at line " + line);
                    }

                    String comment = program.substring(index, endMultiCommentIndex + 2);

                    if (endMultiCommentIndex < chars.length - 2) {
                        index = endMultiCommentIndex + 2;
                        ch = chars[index];
                    } else {
                        index = chars.length;
                    }

                    token = Token.MULTI_COMMENT;
                    value = comment;
                    line += value.chars().filter(ch -> ch == '\n').count();
                } else {
                    int tokenEndIndex = program.indexOf(" ", index + 1);
                    String unexpectedToken = program.substring(index, tokenEndIndex < 0 ? chars.length : tokenEndIndex);
                    throw new RuntimeException(String.format("Unexpected token %s at line %d", unexpectedToken, line));
                }
            } else {
                int tokenEndIndex = program.indexOf(" ", index);
                String unexpectedToken = program.substring(index, tokenEndIndex < 0 ? chars.length : tokenEndIndex);
                throw new RuntimeException(String.format("Unexpected token %s at line %d", unexpectedToken, line));
            }
        }
    }

    private boolean isEOF() {
        return index == chars.length || ch == Character.MIN_VALUE;
    }

    public static boolean isHex(String number) {
        return number.matches("0x[\\da-fA-F]+");
    }

    public static boolean isOct(String number) {
        return number.matches("0[0-7]+");
    }

    public static boolean isDecimal(String number) {
        return number.matches("\\d+");
    }

    private static boolean isValidNumber(String number) {
        return !number.matches("\\d.+");
    }
}
