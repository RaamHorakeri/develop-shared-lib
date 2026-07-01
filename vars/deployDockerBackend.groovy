#!/usr/bin/env groovy

def call(String agentName, String imageName, String environment, String imageTag, String branch, String repoUrl, String credentialsId, String envVarRepo, String teamsWebhookUrl) {

    node(agentName) {

        try {

            stage('Checkout') {

                // Load repository/configuration
                loadConfig(
                    imageName,
                    environment,
                    branch,
                    repoUrl,
                    credentialsId,
                    envVarRepo
                )

                // Checkout source code
                checkout([
                    $class: 'GitSCM',
                    branches: [[name: "*/${branch}"]],
                    userRemoteConfigs: [[
                        url: env.REPO_URL,
                        credentialsId: env.CREDENTIALS_ID
                    ]]
                ])

                // Load Git commit information
                initializeBuild()

                // Deployment variables
                env.IMAGE_NAME  = imageName
                env.IMAGE_TAG   = imageTag
                env.ENVIRONMENT = environment
                env.BRANCH      = branch
            }

            stage('Build Docker Image') {

                echo "🐳 Building Docker image: ${env.IMAGE_NAME}:${env.IMAGE_TAG}"

                sh """
                docker build -t ${env.IMAGE_NAME}:${env.IMAGE_TAG} .
                """
            }

            stage('Deploy with Docker Compose') {

                echo "🚀 Deploying ${env.IMAGE_NAME}:${env.IMAGE_TAG} using Docker Compose..."

                sh """
                docker compose up -d --force-recreate
                """
            }

            stage('Verify Container Status') {

                echo "🔍 Checking container running status for: ${env.IMAGE_NAME}"

                sh """
                STATUS=\$(docker inspect -f '{{.State.Running}}' ${env.IMAGE_NAME} 2>/dev/null || echo false)

                if [ "\$STATUS" != "true" ]; then
                    echo "❌ Container ${env.IMAGE_NAME} is NOT running"
                    docker ps -a
                    exit 1
                fi

                echo "✅ Container ${env.IMAGE_NAME} is running"
                """
            }

            stage('Application Health Check (Logs)') {

                echo "📜 Fetching recent logs for ${env.IMAGE_NAME}"

                sh """
                echo "------ Last 100 lines of logs ------"
                docker logs --tail 100 ${env.IMAGE_NAME}
                """

                echo "✅ Logs fetched successfully, application appears to be running"
            }

            stage('Cleanup Docker Images') {

                echo "🧹 Cleaning up unused Docker images..."

                sh """
                docker image prune -af || true
                """
            }

        } catch (err) {

            echo "❌ Pipeline failed: ${err.getMessage()}"
            currentBuild.result = "FAILURE"
            throw err

        } finally {

            postActions(
                branch,
                env.REPO_URL,
                environment,
                teamsWebhookUrl
            )

        }
    }
}
