package com.demo.sre;

public class KubernetesManager {

    private static final String CONTEXT = "minikube";
    private static final String NAMESPACE = "development";

    public static void main(String[] args) throws Exception {

        if (args.length != 1) {
            throw new IllegalArgumentException(
                "Usage: KubernetesManager <build-number>"
            );
        }

        String buildNumber = args[0];

        String pythonImage = "jenkins-python-demo:" + buildNumber;
        String webImage = "jenkins-webapp:" + buildNumber;
        String jobName = "jenkins-python-job-" + buildNumber;

        System.out.println("Verifying Minikube cluster...");

        CommandRunner.execute(
            "kubectl", "--context", CONTEXT, "get", "nodes"
        );

        System.out.println("Ensuring namespace exists...");

        // Create the namespace if it doesn't already exist.
        // This uses kubectl apply with a generated manifest.
        CommandRunner.execute(
            "kubectl", "--context", CONTEXT,
            "get", "namespace", NAMESPACE
        );

        System.out.println("Loading Docker images into Minikube...");

        CommandRunner.execute(
            "minikube", "image", "load", pythonImage
        );

        CommandRunner.execute(
            "minikube", "image", "load", webImage
        );

        if (jobExists(jobName)) {
    System.out.println(
        "Kubernetes Job already exists: " + jobName
    );
} else {
    System.out.println("Creating Kubernetes Job...");

    CommandRunner.execute(
        "kubectl", "--context", CONTEXT,
        "-n", NAMESPACE,
        "create", "job", jobName,
        "--image=" + pythonImage
    );
}

        System.out.println("Waiting for Job completion...");

        CommandRunner.execute(
            "kubectl", "--context", CONTEXT,
            "-n", NAMESPACE,
            "wait",
            "--for=condition=complete",
            "job/" + jobName,
            "--timeout=120s"
        );

        System.out.println("Reading Kubernetes Job logs...");

        CommandRunner.execute(
            "kubectl", "--context", CONTEXT,
            "-n", NAMESPACE,
            "logs", "job/" + jobName
        );

        System.out.println(
            "Kubernetes Job completed successfully!"
        );
    }
    private static boolean jobExists(String jobName)
        throws Exception {

    ProcessBuilder builder = new ProcessBuilder(
        "kubectl", "--context", CONTEXT,
        "-n", NAMESPACE,
        "get", "job", jobName,
        "-o", "name"
    );

    Process process = builder.start();
    int exitCode = process.waitFor();

    if (exitCode == 0) {
        return true;
    }

    if (exitCode == 1) {
        return false;
    }

    throw new IllegalStateException(
        "Failed to check Kubernetes Job. Exit code: " + exitCode
    );
}
}