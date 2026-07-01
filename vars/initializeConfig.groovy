echo "📦 Checking out repository: ${repoUrl}, Branch: ${branch}"
echo "⚙️ Loading environment configuration for: ${envVarRepo}"

def configData = loadConfig(imageName, environment, branch, repoUrl, credentialsId, envVarRepo)
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
        env."${key}" = env[key]
        echo "🔐 Loaded secret: ${key} from credentialsId: ${jenkinsCredId}"
    }
}

env.REPO_URL = repoUrl
env.CREDENTIALS_ID = credentialsId
