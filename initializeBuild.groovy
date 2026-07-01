#!/usr/bin/env groovy

def call() {

    // Default values
    env.committer   = "Unknown"
    env.commitId    = "N/A"
    env.commitMsg   = "No message"
    env.triggerType = "Unknown"

    // Git Information
    env.commitId     = sh(script: "git rev-parse --short HEAD", returnStdout: true).trim()
    env.commitMsg    = sh(script: "git log -1 --pretty=%B", returnStdout: true).trim()
    env.gitCommitter = sh(script: "git log -1 --pretty=%an", returnStdout: true).trim()

    // Build Trigger Detection
    def userCause = currentBuild.rawBuild.getCause(hudson.model.Cause.UserIdCause)
    def scmCause  = currentBuild.rawBuild.getCause(hudson.triggers.SCMTrigger.SCMTriggerCause)

    if (userCause) {

        env.triggerType = "Jenkins Manual"
        env.committer   = userCause.getUserName()
        env.commitMsg   = "Manually triggered from Jenkins"

    } else if (scmCause) {

        env.triggerType = "Git Push"
        env.committer   = env.gitCommitter

    } else {

        env.triggerType = "Unknown"
        env.committer   = env.gitCommitter
    }

    echo """
=========================================
           Build Information
=========================================

Trigger Type : ${env.triggerType}
Triggered By : ${env.committer}
Commit ID    : ${env.commitId}
Commit Msg   : ${env.commitMsg}

=========================================
"""
}
