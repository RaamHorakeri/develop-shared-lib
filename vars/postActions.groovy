#!/usr/bin/env groovy

def call(String branch,
         String repoUrl,
         String environment,
         String teamsWebhookUrl) {

    stage('Post Actions') {

        script {

            def buildResult = currentBuild.result ?: currentBuild.currentResult ?: "UNKNOWN"

            def committer = env.committer ?: env.gitCommitter ?: "Unknown"
            def commitId  = env.commitId ?: "N/A"
            def commitMsg = env.commitMsg ?: "No message"
            def repoName  = repoUrl ?: "Unknown repo"

            def millis  = currentBuild.duration ?: 0
            def seconds = (millis / 1000) as int
            def minutes = (seconds / 60) as int
            seconds = seconds % 60

            def duration = "${minutes} min ${seconds} sec"

            def timestamp = new Date().format(
                "yyyy-MM-dd HH:mm:ss",
                TimeZone.getTimeZone("Asia/Kolkata")
            )

            def status

            switch (buildResult) {

                case "SUCCESS":
                    status = "✅ SUCCESS"
                    break

                case "FAILURE":
                    status = "❌ FAILURE"
                    break

                case "ABORTED":
                    status = "🚫 ABORTED"
                    break

                default:
                    status = "⚠️ ${buildResult}"
            }

            currentBuild.displayName = "#${env.BUILD_NUMBER} - ${branch}"

            currentBuild.description = """${status}<br>
🚀 Trigger Type: <b>${env.triggerType}</b><br>
🔥 Triggered by: <b>${committer}</b><br>
🌿 Branch: <b>${branch}</b><br>
🧱 Commit: <b>${commitId}</b><br>
💬 Message: <b>${commitMsg}</b><br>
📦 Repository: <b>${repoName}</b><br>
🐳 Image: <b>${env.IMAGE_NAME}:${env.IMAGE_TAG}</b><br>
🌍 Environment: <b>${environment}</b><br>
🔢 Build Number: <b>#${env.BUILD_NUMBER}</b><br>
⏱ Duration: <b>${duration}</b><br>
🕒 Time: <b>${timestamp}</b>"""

            def webhookValue = ""

            try {

                withCredentials([
                    string(
                        credentialsId: teamsWebhookUrl,
                        variable: "TEAMS_WEBHOOK"
                    )
                ]) {

                    webhookValue = TEAMS_WEBHOOK
                }

            } catch (Exception e) {

                webhookValue = teamsWebhookUrl
            }

            try {

                if (webhookValue?.trim()) {

                    def payload = """
{
  "@type":"MessageCard",
  "@context":"https://schema.org/extensions",
  "summary":"Deployment Status",
  "themeColor":"${buildResult == 'SUCCESS' ? '00FF00' : buildResult == 'FAILURE' ? 'FF0000' : 'FFA500'}",
  "title":"Notification from ${env.JOB_NAME.replace('/', ' » ')}",
  "sections":[
    {
      "text":"${status}",
      "facts":[
        {
          "name":"📁 Job",
          "value":"${env.JOB_NAME.replace('/', ' » ')}"
        },
        {
          "name":"🚀 Trigger Type",
          "value":"${env.triggerType}"
        },
        {
          "name":"🔥 Triggered By",
          "value":"${committer}"
        },
        {
          "name":"🌿 Branch",
          "value":"${branch}"
        },
        {
          "name":"🧱 Commit",
          "value":"${commitId}"
        },
        {
          "name":"💬 Message",
          "value":"${commitMsg}"
        },
        {
          "name":"📦 Repository",
          "value":"${repoName}"
        },
        {
          "name":"🐳 Docker Image",
          "value":"${env.IMAGE_NAME}:${env.IMAGE_TAG}"
        },
        {
          "name":"🌍 Environment",
          "value":"${environment}"
        },
        {
          "name":"🔢 Build Number",
          "value":"#${env.BUILD_NUMBER}"
        },
        {
          "name":"⏱ Duration",
          "value":"${duration}"
        },
        {
          "name":"🕒 Time",
          "value":"${timestamp}"
        },
        {
          "name":"Status",
          "value":"${buildResult}"
        },
        {
          "name":"🔗 Build URL",
          "value":"${env.BUILD_URL}"
        },
        {
          "name":"📄 Console Log",
          "value":"${env.BUILD_URL}console"
        }
      ]
    }
  ]
}
"""
                    writeFile(
                        file: "teams-payload.json",
                        text: payload
                    )

                    sh """
                    set +x
                    curl -s -X POST \
                      -H "Content-Type: application/json" \
                      --data @teams-payload.json \
                      "${webhookValue}" > /dev/null
                    set -x
                    """

                }

            } catch (Exception ex) {

                echo "Teams notification skipped: ${ex.message}"

            }

            echo "🏁 Build completed with status: ${buildResult}"

        }
    }
}

