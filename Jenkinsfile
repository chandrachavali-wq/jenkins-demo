pipeline {
    agent any

	environment {
	    PATH = "/Users/sekharchavali/.docker/bin:/opt/homebrew/bin:${env.PATH}"

	    IMAGE_NAME = "jenkins-python-demo"
	    WEB_IMAGE = "jenkins-webapp"

	    DEPLOYMENT = "jenkins-python-webapp-helm-webapp"

	    HELM_RELEASE = "jenkins-python-webapp"
	    HELM_CHART = "./helm-webapp"

	    NAMESPACE = "development"
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

	stage('Validate Helm Chart') {
	    steps {
		echo 'Validating Helm chart...'

		sh '''
		    echo "Running Helm lint..."

		    helm lint "$HELM_CHART"

		    echo "Rendering Kubernetes manifests..."

		    helm template "$HELM_RELEASE" "$HELM_CHART" \
		      --namespace "$NAMESPACE" \
		      --set-string image.tag="$BUILD_NUMBER" \
		      > /dev/null

		    echo "Helm chart validation successful."
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
	stage('Deploy and Verify Web Application') {
	    steps {
		script {
		    def previousRevision = ''

		    try {
			echo 'Checking existing Helm release...'

			previousRevision = sh(
			    script: '''
				if helm status "$HELM_RELEASE" \
				     --namespace "$NAMESPACE" >/dev/null 2>&1; then

				    helm history "$HELM_RELEASE" \
				      --namespace "$NAMESPACE" \
				      -o json |
				    python3 -c 'import json,sys; h=json.load(sys.stdin); print(next((str(r["revision"]) for r in reversed(h) if r["status"] == "deployed"), ""))'

				else
				    echo ""
				fi
			    ''',
			    returnStdout: true
			).trim()
			echo "Previous deployed Helm revision: ${previousRevision ?: 'None'}"

			echo 'Deploying Python web application using Helm...'

			sh '''
			    helm upgrade --install "$HELM_RELEASE" "$HELM_CHART" \
			      --namespace "$NAMESPACE" \
			      --set-string image.repository="$WEB_IMAGE" \
			      --set-string image.tag="$BUILD_NUMBER" \
			      --wait \
			      --timeout 5m
			'''

			echo 'Verifying Kubernetes deployment...'

			sh '''
			    kubectl rollout status \
			      deployment/"$DEPLOYMENT" \
			      -n "$NAMESPACE" \
			      --timeout=180s

			    kubectl get deployment "$DEPLOYMENT" -n "$NAMESPACE"

			    kubectl get pods \
			      -n "$NAMESPACE" \
			      -l app.kubernetes.io/instance="$HELM_RELEASE"

			    helm status "$HELM_RELEASE" -n "$NAMESPACE"
			'''

			echo 'Checking application health...'
			sh '''
			    kubectl run "webapp-health-${BUILD_NUMBER}" \
			      -n "$NAMESPACE" \
			      --image=curlimages/curl:8.12.1 \
			      --restart=Never \
			      --image-pull-policy=IfNotPresent \
			      --command -- \
			      curl --fail --silent --show-error \
				--retry 5 --retry-delay 2 \
				http://jenkins-python-webapp-helm-webapp:8081/health

			    kubectl wait \
			      -n "$NAMESPACE" \
			      --for=jsonpath='{.status.phase}'=Succeeded \
			      pod/"webapp-health-${BUILD_NUMBER}" \
			      --timeout=120s

			    kubectl logs \
			      -n "$NAMESPACE" \
			      pod/"webapp-health-${BUILD_NUMBER}"
			'''

			echo 'Helm deployment and health verification successful.'
		    }
		    catch (Exception e) {
			echo "Deployment or health verification failed: ${e}"

			if (previousRevision) {
				echo "Attempting Helm rollback to revision ${previousRevision}..."

				sh """
				helm rollback "\$HELM_RELEASE" "${previousRevision}" \
				  --namespace "\$NAMESPACE" \
				  --wait \
				  --timeout 5m
				"""
				echo 'Verifying Kubernetes deployment after rollback...'

				sh '''
				    kubectl rollout status deployment/"$DEPLOYMENT" \
				      -n "$NAMESPACE" \
				      --timeout=180s

				    kubectl get deployment "$DEPLOYMENT" -n "$NAMESPACE"
				'''

				echo 'Checking application health after rollback...'

				sh '''
				    kubectl run "rollback-health-${BUILD_NUMBER}" \
				      -n "$NAMESPACE" \
				      --image=curlimages/curl:8.12.1 \
				      --restart=Never \
				      --image-pull-policy=IfNotPresent \
				      --command -- \
				      curl --fail --silent --show-error \
				      --retry 5 --retry-delay 2 \
				      http://jenkins-python-webapp-helm-webapp:8081/health

				    kubectl wait \
				      -n "$NAMESPACE" \
				      --for=jsonpath='{.status.phase}=Succeeded' \
				      "pod/rollback-health-${BUILD_NUMBER}" \
				      --timeout=120s

				    kubectl logs \
				      -n "$NAMESPACE" \
				      "pod/rollback-health-${BUILD_NUMBER}"
				'''

				echo 'Rollback completed and application health verified.'
			} else {
			    echo 'No previous deployed revision exists; automatic rollback is unavailable.'
			}

			error('Helm deployment or health verification failed.')
		    }
		    finally {
			echo 'Cleaning up temporary health-check Pod...'

			sh '''
			    kubectl delete pod "webapp-health-${BUILD_NUMBER}" \
			      -n "$NAMESPACE" \
			      --ignore-not-found=true \
			      --wait=false
                            kubectl delete pod "rollback-health-${BUILD_NUMBER}" \
                              -n "$NAMESPACE" \
                              --ignore-not-found=true \
                              --wait=false
			'''
		    }
		}
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
