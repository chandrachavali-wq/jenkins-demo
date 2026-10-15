pipeline {
    agent any

    environment {
        PATH = "/Users/sekharchavali/.docker/bin:/opt/homebrew/bin:${env.PATH}"
        IMAGE_NAME = "jenkins-python-demo"
        WEB_IMAGE = "jenkins-webapp"
        DEPLOYMENT = "jenkins-webapp"
    }

    stages {

        stage('Build') {
            steps {
                echo 'Compiling Python applications...'

                sh '''
                    python3 -m py_compile app.py webapp.py
                '''
            }
        }

        stage('Test') {
            steps {
                echo 'Running Python unit tests...'

                sh '''
                    python3 -m unittest discover -v
                '''
            }
        }

        stage('Docker Build') {
            steps {
                echo 'Building Python Job image...'

                sh '''
                    docker build \
                      -t ${IMAGE_NAME}:${BUILD_NUMBER} .
                '''

                echo 'Building Python Web Application image...'

                sh '''
                    docker build \
                      -f Dockerfile.web \
                      -t ${WEB_IMAGE}:${BUILD_NUMBER} .
                '''
            }
        }

        stage('Docker Run') {
            steps {
                echo 'Testing Python Job container...'

                sh '''
                    docker run --rm \
                      ${IMAGE_NAME}:${BUILD_NUMBER}
                '''
            }
        }

        stage('Load Images into Minikube') {
            steps {
                echo 'Loading Docker images into Kubernetes...'

                sh '''
                    minikube image load ${IMAGE_NAME}:${BUILD_NUMBER}
                    minikube image load ${WEB_IMAGE}:${BUILD_NUMBER}
                '''
            }
        }

        stage('Deploy Kubernetes Job') {
            steps {
                echo 'Creating Kubernetes batch Job...'

                sh '''
                    kubectl create job \
                      jenkins-python-job-${BUILD_NUMBER} \
                      --image=${IMAGE_NAME}:${BUILD_NUMBER}
                '''
            }
        }

        stage('Verify Kubernetes Job') {
            steps {
                sh '''
                    kubectl wait \
                      --for=condition=complete \
                      job/jenkins-python-job-${BUILD_NUMBER} \
                      --timeout=120s

                    kubectl logs \
                      job/jenkins-python-job-${BUILD_NUMBER}
                '''
            }
        }

	stage('Rolling Deployment') {
	    steps {
		script {
		    try {
			echo 'Starting Kubernetes rolling deployment...'

			sh '''
			    sed "s|image: jenkins-webapp:1.0|image: ${WEB_IMAGE}:${BUILD_NUMBER}|" deployment.yaml \
			      | kubectl apply -f -

			    kubectl apply -f service.yaml

			    kubectl rollout status \
			      deployment/${DEPLOYMENT} \
			      --timeout=180s
			'''

			echo 'Kubernetes rolling deployment completed successfully.'
		    }
		    catch (Exception e) {
			echo "Rolling deployment failed: ${e}"

			echo 'Attempting Kubernetes rollback...'

			sh '''
			    kubectl rollout undo deployment/${DEPLOYMENT}

			    kubectl rollout status \
			      deployment/${DEPLOYMENT} \
			      --timeout=180s
			'''

			error('Deployment failed. Rollback was attempted.')
		    }
		}
	    }
	}
	stage('Verify Web Deployment') {
	    steps {
		echo 'Checking Kubernetes application health...'

		sh '''
		    kubectl get deployment ${DEPLOYMENT}

		    kubectl get pods -l app=jenkins-webapp

		    kubectl get svc jenkins-webapp-service

		    kubectl run webapp-health-${BUILD_NUMBER} \
		      --image=curlimages/curl:8.12.1 \
		      --restart=Never \
		      --image-pull-policy=IfNotPresent \
		      --command -- \
		      curl --fail --silent --show-error \
		      --retry 5 --retry-delay 2 \
		      http://jenkins-webapp-service/health

		    kubectl wait \
		      --for=jsonpath='{.status.phase}'=Succeeded \
		      pod/webapp-health-${BUILD_NUMBER} \
		      --timeout=120s

		    kubectl logs pod/webapp-health-${BUILD_NUMBER}
		'''
	    }
	}
    }
    post {
        success {
            echo 'Jenkins Kubernetes CI/CD Pipeline SUCCESS!'
        }

        failure {
            echo 'Jenkins Kubernetes CI/CD Pipeline FAILED!'
        }
    }
}
