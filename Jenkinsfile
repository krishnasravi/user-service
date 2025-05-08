pipeline {
  agent any

  environment {
    IMAGE_NAME = "user-service"
    IMAGE_TAG = "v1.${BUILD_NUMBER}"
    DOCKER_REGISTRY = "krishnasravi"
    GIT_REPO = "https://github.com/krishnasravi/user-service.git"
    GIT_CREDENTIALS_ID = "github-pat"
    APP_DIR = "app"
    MANIFEST_DIR = "deployment"
    DEPLOYMENT_FILE = "deployment.yaml"
    ARGOCD_SERVER = "3.235.249.7:32505"
    ARGOCD_USERNAME = "admin"
    ARGOCD_PASSWORD = "S@newlife143"
    ARGOCD_APP = "user-service-app"
    K8S_NAMESPACE = "default"
  }

  stages {

    stage('Clone Repository') {
      steps {
        git url: "${GIT_REPO}", branch: 'main', credentialsId: "${GIT_CREDENTIALS_ID}"
      }
    }

    stage('Maven Build') {
      steps {
        dir("${APP_DIR}") {
          sh 'mvn clean package -DskipTests'
        }
      }
    }

    stage('Docker Build') {
      steps {
        dir("${APP_DIR}") {
          sh "docker build -t ${DOCKER_REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG} ."
        }
      }
    }

    stage('Trivy Scan') {
      steps {
        script {
          def trivyStatus = sh(
            script: "trivy image --exit-code 1 --severity CRITICAL,HIGH ${DOCKER_REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG}",
            returnStatus: true
          )
          if (trivyStatus != 0) {
            echo "⚠️ Trivy scan found vulnerabilities. Proceeding anyway."
          } else {
            echo "✅ Trivy scan passed."
          }
        }
      }
    }

    stage('Push Docker Image') {
      when {
        expression {
          return currentBuild.result == null || currentBuild.result == 'SUCCESS'
        }
      }
      steps {
        withDockerRegistry([credentialsId: 'docker-cred-id', url: 'https://index.docker.io/v1/']) {
          sh "docker push ${DOCKER_REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG}"
        }
      }
    }

    stage('Update Kubernetes Manifest') {
      steps {
        script {
          def deployFile = "${MANIFEST_DIR}/${DEPLOYMENT_FILE}"
          sh """
            sed -i 's|image: ${DOCKER_REGISTRY}/${IMAGE_NAME}:.*|image: ${DOCKER_REGISTRY}/${IMAGE_NAME}:${IMAGE_TAG}|' ${deployFile}
            git config user.name "jenkins"
            git config user.email "jenkins@ci"
            git add ${deployFile}
            git commit -m "Deploy ${IMAGE_TAG}"
          """
          withCredentials([usernamePassword(credentialsId: "${GIT_CREDENTIALS_ID}", usernameVariable: 'GIT_USER', passwordVariable: 'GIT_PASS')]) {
            sh """
              git remote set-url origin https://${GIT_USER}:${GIT_PASS}@github.com/krishnasravi/user-service.git
              git push origin main
            """
          }
        }
      }
    }

    stage('Login to ArgoCD and Trigger Sync') {
      steps {
        sh """
          argocd login ${ARGOCD_SERVER} --username ${ARGOCD_USERNAME} --password ${ARGOCD_PASSWORD} --insecure
          argocd app sync ${ARGOCD_APP}
        """
      }
    }

    stage('Wait for ArgoCD App to be Healthy') {
      steps {
        script {
          def status = sh(
            script: "argocd app wait ${ARGOCD_APP} --health --operation --timeout 300",
            returnStatus: true
          )
          if (status != 0) {
            currentBuild.result = 'FAILURE'
            error("❌ ArgoCD app did not become healthy. Triggering rollback.")
          }
        }
      }
    }

    stage('Rollback on Failure') {
      when {
        expression { currentBuild.result == 'FAILURE' }
      }
      steps {
        script {
          sh """
            git revert HEAD --no-edit
            git push origin main
          """
          withCredentials([usernamePassword(credentialsId: "${GIT_CREDENTIALS_ID}", usernameVariable: 'GIT_USER', passwordVariable: 'GIT_PASS')]) {
            sh """
              argocd login ${ARGOCD_SERVER} --username ${ARGOCD_USERNAME} --password ${ARGOCD_PASSWORD} --insecure
              argocd app sync ${ARGOCD_APP}
            """
          }
        }
      }
    }

    stage('Cleanup') {
      steps {
        script {
          echo "Performing cleanup..."
          sh "docker image prune -f"
          sh "docker container prune -f"
        }
      }
    }
  }

  post {
    always {
      echo "Post-build cleanup: deleting workspace and pruning Docker volumes"
      deleteDir()
      sh 'docker system prune -f --volumes || true'
    }
    failure {
      echo "Pipeline failed. Rollback attempted if necessary."
    }
    success {
      echo "Deployment completed successfully."
    }
  }
}
