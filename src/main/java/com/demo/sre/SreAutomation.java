package com.demo.sre;

import java.util.Arrays;

/** Entry point for the independently released automation tool. */
public final class SreAutomation {
    private SreAutomation() { }

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && ("--help".equals(args[0]) || "help".equals(args[0]))) {
            System.out.println("Usage: java -jar sre-automation.jar <docker|artifactory|kubernetes|helm> <build-number> [--simulate-failure]");
            return;
        }
        if (args.length < 2 || !args[1].matches("[0-9]+")) {
            throw new IllegalArgumentException("Expected a command and numeric Jenkins build number; use --help");
        }
        String[] managerArgs = Arrays.copyOfRange(args, 1, args.length);
        switch (args[0]) {
            case "docker" -> DockerManager.main(managerArgs);
            case "artifactory" -> ArtifactoryManager.main(managerArgs);
            case "kubernetes" -> KubernetesManager.main(managerArgs);
            case "helm" -> HelmManager.main(managerArgs);
            default -> throw new IllegalArgumentException("Unknown command; use --help");
        }
    }
}
