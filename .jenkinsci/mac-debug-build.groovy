#!/usr/bin/env groovy

def doDebugBuild() {
    def cmakeOptions = ""
    def scmVars = checkout scm
    IROHA_VERSION = "0x${scmVars.GIT_COMMIT}"
    IROHA_HOME = "/opt/iroha"
    IROHA_BUILD = "${IROHA_HOME}/build"

    sh """
    ccache --version
    ccache --show-stats
    ccache --zero-stats
    ccache --max-size=5G
    """
    sh """
    cmake \
        -DTESTING=ON \
        -H. \
        -Bbuild \
        -DCMAKE_BUILD_TYPE=${params.build_type} \
        -DIROHA_VERSION=${IROHA_VERSION} \
        ${cmakeOptions}
    """
    sh "cmake --build build -- -j${params.PARALLELISM}"
    sh "ccache --show-stats"
    sh """
    export IROHA_POSTGRES_PASSWORD=${IROHA_POSTGRES_PASSWORD}; \
    export IROHA_POSTGRES_USER=${IROHA_POSTGRES_USER}; \
    mkdir -p /var/jenkins/${GIT_COMMIT}-${BUILD_NUMBER}; \
    initdb -D /var/jenkins/${GIT_COMMIT}-${BUILD_NUMBER}/ -U ${IROHA_POSTGRES_USER} --pwfile=<(echo ${IROHA_POSTGRES_PASSWORD}); \
    pg_ctl -D /var/jenkins/${GIT_COMMIT}-${BUILD_NUMBER}/ -o '-p 5433' -l /var/jenkins/${GIT_COMMIT}-${BUILD_NUMBER}/events.log start; \
    psql -h localhost -d postgres -p 5433 -U ${IROHA_POSTGRES_USER} --file=<(echo create database ${IROHA_POSTGRES_USER};)
    """
    def testExitCode = sh(script: """cd build && IROHA_POSTGRES_HOST=localhost IROHA_POSTGRES_PORT=5433 ctest --output-on-failure """, returnStatus: true)
    if (testExitCode != 0) {
    currentBuild.result = "UNSTABLE"
    }
}

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
