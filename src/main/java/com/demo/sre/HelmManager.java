package com.demo.sre;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class HelmManager {

    private static final String CONTEXT = "minikube";
    private static final String NAMESPACE = "development";
    private static final String RELEASE = "jenkins-python-webapp";
    private static final String CHART = "./helm-webapp";
    private static final String DEPLOYMENT =
            "jenkins-python-webapp-helm-webapp";

    public static void main(String[] args) throws Exception {

        if (args.length != 1) {
            throw new IllegalArgumentException(
                "Usage: HelmManager <build-number>"
            );
        }

        String buildNumber = args[0];
        String previousRevision = findLastDeployedRevision();

        try {
            System.out.println("Deploying image version: " + buildNumber);

            CommandRunner.execute(
                "helm", "upgrade", "--install",
                RELEASE, CHART,
                "--kube-context", CONTEXT,
                "--namespace", NAMESPACE,
                "--set-string", "image.repository=jenkins-webapp",
                "--set-string", "image.tag=" + buildNumber,
                "--server-side=true",
                "--force-conflicts",
                "--wait",
                "--timeout", "5m"
            );

            CommandRunner.execute(
                "kubectl", "--context", CONTEXT,
                "-n", NAMESPACE,
                "rollout", "status",
                "deployment/" + DEPLOYMENT,
                "--timeout=180s"
            );

            verifyApplicationHealth(buildNumber);

            System.out.println(
                "Helm deployment, rollout, and health check succeeded!"
            );
        } catch (Exception deploymentError) {
            System.err.println(
                "Helm deployment failed: " + deploymentError
            );

            if (previousRevision != null) {
                System.out.println(
                    "Rolling back to revision " + previousRevision
                );

                CommandRunner.execute(
                    "helm", "rollback",
                    RELEASE, previousRevision,
                    "--kube-context", CONTEXT,
                    "--namespace", NAMESPACE,
                    "--wait",
                    "--timeout", "5m"
                );

                CommandRunner.execute(
                    "kubectl", "--context", CONTEXT,
                    "-n", NAMESPACE,
                    "rollout", "status",
                    "deployment/" + DEPLOYMENT,
                    "--timeout=180s"
                );

                System.out.println("Rollback succeeded.");
            } else {
                System.err.println(
                    "No previously deployed revision available."
                );
            }

            throw deploymentError;
        }
    }

    
private static String findLastDeployedRevision()
        throws Exception {

    ProcessBuilder builder = new ProcessBuilder(
        "helm", "history", RELEASE,
        "--kube-context", CONTEXT,
        "--namespace", NAMESPACE,
        "--output", "json"
    );

    builder.redirectErrorStream(true);

    Process process = builder.start();

    String output;
    try (var input = process.getInputStream()) {
        output = new String(input.readAllBytes());
    }

    int exitCode = process.waitFor();

    if (exitCode != 0) {
        if (output.contains("release: not found")
                || output.contains("release not found")) {
            System.out.println(
                "No existing Helm release found."
            );
            return null;
        }

        throw new IllegalStateException(
            "Unable to read Helm history: " + output
        );
    }

    ObjectMapper mapper = new ObjectMapper();
    JsonNode history = mapper.readTree(output);

    String deployedRevision = null;

    for (JsonNode revision : history) {
        if ("deployed".equals(
                revision.path("status").asText())) {

            deployedRevision =
                revision.path("revision").asText();
        }
    }

    System.out.println(
        "Previous deployed Helm revision: " +
        (deployedRevision == null
            ? "None" : deployedRevision)
    );

    return deployedRevision;
}
private static void verifyApplicationHealth(String buildNumber)
        throws Exception {

    String podName = "webapp-health-" + buildNumber;

    try {
        System.out.println("Checking application health...");

        CommandRunner.execute(
            "kubectl", "--context", CONTEXT,
            "-n", NAMESPACE,
            "run", podName,
            "--image=curlimages/curl:8.12.1",
            "--restart=Never",
            "--image-pull-policy=IfNotPresent",
            "--command", "--",
            "curl", "--fail", "--silent", "--show-error",
            "--retry", "5", "--retry-delay", "2",
            "http://" + DEPLOYMENT + ":8081/health"
        );

        CommandRunner.execute(
            "kubectl", "--context", CONTEXT,
            "-n", NAMESPACE,
            "wait",
            "--for=jsonpath={.status.phase}=Succeeded",
            "pod/" + podName,
            "--timeout=120s"
        );

        CommandRunner.execute(
            "kubectl", "--context", CONTEXT,
            "-n", NAMESPACE,
            "logs", podName
        );

        System.out.println("Application health check passed.");

    } finally {
        CommandRunner.execute(
            "kubectl", "--context", CONTEXT,
            "-n", NAMESPACE,
            "delete", "pod", podName,
            "--ignore-not-found=true",
            "--wait=false"
        );
    }
}
}
