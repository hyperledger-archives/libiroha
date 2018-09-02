properties([parameters([
  booleanParam(defaultValue: true, description: 'Build `iroha`', name: 'iroha'),
  choice(choices: 'Debug\nRelease', description: 'Iroha build type', name: 'build_type'),
  booleanParam(defaultValue: true, description: 'Build `bindings`', name: 'bindings'),
  booleanParam(defaultValue: true, description: '', name: 'x86_64_linux'),
  booleanParam(defaultValue: false, description: '', name: 'armv7_linux'),
  booleanParam(defaultValue: false, description: '', name: 'armv8_linux'),
  booleanParam(defaultValue: true, description: '', name: 'x86_64_macos'),
  booleanParam(defaultValue: false, description: '', name: 'x86_64_win'),
  booleanParam(defaultValue: true, description: 'Build Java bindings', name: 'JavaBindings'),
  choice(choices: 'Release\nDebug', description: 'Java bindings build type', name: 'JBBuildType'),
  string(defaultValue: 'jp.co.soramitsu.iroha', description: 'Java bindings package name', name: 'JBPackageName'),
  booleanParam(defaultValue: false, description: 'Build Python bindings', name: 'PythonBindings'),
  choice(choices: 'Release\nDebug', description: 'Python bindings build type', name: 'PBBuildType'),
  choice(choices: 'python3\npython2', description: 'Python bindings version', name: 'PBVersion'),
  booleanParam(defaultValue: false, description: 'Build Android bindings', name: 'AndroidBindings'),
  choice(choices: '26\n25\n24\n23\n22\n21\n20\n19\n18\n17\n16\n15\n14', description: 'Android Bindings ABI Version', name: 'ABABIVersion'),
  choice(choices: 'Release\nDebug', description: 'Android bindings build type', name: 'ABBuildType'),
  choice(choices: 'arm64-v8a\narmeabi-v7a\narmeabi\nx86_64\nx86', description: 'Android bindings platform', name: 'ABPlatform'),
  string(defaultValue: '4', description: 'How much parallelism should we exploit. "4" is optimal for machines with modest amount of memory and at least 4 cores', name: 'PARALLELISM')])])

pipeline {
  environment {
    CCACHE_DIR = '/opt/.ccache'
    CCACHE_RELEASE_DIR = '/opt/.ccache-release'
    SORABOT_TOKEN = credentials('SORABOT_TOKEN')
    SONAR_TOKEN = credentials('SONAR_TOKEN')
    GIT_RAW_BASE_URL = "https://raw.githubusercontent.com/hyperledger/libiroha"
    DOCKER_REGISTRY_BASENAME = "hyperledger/libiroha"

    IROHA_NETWORK = "iroha-0${CHANGE_ID}-${env.GIT_COMMIT}-${BUILD_NUMBER}"
    IROHA_POSTGRES_HOST = "pg-0${CHANGE_ID}-${env.GIT_COMMIT}-${BUILD_NUMBER}"
    IROHA_POSTGRES_USER = "pguser${env.GIT_COMMIT}"
    IROHA_POSTGRES_PASSWORD = "${env.GIT_COMMIT}"
    IROHA_POSTGRES_PORT = 5432
    CHANGE_BRANCH_LOCAL = ''
  }

  options {
    buildDiscarder(logRotator(numToKeepStr: '20'))
    timestamps()
  }

  agent any
  stages {
    stage ('Stop same job builds') {
      agent { label 'master' }
      steps {
        script {
          // need this for develop->master PR cases
          // CHANGE_BRANCH is not defined if this is a branch build
          try {
            CHANGE_BRANCH_LOCAL = env.CHANGE_BRANCH
          }
          catch(MissingPropertyException e) { }
          if (env.GIT_LOCAL_BRANCH != "develop" && env.CHANGE_BRANCH_LOCAL != "develop") {
            def builds = load ".jenkinsci/cancel-builds-same-job.groovy"
            builds.cancelSameJobBuilds()
          }
        }
      }
    }
    stage('Build Debug') {
      when {
        allOf {
          expression { params.build_type == 'Debug' }
          expression { return params.iroha }
        }
      }
      parallel {
        stage ('x86_64_linux') {
          when {
            beforeAgent true
            expression { return params.x86_64_linux }
          }
          agent { label 'x86_64' }
          steps {
            script {
              debugBuild = load ".jenkinsci/debug-build.groovy"
              debugBuild.doDebugBuild()
              if (env.GIT_LOCAL_BRANCH ==~ /(master|develop)/) {
                releaseBuild = load ".jenkinsci/release-build.groovy"
                releaseBuild.doReleaseBuild()
              }
            }
          }
          post {
            always {
              script {
                post = load ".jenkinsci/linux-post-step.groovy"
                post.linuxPostStep()
              }
            }
          }
        }
        stage('x86_64_macos'){
          when {
            beforeAgent true
            expression { return params.x86_64_macos }
          }
          agent { label 'mac' }
          steps {
            script {
              def cmakeOptions = ""
              def scmVars = checkout scm
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
                  -DCMAKE_BUILD_TYPE=${params.build_type} \
                  -DIROHA_VERSION=${env.IROHA_VERSION} \
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
              if (env.GIT_LOCAL_BRANCH ==~ /(master|develop)/) {
                releaseBuild = load ".jenkinsci/mac-release-build.groovy"
                releaseBuild.doReleaseBuild()
              }
            }
          }
          post {
            always {
              script {
                timeout(time: 600, unit: "SECONDS") {
                  try {
                    if (currentBuild.currentResult == "SUCCESS" && env.GIT_LOCAL_BRANCH ==~ /(master|develop)/) {
                      def artifacts = load ".jenkinsci/artifacts.groovy"
                      def commit = env.GIT_COMMIT
                      filePaths = [ '\$(pwd)/build/*.tar.gz' ]
                      // artifacts.uploadArtifacts(filePaths, sprintf('/iroha/macos/%1$s-%2$s-%3$s', [env.GIT_LOCAL_BRANCH, sh(script: 'date "+%Y%m%d"', returnStdout: true).trim(), commit.substring(0,6)]))
                    }
                  }
                  finally {
                    cleanWs()
                    sh """
                      pg_ctl -D /var/jenkins/${env.GIT_COMMIT}-${BUILD_NUMBER}/ stop && \
                      rm -rf /var/jenkins/${env.GIT_COMMIT}-${BUILD_NUMBER}/
                    """
                  }
                }
              }
            }
          }
        }
      }
    }
    stage('Build Release') {
      when {
        expression { params.build_type == 'Release' }
        expression { return params.iroha }
      }
      parallel {
        stage('x86_64_linux') {
          when {
            beforeAgent true
            expression { return params.x86_64_linux }
          }
          agent { label 'x86_64' }
          steps {
            script {
              def releaseBuild = load ".jenkinsci/release-build.groovy"
              releaseBuild.doReleaseBuild()
            }
          }
          post {
            always {
              script {
                post = load ".jenkinsci/linux-post-step.groovy"
                post.linuxPostStep()
              }
            }
          }
        }
        stage('x86_64_macos') {
          when {
            beforeAgent true
            expression { return params.x86_64_macos }
          }
          agent { label 'mac' }
          steps {
            script {
              def releaseBuild = load ".jenkinsci/mac-release-build.groovy"
              releaseBuild.doReleaseBuild()
            }
          }
          post {
            always {
              script {
                timeout(time: 600, unit: "SECONDS") {
                  try {
                    if (currentBuild.currentResult == "SUCCESS" && env.GIT_LOCAL_BRANCH ==~ /(master|develop)/) {
                      def artifacts = load ".jenkinsci/artifacts.groovy"
                      def commit = env.GIT_COMMIT
                      filePaths = [ '\$(pwd)/build/*.tar.gz' ]
                      // artifacts.uploadArtifacts(filePaths, sprintf('/iroha/macos/%1$s-%2$s-%3$s', [env.GIT_LOCAL_BRANCH, sh(script: 'date "+%Y%m%d"', returnStdout: true).trim(), commit.substring(0,6)]))
                    }
                  }
                  finally {
                    cleanWs()
                  }
                }
              }
            }
          }
        }
      }
    }
    stage('Build bindings') {
      when {
        beforeAgent true
        expression { return params.bindings }
      }
      parallel {
        stage('Linux bindings') {
          when {
            beforeAgent true
            expression { return params.x86_64_linux }
          }
          agent { label 'x86_64' }
          environment {
            JAVA_HOME = "/usr/lib/jvm/java-8-oracle"
          }
          steps {
            script {
              def bindings = load ".jenkinsci/bindings.groovy"
              def dPullOrBuild = load ".jenkinsci/docker-pull-or-build.groovy"
              def platform = sh(script: 'uname -m', returnStdout: true).trim()
              if (params.JavaBindings || params.PythonBindings) {
                def iC = dPullOrBuild.dockerPullOrUpdate(
                  "$platform-develop-build",
                  "${env.GIT_RAW_BASE_URL}/${env.GIT_COMMIT}/docker/develop/Dockerfile",
                  "${env.GIT_RAW_BASE_URL}/${env.GIT_PREVIOUS_COMMIT}/docker/develop/Dockerfile",
                  "${env.GIT_RAW_BASE_URL}/develop/docker/develop/Dockerfile",
                  ['PARALLELISM': params.PARALLELISM])
                if (params.JavaBindings) {
                  iC.inside("-v /tmp/${env.GIT_COMMIT}/bindings-artifact:/tmp/bindings-artifact") {
                    bindings.doJavaBindings('linux', params.JBPackageName, params.JBBuildType)
                  }
                }
                if (params.PythonBindings) {
                  iC.inside("-v /tmp/${env.GIT_COMMIT}/bindings-artifact:/tmp/bindings-artifact") {
                    bindings.doPythonBindings('linux', params.PBBuildType)
                  }
                }
              }
              if (params.AndroidBindings) {
                def iC = dPullOrBuild.dockerPullOrUpdate(
                  "android-${params.ABPlatform}-${params.ABBuildType}",
                  "${env.GIT_RAW_BASE_URL}/${env.GIT_COMMIT}/docker/android/Dockerfile",
                  "${env.GIT_RAW_BASE_URL}/${env.GIT_PREVIOUS_COMMIT}/docker/android/Dockerfile",
                  "${env.GIT_RAW_BASE_URL}/develop/docker/android/Dockerfile",
                  ['PARALLELISM': params.PARALLELISM, 'PLATFORM': params.ABPlatform, 'BUILD_TYPE': params.ABBuildType])
                iC.inside("-v /tmp/${env.GIT_COMMIT}/bindings-artifact:/tmp/bindings-artifact") {
                  bindings.doAndroidBindings(params.ABABIVersion)
                }
              }
            }
          }
          post {
            success {
              script {
                def artifacts = load ".jenkinsci/artifacts.groovy"
                if (params.JavaBindings) {
                  javaBindingsFilePaths = [ '/tmp/${env.GIT_COMMIT}/bindings-artifact/java-bindings-*.zip' ]
                  artifacts.uploadArtifacts(javaBindingsFilePaths, '/libiroha/bindings/java')
                }
                if (params.PythonBindings) {
                  pythonBindingsFilePaths = [ '/tmp/${env.GIT_COMMIT}/bindings-artifact/python-bindings-*.zip' ]
                  artifacts.uploadArtifacts(pythonBindingsFilePaths, '/libiroha/bindings/python')
                }
                if (params.AndroidBindings) {
                  androidBindingsFilePaths = [ '/tmp/${env.GIT_COMMIT}/bindings-artifact/android-bindings-*.zip' ]
                  artifacts.uploadArtifacts(androidBindingsFilePaths, '/libiroha/bindings/android')
                }
              }
            }
            cleanup {
              // sh "rm -rf /tmp/${env.GIT_COMMIT}"
              cleanWs()
            }
          }
        }
        stage ('Windows bindings') {
          when {
            beforeAgent true
            expression { return params.x86_64_win }
          }
          agent { label 'win' }
          steps {
            script {
              def bindings = load ".jenkinsci/bindings.groovy"
              if (params.JavaBindings) {
                bindings.doJavaBindings('windows', params.JBPackageName, params.JBBuildType)
              }
              if (params.PythonBindings) {
                bindings.doPythonBindings('windows', params.PBBuildType)
              }
            }
          }
          post {
            success {
              script {
                def artifacts = load ".jenkinsci/artifacts.groovy"
                if (params.JavaBindings) {
                  javaBindingsFilePaths = [ '/tmp/${env.GIT_COMMIT}/bindings-artifact/java-bindings-*.zip' ]
                  artifacts.uploadArtifacts(javaBindingsFilePaths, '/iroha/bindings/java')
                }
                if (params.PythonBindings) {
                  pythonBindingsFilePaths = [ '/tmp/${env.GIT_COMMIT}/bindings-artifact/python-bindings-*.zip' ]
                  artifacts.uploadArtifacts(pythonBindingsFilePaths, '/iroha/bindings/python')
                }
              }
            }
            cleanup {
              sh "rm -rf /tmp/${env.GIT_COMMIT}"
              cleanWs()
            }
          }
        }
      }
    }
  }
}
