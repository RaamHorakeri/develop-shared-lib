def call(String imageName, String environment, String branch, String repoUrl, String credentialsId, String envVarRepo) {
    // Case-insensitive environment mapping with empty private
    def commonEnvVarSets = [
        ui_deploy_var: [:]
      ]    

    // Normalize input and find matching key (case-insensitive)
    def normalizedInput = envVarRepo?.trim()?.toLowerCase()
    def matchedKey = commonEnvVarSets.keySet().find { it.toLowerCase() == normalizedInput }

    if (!matchedKey) {
        error("Invalid envVarRepo: '${envVarRepo}'. Valid options: ${commonEnvVarSets.keySet().join(', ')}")
    }

    // Will return empty map for 'private'
    def envVars = commonEnvVarSets[matchedKey] ?: [:]

    return [
        services: [
            (imageName): [
                environments: [
                    (environment): [
                        agentName: '',
                        repoUrl: repoUrl,
                        branch: branch,
                        credentialsId: credentialsId,
                        envVars: envVars  // Empty for private
                    ]
                ]
            ]
        ]
    ]
}
