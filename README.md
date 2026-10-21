Webhook test
Webhook testing

## Versioned Java automation

Java 17+ runs the shaded JAR, including Jackson. Build once with `mvn clean package`.
The current release is `1.0.1`:

```
java -jar target/sre-automation-1.0.1.jar --help
java -jar target/sre-automation-1.0.1.jar docker 33
```

Commands: `docker`, `artifactory`, `kubernetes`, and `helm`, followed by a numeric
Jenkins build number. Helm also accepts `--simulate-failure` after the number.
These commands retain the existing Docker, Python artifact, Kubernetes and Helm
behavior, so run them only when you intend those actions.

Create a separate Jenkins Pipeline job using this repository and script path
`Jenkinsfile.publish`. It builds, publishes and downloads both files to verify
byte equality, using the existing Secret text credential `jfrog-token`:

- `jenkins-artifacts-local/sre-automation/1.0.1/sre-automation-1.0.1.jar`
- `jenkins-artifacts-local/sre-automation/1.0.1/sre-automation-1.0.1.jar.sha256`

Alternatively run `python3 scripts/publish-automation.py` with `JFROG_TOKEN`
already set securely in your environment. Never commit or paste the token.
Publishing refuses to replace different bytes at an existing release path;
use one publishing job and increment the Maven version for each changed release.

After publishing, configure `GitHub-Python-Build` to load `Jenkinsfile` from SCM.
It retains Python compilation and tests, downloads the pinned release, fails on
HTTP errors or checksum mismatch, then executes each manager with `java -jar`.
It does not invoke Maven or compile Java. Jenkins needs Java 17+, Python 3,
curl, Docker, kubectl, minikube and Helm; Maven is needed only by the publishing
job. The existing local Artifactory URL remains `http://localhost:8086` and must
be reachable from the Jenkins agent. SHA-256 detects corruption; trust also
relies on controlling access to the repository and its transport.

For the next release update `pom.xml`, publish first, then update `SRE_VERSION`
in `Jenkinsfile`. Python application sources and Helm rollback logic are unchanged.
