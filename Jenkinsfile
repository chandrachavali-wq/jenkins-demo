
pipeline {
    agent any

    options {
        disableConcurrentBuilds()
        timestamps()
    }

    environment {
        PATH = "/Users/sekharchavali/.docker/bin:/opt/homebrew/bin:${env.PATH}"
        SRE_VERSION = '1.0.2'
        SRE_JAR = '.automation/sre-automation.jar'
        KUBE_CONTEXT = 'minikube'
        ARTIFACTORY_URL = 'http://localhost:8086'
    }

    stages {
        stage('Python Build') {
            steps {
                sh 'python3 -m py_compile app.py webapp.py'
            }
        }

        stage('Python Unit Tests') {
            steps {
                sh 'python3 -m unittest discover -v'
            }
        }

        
        stage('ML Build Failure Prediction') {
            steps {
                script {
                    try {
                        def response = sh(
                            script: '''
                                curl --fail --silent --show-error \
                                --connect-timeout 3 \
                                --max-time 15 \
                                -X POST http://localhost:8001/predict \
                                -H "Content-Type: application/json" \
                                -d '{"previous_failures":3,"queue_time_minutes":12,"previous_build_duration_minutes":45,"previous_build_cpu_percent":90,"previous_build_memory_percent":85}'
                            ''',
                            returnStdout: true
                        ).trim()

                        withEnv(["ML_RESPONSE=${response}"]) {
                            sh(script: '''
        python3 - <<'PY'
        import json
        import os

        result = json.loads(os.environ["ML_RESPONSE"])
        prediction = result["prediction"]
        probability = float(result["failure_probability"]) * 100

        print("===== ML BUILD RISK ANALYSIS =====")
        print(f"Prediction          : {prediction}")
        print(f"Failure Probability : {probability:.1f}%")
        print("Pipeline Mode       : ADVISORY")
        print("==================================")

        if prediction == "HIGH_RISK":
            print("WARNING: High build failure risk predicted.")
        PY
        '''.stripIndent())
                        }
                    } catch (Exception e) {
                        echo "WARNING: ML prediction unavailable: ${e.message}"
                        echo 'Continuing the existing CI/CD pipeline.'
                    }
                }
            }
        }

        stage('Download and Verify Java Automation') {
            steps {
                withCredentials([string(credentialsId: 'jfrog-token', variable: 'JFROG_TOKEN')]) {
                    sh '''
                        set +x
                        set -eu
                        mkdir -p .automation
                        base="$ARTIFACTORY_URL/artifactory/jenkins-artifacts-local/sre-automation/$SRE_VERSION"
                        name="sre-automation-$SRE_VERSION.jar"
                        rm -f "$SRE_JAR" "$SRE_JAR.tmp"
                        curl --fail --silent --show-error --header "Authorization: Bearer $JFROG_TOKEN" \
                          "$base/$name" -o "$SRE_JAR.tmp"
                        curl --fail --silent --show-error --header "Authorization: Bearer $JFROG_TOKEN" \
                          "$base/$name.sha256" -o .automation/expected.sha256
                        python3 scripts/verify-sha256.py "$SRE_JAR.tmp" .automation/expected.sha256
                        mv "$SRE_JAR.tmp" "$SRE_JAR"
                        java -jar "$SRE_JAR" --help
                    '''
                }
            }
        }

        stage('Docker Build and Test via Java') {
            steps {
                sh '''
                    java -jar "$SRE_JAR" docker \
                      "$BUILD_NUMBER"
                '''
            }
        }

        stage('Publish and Verify JFrog Artifact via Java') {
            steps {
                withCredentials([
                    string(
                        credentialsId: 'jfrog-token',
                        variable: 'JFROG_TOKEN'
                    )
                ]) {
                    sh '''
                        set +x
                        java -jar "$SRE_JAR" artifactory \
                          "$BUILD_NUMBER"
                    '''
                }
            }
        }

        stage('Kubernetes Automation via Java') {
            steps {
                sh '''
                    java -jar "$SRE_JAR" kubernetes \
                      "$BUILD_NUMBER"
                '''
            }
        }

        stage('Helm Deployment via Java') {
            steps {
                sh '''
                    java -jar "$SRE_JAR" helm "$BUILD_NUMBER"
                '''
            }
        }
    }

    post {
        success {
            echo 'Java-driven Jenkins CI/CD Pipeline SUCCESS!'
        }

        failure {
            echo 'Java-driven Jenkins CI/CD Pipeline FAILED!'
        }

        always {
            echo "Pipeline execution completed for build ${env.BUILD_NUMBER}"
        }
    }
}
