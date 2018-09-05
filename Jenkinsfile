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
  string(defaultValue: 'tech.iroha.libiroha', description: 'Java bindings package name', name: 'JBPackageName'),
  booleanParam(defaultValue: true, description: 'Build Python2 bindings', name: 'Python2Bindings'),
  booleanParam(defaultValue: false, description: 'Build Python3 bindings', name: 'Python3Bindings'),
  choice(choices: 'Release\nDebug', description: 'Python bindings build type', name: 'PBBuildType'),
  string(defaultValue: '4', description: 'How much parallelism should we exploit. "4" is optimal for machines with modest amount of memory and at least 4 cores', name: 'PARALLELISM')])])

pipeline {
  environment {
    CCACHE_DIR = '/opt/.ccache'
    CCACHE_RELEASE_DIR = '/opt/.ccache-release'
    SORABOT_TOKEN = credentials('SORABOT_TOKEN')
    SONAR_TOKEN = credentials('SONAR_TOKEN')
    GIT_RAW_BASE_URL = "https://raw.githubusercontent.com/hyperledger/libiroha"
    DOCKER_REGISTRY_BASENAME = "hyperledger/libiroha"

    IROHA_NETWORK = "iroha-0${CHANGE_ID}-${GIT_COMMIT}-${BUILD_NUMBER}"
    IROHA_POSTGRES_HOST = "pg-0${CHANGE_ID}-${GIT_COMMIT}-${BUILD_NUMBER}"
    IROHA_POSTGRES_USER = "pguser${GIT_COMMIT}"
    IROHA_POSTGRES_PASSWORD = "${GIT_COMMIT}"
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
            CHANGE_BRANCH_LOCAL = CHANGE_BRANCH
          }
          catch(MissingPropertyException e) { }
          if (GIT_LOCAL_BRANCH != "develop" && CHANGE_BRANCH_LOCAL != "develop") {
            def builds = load ".jenkinsci/cancel-builds-same-job.groovy"
            builds.cancelSameJobBuilds()
          }
        }
      }
    }
    stage('Build native library') {
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
          agent { label 'docker_1' }
          steps {
            script {
              debugBuild = load ".jenkinsci/linux-debug-build.groovy"
              debugBuild.doDebugBuild()
            }
          }
          post {    
            cleanup {
              script {
                def cleanup = load ".jenkinsci/docker-cleanup.groovy"
                cleanup.doDockerCleanup()
                cleanWs()
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
              debugBuild = load ".jenkinsci/mac-debug-build.groovy"
              debugBuild.doDebugBuild()
            }
          }
          post {
            always {
              script {
                cleanWs()
                sh """
                    pg_ctl -D /var/jenkins/${GIT_COMMIT}-${BUILD_NUMBER}/ stop && \
                    rm -rf /var/jenkins/${GIT_COMMIT}-${BUILD_NUMBER}/
                """
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
              def iC = dPullOrBuild.dockerPullOrUpdate(
                "$platform-develop-build",
                "${GIT_RAW_BASE_URL}/${GIT_COMMIT}/docker/develop/Dockerfile",
                "${GIT_RAW_BASE_URL}/${GIT_PREVIOUS_COMMIT}/docker/develop/Dockerfile",
                "${GIT_RAW_BASE_URL}/develop/docker/develop/Dockerfile",
                ['PARALLELISM': params.PARALLELISM])
              if (params.JavaBindings) {
                iC.inside() {
                  bindings.doJavaBindings('linux', params.JBPackageName, params.JBBuildType)
                }
              }
              if (params.Python2Bindings) {
                iC.inside() {
                  bindings.doPythonBindings('2', 'linux', params.PBBuildType)
                }
              }
              if (params.Python3Bindings) {
                iC.inside() {
                  bindings.doPythonBindings('3', 'linux', params.PBBuildType)
                }
              }
            }
          }
          post {
            success {
              script {
                def artifacts = load ".jenkinsci/artifacts.groovy"
                if (params.JavaBindings) {
                  javaBindingsFilePaths = [ 'java-bindings-*.zip' ]
                  artifacts.uploadArtifacts(javaBindingsFilePaths, 'libiroha/bindings/java')
                }
                if (params.Python2Bindings || params.Python3Bindings) {
                  pythonBindingsFilePaths = [ 'python-bindings-*.zip' ]
                  artifacts.uploadArtifacts(pythonBindingsFilePaths, 'libiroha/bindings/python')
                }
              }
            }
            cleanup {
              sh "rm -rf /tmp/${GIT_COMMIT}"
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
              if (params.Python2Bindings) {
                bindings.doPythonBindings('windows', params.PBBuildType)
              }
              if (params.Python3Bindings) {
                bindings.doPythonBindings('windows', params.PBBuildType)
              }
            }
          }
          post {
            success {
              script {
                def artifacts = load ".jenkinsci/artifacts.groovy"
                if (params.JavaBindings) {
                  javaBindingsFilePaths = [ 'java-bindings-*.zip' ]
                  artifacts.uploadArtifacts(javaBindingsFilePaths, 'libiroha/bindings/java')
                }
                if (params.Python2Bindings || params.Python2Bindings) {
                  pythonBindingsFilePaths = [ 'python-bindings-*.zip' ]
                  artifacts.uploadArtifacts(pythonBindingsFilePaths, 'libiroha/bindings/python')
                }
              }
            }
            cleanup {
              sh "rm -rf /tmp/${GIT_COMMIT}"
              cleanWs()
            }
          }
        }
      }
    }
  }
}
