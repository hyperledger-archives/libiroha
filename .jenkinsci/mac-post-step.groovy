#!/usr/bin/env groovy

def macPostStep() {
    timeout(time: 600, unit: "SECONDS") {
        try {
        if (currentBuild.currentResult == "SUCCESS") {
            def artifacts = load ".jenkinsci/artifacts.groovy"
            def commit = GIT_COMMIT
            artifacts.uploadArtifacts(filePaths, sprintf('libiroha/macos/%1$s-%2$s-%3$s', [env.GIT_LOCAL_BRANCH, sh(script: 'date "+%Y%m%d"', returnStdout: true).trim(), commit.take(6)]))
        }
        }
        finally {
        cleanWs()
        sh """
            pg_ctl -D /var/jenkins/${GIT_COMMIT}-${BUILD_NUMBER}/ stop && \
            rm -rf /var/jenkins/${GIT_COMMIT}-${BUILD_NUMBER}/
        """
        }
    }
}

return this
