pipeline {
    agent any

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
                sh 'docker build -t jenkins-python-demo:1.0 .'
            }
        }

        stage('Docker Run') {
            steps {
                echo 'Running Docker container...'
                sh 'docker run --rm jenkins-python-demo:1.0'
            }
        }
    }

    post {
        success {
            echo 'Jenkins CI/CD Pipeline SUCCESS!'
        }

        failure {
            echo 'Jenkins CI/CD Pipeline FAILED!'
        }
    }
}
