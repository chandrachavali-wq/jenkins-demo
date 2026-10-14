pipeline {
    agent any

    environment {
        PATH = "/Users/sekharchavali/.docker/bin:${env.PATH}"
    }

    stages {
        stage('Build') {
            steps {
                sh 'python3 -m py_compile app.py'
            }
        }

        stage('Test') {
            steps {
                sh 'python3 -m unittest discover -v'
            }
        }

        stage('Docker Build') {
            steps {
                sh 'docker build -t jenkins-python-demo:1.0 .'
            }
        }

        stage('Docker Run') {
            steps {
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
