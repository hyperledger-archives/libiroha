#!/usr/bin/env groovy

def doDebugBuild(coverageEnabled=false) {
  def dPullOrBuild = load ".jenkinsci/docker-pull-or-build.groovy"
  def manifest = load ".jenkinsci/docker-manifest.groovy"
  def pCommit = load ".jenkinsci/previous-commit.groovy"
  def parallelism = params.PARALLELISM
  def platform = sh(script: 'uname -m', returnStdout: true).trim()
  def previousCommit = pCommit.previousCommitOrCurrent()
  // params are always null unless job is started
  // this is the case for the FIRST build only.
  // So just set this to same value as default.
  // This is a known bug. See https://issues.jenkins-ci.org/browse/JENKINS-41929
  if (!parallelism) {
    parallelism = 4
  }
  if (env.NODE_NAME.contains('arm7')) {
    parallelism = 1
  }

  sh "docker network create ${env.IROHA_NETWORK}"
  def iC = dPullOrBuild.dockerPullOrUpdate("${platform}-develop-build",
                                           "${env.GIT_RAW_BASE_URL}/${env.GIT_COMMIT}/docker/develop/Dockerfile",
                                           "${env.GIT_RAW_BASE_URL}/${previousCommit}/docker/develop/Dockerfile",
                                           "${env.GIT_RAW_BASE_URL}/develop/docker/develop/Dockerfile",
                                           ['PARALLELISM': parallelism])
  // push Docker image in case the current branch is develop,
  // or it is a commit into PR which base branch is develop (usually develop -> master)
  if ((env.GIT_LOCAL_BRANCH == 'develop' || env.CHANGE_BRANCH_LOCAL == 'develop') && manifest.manifestSupportEnabled()) {
    manifest.manifestCreate("${env.DOCKER_REGISTRY_BASENAME}:develop-build",
      ["${env.DOCKER_REGISTRY_BASENAME}:x86_64-develop-build",
       "${env.DOCKER_REGISTRY_BASENAME}:armv7l-develop-build",
       "${env.DOCKER_REGISTRY_BASENAME}:aarch64-develop-build"])
    manifest.manifestAnnotate("${env.DOCKER_REGISTRY_BASENAME}:develop-build",
      [
        [manifest: "${env.DOCKER_REGISTRY_BASENAME}:x86_64-develop-build",
         arch: 'amd64', os: 'linux', osfeatures: [], variant: ''],
        [manifest: "${env.DOCKER_REGISTRY_BASENAME}:armv7l-develop-build",
         arch: 'arm', os: 'linux', osfeatures: [], variant: 'v7'],
        [manifest: "${env.DOCKER_REGISTRY_BASENAME}:aarch64-develop-build",
         arch: 'arm64', os: 'linux', osfeatures: [], variant: '']
      ])
    withCredentials([usernamePassword(credentialsId: 'docker-hub-credentials', usernameVariable: 'login', passwordVariable: 'password')]) {
      manifest.manifestPush("${env.DOCKER_REGISTRY_BASENAME}:develop-build", login, password)
    }
  }

  docker.image('postgres:9.5').withRun(""
    + " -e POSTGRES_USER=${env.IROHA_POSTGRES_USER}"
    + " -e POSTGRES_PASSWORD=${env.IROHA_POSTGRES_PASSWORD}"
    + " --name ${env.IROHA_POSTGRES_HOST}"
    + " --network=${env.IROHA_NETWORK}") {
      iC.inside(""
      + " -e IROHA_POSTGRES_HOST=${env.IROHA_POSTGRES_HOST}"
      + " -e IROHA_POSTGRES_PORT=${env.IROHA_POSTGRES_PORT}"
      + " -e IROHA_POSTGRES_USER=${env.IROHA_POSTGRES_USER}"
      + " -e IROHA_POSTGRES_PASSWORD=${env.IROHA_POSTGRES_PASSWORD}"
      + " --network=${env.IROHA_NETWORK}"
      + " -v /var/jenkins/ccache:${CCACHE_DIR}"
      + " -v /tmp/${env.GIT_COMMIT}-${BUILD_NUMBER}:/tmp/${env.GIT_COMMIT}") {

      def scmVars = checkout scm
      def cmakeOptions = ""
      if ( coverageEnabled ) {
        cmakeOptions = " -DCOVERAGE=ON "
      }
      env.IROHA_VERSION = "0x${scmVars.GIT_COMMIT}"
      env.IROHA_HOME = "/opt/iroha"
      env.IROHA_BUILD = "${env.IROHA_HOME}/build"

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
          -DCMAKE_BUILD_TYPE=Debug \
          -DIROHA_VERSION=${env.IROHA_VERSION} \
          ${cmakeOptions}
      """
      sh "cmake --build build -- -j${parallelism}"
      sh "ccache --show-stats"
      def testExitCode = sh(script: """cd build && ctest --output-on-failure""", returnStatus: true)
      if (testExitCode != 0) {
        currentBuild.result = "UNSTABLE"
      }
    }
  }
}

def linuxPostStep() {
  timeout(time: 600, unit: "SECONDS") {
    try {
      // if (currentBuild.currentResult == "SUCCESS" && env.GIT_LOCAL_BRANCH ==~ /(master|develop)/) {
        def artifacts = load ".jenkinsci/artifacts.groovy"
        def commit = env.GIT_COMMIT
        def platform = sh(script: 'uname -m', returnStdout: true).trim()
        filePaths = [ '/tmp/${GIT_COMMIT}-${BUILD_NUMBER}/*' ]
        sh "ls -al /tmp/${GIT_COMMIT}-${BUILD_NUMBER}/" 
        artifacts.uploadArtifacts(filePaths, sprintf('libiroha/linux/%4$s/%1$s-%2$s-%3$s', ["develop", sh(script: 'date "+%Y%m%d"', returnStdout: true).trim(), commit.take(6), platform]))
      // }
    }
    finally {
      def cleanup = load ".jenkinsci/docker-cleanup.groovy"
      cleanup.doDockerCleanup()
      // cleanWs()
    }
  }
}

return this
