#!/usr/bin/env groovy

def call(String imageName, String environment, String branch, String repoUrl, String credentialsId, String envVarRepo) {

    // Case-insensitive environment mapping
    def commonEnvVarSets = [

        ui_deploy_var: [
            // Example:
            // API_KEY : 'jenkins-credential-id'
        ]

    ]

    // Normalize input
    def normalizedInput = envVarRepo?.trim()?.toLowerCase()

    def matchedKey = commonEnvVarSets.keySet().find {
        it.toLowerCase() == normalizedInput
    }

    if (!matchedKey) {
        error("❌ Invalid envVarRepo: '${envVarRepo}'. Valid options: ${commonEnvVarSets.keySet().join(', ')}")
    }

    def envVars = commonEnvVarSets[matchedKey] ?: [:]

    echo "📦 Checking out repository: ${repoUrl}, Branch: ${branch}"
    echo "⚙️ Loading environment configuration for: ${envVarRepo}"

    echo """
✅ Loaded Config:
   🔹 repoUrl:       ${repoUrl}
   🔹 credentialsId: ${credentialsId}
   🔹 envVarRepo:    ${envVarRepo}
   🔹 envVars:       ${envVars.keySet().join(', ') ?: 'None'}
"""

    // Load Jenkins credentials into environment variables
    envVars.each { key, jenkinsCredId ->

        withCredentials([
            string(credentialsId: jenkinsCredId, variable: key)
        ]) {

            env."${key}" = env[key]

            echo "🔐 Loaded secret: ${key} from credentialsId: ${jenkinsCredId}"
        }
    }

    // Export values for main pipeline
    env.REPO_URL = repoUrl
    env.CREDENTIALS_ID = credentialsId
    env.BRANCH = branch
    env.ENVIRONMENT = environment
}
