
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
        stage('Infrastructure Metrics Collection') {
            steps {
                script {
                    try {
                        sh '''
                            mkdir -p metrics-history

                            python3 scripts/collect_infrastructure_metrics.py > "metrics-history/infrastructure_${BUILD_NUMBER}.json"

                            python3 -m json.tool "metrics-history/infrastructure_${BUILD_NUMBER}.json"
                        '''
                    } catch (Exception e) {
                        echo "WARNING: Infrastructure metrics collection failed: ${e.message}"
                        echo 'Continuing CI/CD pipeline in advisory mode.'
                    }
                }
            }
            post {
                always {
                    script {
                        archiveArtifacts(
                            artifacts: "metrics-history/infrastructure_${env.BUILD_NUMBER}.json",
                            allowEmptyArchive: true
                        )
                    }
                }
            }
        }
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
                        withCredentials([
                            usernamePassword(
                                credentialsId: 'jenkins-api',
                                usernameVariable: 'JENKINS_USER',
                                passwordVariable: 'JENKINS_TOKEN'
                            )
                        ]) {
                            sh '''
                                set -eu
                                python3 scripts/collect_build_metrics.py > build_metrics.json
                                python3 scripts/predict_build_risk.py
                            '''
                        }
                    } catch (Exception e) {
                        echo "WARNING: ML prediction unavailable: ${e.message}"
                        echo 'Continuing CI/CD pipeline in advisory mode.'
                    }
                }
            }
        }

        stage('Download and Verify Java Automation') {
            steps {
                withCredentials([
                    string(
                        credentialsId: 'jfrog-token',
                        variable: 'JFROG_TOKEN'
                    )
                ]) {
                    sh '''
                        set +x
                        set -eu

                        mkdir -p .automation

                        base="$ARTIFACTORY_URL/artifactory/jenkins-artifacts-local/sre-automation/$SRE_VERSION"
                        name="sre-automation-$SRE_VERSION.jar"

                        rm -f "$SRE_JAR" "$SRE_JAR.tmp"

                        curl --fail --silent --show-error \
                          --header "Authorization: Bearer $JFROG_TOKEN" \
                          "$base/$name" -o "$SRE_JAR.tmp"

                        curl --fail --silent --show-error \
                          --header "Authorization: Bearer $JFROG_TOKEN" \
                          "$base/$name.sha256" -o .automation/expected.sha256

                        python3 scripts/verify-sha256.py \
                          "$SRE_JAR.tmp" .automation/expected.sha256

                        mv "$SRE_JAR.tmp" "$SRE_JAR"

                        java -jar "$SRE_JAR" --help
                    '''
                }
            }
        }

        stage('Docker Build and Test via Java') {
            steps {
                sh '''
                    java -jar "$SRE_JAR" docker "$BUILD_NUMBER"
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
                        java -jar "$SRE_JAR" artifactory "$BUILD_NUMBER"
                    '''
                }
            }
        }

        stage('Kubernetes Automation via Java') {
            steps {
                sh '''
                    java -jar "$SRE_JAR" kubernetes "$BUILD_NUMBER"
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
