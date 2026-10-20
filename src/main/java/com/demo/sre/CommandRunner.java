package com.demo.sre;

import java.util.Arrays;

public class CommandRunner {

    public static void execute(String... command)
            throws Exception {

        System.out.println(
            "Executing: " + Arrays.toString(command)
        );

        ProcessBuilder builder =
                new ProcessBuilder(command);

        builder.inheritIO();

        Process process = builder.start();

        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new RuntimeException(
                "Command failed with exit code: " + exitCode
            );
        }
    }
}
