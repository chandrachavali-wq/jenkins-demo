
pipeline {
    agent any

    options {
        disableConcurrentBuilds()
        timestamps()
    }

    environment {
        PATH = "/Users/sekharchavali/.docker/bin:/opt/homebrew/bin:${env.PATH}"
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

        stage('Build Java Automation') {
            steps {
                sh 'mvn clean package'
            }
        }

        stage('Docker Build and Test via Java') {
            steps {
                sh '''
                    java -cp target/classes \
                      com.demo.sre.DockerManager \
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
                        java -cp target/classes \
                          com.demo.sre.ArtifactoryManager \
                          "$BUILD_NUMBER"
                    '''
                }
            }
        }

        stage('Kubernetes Automation via Java') {
            steps {
                sh '''
                    java -cp target/classes \
                      com.demo.sre.KubernetesManager \
                      "$BUILD_NUMBER"
                '''
            }
        }

        stage('Helm Deployment via Java') {
            steps {
                sh '''
                    mvn exec:java \
                      -Dexec.mainClass=com.demo.sre.HelmManager \
                      -Dexec.args="$BUILD_NUMBER"
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
