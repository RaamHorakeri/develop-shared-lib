#!/usr/bin/env groovy

def call(String agentName, String imageName, String environment, String imageTag, String branch, String repoUrl, String credentialsId, String envVarRepo, String teamsWebhookUrl) {

    node(agentName) {

        try {

            stage('Checkout') {

                echo "📦 Checking out repository: ${repoUrl}, Branch: ${branch}"
                echo "⚙️ Loading environment configuration for: ${envVarRepo}"

                def configData    = loadConfig(imageName, environment, branch, repoUrl, credentialsId, envVarRepo)
                def serviceConfig = configData.services[imageName]?.environments[environment]

                if (!serviceConfig) {
                    error("❌ No configuration found for image: '${imageName}' and environment: '${environment}' in ${envVarRepo}")
                }

                repoUrl       = serviceConfig.repoUrl ?: repoUrl
                credentialsId = serviceConfig.credentialsId ?: credentialsId
                def envVars   = serviceConfig.envVars ?: [:]

                echo """
                ✅ Loaded Config:
                   🔹 repoUrl:       ${repoUrl}
                   🔹 credentialsId: ${credentialsId}
                   🔹 envVarRepo:    ${envVarRepo}
                   🔹 envVars:       ${envVars.keySet().join(', ') ?: 'None'}
                    """

                envVars.each { key, jenkinsCredId ->
                    withCredentials([string(credentialsId: jenkinsCredId, variable: key)]) {
                        env."${key}" = "${env[key]}"
                        echo "🔐 Loaded secret: ${key} from credentialsId: ${jenkinsCredId}"
                    }
                }

                checkout([
                    $class: 'GitSCM',
                    branches: [[name: "*/${branch}"]],
                    userRemoteConfigs: [[
                        url: repoUrl,
                        credentialsId: credentialsId
                    ]]
                ])

                initializeBuild()

                env.IMAGE_NAME  = imageName
                env.IMAGE_TAG   = imageTag
                env.ENVIRONMENT = environment
                env.BRANCH      = branch
                env.REPO_URL    = repoUrl
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

                sh "docker image prune -af || true"
            }

        } catch (err) {

            echo "❌ Pipeline failed: ${err.getMessage()}"
            currentBuild.result = "FAILURE"
            throw err

        } finally {

            postActions(
                branch,
                repoUrl,
                environment,
                teamsWebhookUrl
            )

        }
    }
}
