package com.kpi.fict;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Main {

    public static void main(String[] args) {

        if (args.length != 2) {
            throw new RuntimeException("Input file path and character must be given");
        }

        File input = null;
        char character;
        try {
            input = new File(args[0]);
            character = (char) Integer.parseInt(args[1]);
        } catch (NumberFormatException ex1) {
            if (args[1].length() != 1) {
                throw new RuntimeException("Character must be given");
            }

            character = args[0].toCharArray()[0];
        }

        String program;
        try {
            program = String.join("\n", Files.readAllLines(Paths.get(input.getPath())));
        } catch (IOException e) {
            throw new RuntimeException(String.format("Input file %s not found", input.getName()));
        }

        Parser parser = new Parser(program);
        parser.parse("Upper-lower case");

        Node root = parser.getRoot();
        parser.setCharacter(root, character);

        Compiler compiler = new Compiler(root);
        compiler.compile();
        String result = compiler.getProgram();

        File file = new File("result.asm");

        try {
            file.createNewFile();

            PrintWriter writer = new PrintWriter(file);
            writer.write(result);
            System.out.println("success!");

            writer.flush();
            writer.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
