package com.demo.sre;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.util.zip.*;

public class ArtifactoryManager {

    public static void main(String[] args) throws Exception {

        if (args.length != 1) {
            throw new IllegalArgumentException(
                "Usage: ArtifactoryManager <build-number>"
            );
        }

        String buildNumber = args[0];

        String token = System.getenv("JFROG_TOKEN");

        if (token == null || token.isBlank()) {
            throw new IllegalStateException(
                "JFROG_TOKEN environment variable is missing"
            );
        }

        String baseUrl = System.getenv()
            .getOrDefault("ARTIFACTORY_URL",
                          "http://localhost:8086");

        String repository = "jenkins-artifacts-local";

        Path artifact = Paths.get(
            "build/jenkins-demo-" + buildNumber + ".zip"
        );

        Files.createDirectories(artifact.getParent());

        // Package Python application files
        try (ZipOutputStream zip =
                new ZipOutputStream(Files.newOutputStream(artifact))) {

            for (String filename : new String[] {
                    "app.py", "webapp.py",
                    "Dockerfile", "Dockerfile.web"
            }) {

                zip.putNextEntry(new ZipEntry(filename));
                Files.copy(Paths.get(filename), zip);
                zip.closeEntry();
            }
        }

        System.out.println("Artifact created: " + artifact);

        URI destination = URI.create(
            baseUrl + "/artifactory/" + repository +
            "/jenkins-demo/build-" + buildNumber +
            "/" + artifact.getFileName()
        );

        HttpClient client = HttpClient.newHttpClient();

        HttpRequest upload = HttpRequest.newBuilder(destination)
            .header("Authorization", "Bearer " + token)
            .PUT(HttpRequest.BodyPublishers.ofFile(artifact))
            .build();

        HttpResponse<String> response = client.send(
            upload, HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() < 200 ||
            response.statusCode() >= 300) {
            throw new IllegalStateException(
                "Artifactory upload failed. HTTP " +
                response.statusCode()
            );
        }

        System.out.println("Artifact uploaded successfully.");

        // Verify that the artifact exists
        HttpRequest verify = HttpRequest.newBuilder(destination)
            .header("Authorization", "Bearer " + token)
            .method("HEAD", HttpRequest.BodyPublishers.noBody())
            .build();

        HttpResponse<Void> verification = client.send(
            verify, HttpResponse.BodyHandlers.discarding()
        );

        if (verification.statusCode() != 200) {
            throw new IllegalStateException(
                "Artifact verification failed. HTTP " +
                verification.statusCode()
            );
        }

        System.out.println(
            "Artifact verified successfully in Artifactory!"
        );
    }
}
