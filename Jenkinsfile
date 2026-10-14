pipeline {
    agent any

    environment {
        PATH = "/Users/sekharchavali/.docker/bin:/opt/homebrew/bin:${env.PATH}"
        IMAGE_NAME = "jenkins-python-demo"
    }

    stages {

        stage('Build') {
            steps {
                echo 'Building Python application...'
                sh 'python3 -m py_compile app.py'
            }
        }

        stage('Test') {
            steps {
                echo 'Running unit tests...'
                sh 'python3 -m unittest discover -v'
            }
        }

        stage('Docker Build') {
            steps {
                echo 'Building Docker image...'

                sh '''
                    docker build \
                      -t ${IMAGE_NAME}:${BUILD_NUMBER} .
                '''
            }
        }

        stage('Docker Run') {
            steps {
                echo 'Testing Docker container...'

                sh '''
                    docker run --rm \
                      ${IMAGE_NAME}:${BUILD_NUMBER}
                '''
            }
        }

        stage('Load Image into Minikube') {
            steps {
                echo 'Loading Docker image into Minikube...'

                sh '''
                    minikube image load \
                      ${IMAGE_NAME}:${BUILD_NUMBER}
                '''
            }
        }

        stage('Deploy Kubernetes Job') {
            steps {
                echo 'Creating Kubernetes Job...'

                sh '''
                    kubectl create job \
                      jenkins-python-job-${BUILD_NUMBER} \
                      --image=${IMAGE_NAME}:${BUILD_NUMBER}
                '''
            }
        }

        stage('Verify Kubernetes Job') {
            steps {
                echo 'Waiting for Kubernetes Job completion...'

                sh '''
                    kubectl wait \
                      --for=condition=complete \
                      job/jenkins-python-job-${BUILD_NUMBER} \
                      --timeout=120s
                '''

                echo 'Displaying application logs...'

                sh '''
                    kubectl logs \
                      job/jenkins-python-job-${BUILD_NUMBER}
                '''
            }
        }
    }

    post {
        success {
            echo 'Jenkins Docker Kubernetes Pipeline SUCCESS!'
        }

        failure {
            echo 'Jenkins Docker Kubernetes Pipeline FAILED!'
        }
    }
}
