package com.demo.sre;

public class DockerManager {

    public static void main(String[] args)
            throws Exception {

        if (args.length != 1) {
            throw new IllegalArgumentException(
                "Usage: DockerManager <build-number>"
            );
        }

        String buildNumber = args[0];

        String pythonImage =
                "jenkins-python-demo:" + buildNumber;

        String webImage =
                "jenkins-webapp:" + buildNumber;

        System.out.println("Building Python Job image...");

        CommandRunner.execute(
                "docker", "build",
                "-t", pythonImage,
                "."
        );

        System.out.println("Building Python Web image...");

        CommandRunner.execute(
                "docker", "build",
                "-f", "Dockerfile.web",
                "-t", webImage,
                "."
        );

        System.out.println("Testing Python Job...");

        CommandRunner.execute(
                "docker", "run",
                "--rm", pythonImage
        );

        System.out.println(
                "Docker build and testing successful!"
        );
    }
}
