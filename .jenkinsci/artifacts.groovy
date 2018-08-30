#!/usr/bin/env groovy

def uploadArtifacts(filePath, uploadPath, artifactServers=['artifact.soramitsu.co.jp']) {
  def filePathsConverted = []
  agentType = sh(script: 'uname', returnStdout: true).trim()
  filePath.each {
    fp = sh(script: "ls -d ${it} | tr '\n' ','", returnStdout: true).trim()
    filePathsConverted.addAll(fp.split(','))
  }
  def shaSumBinary = 'sha256sum'
  def md5SumBinary = 'md5sum'
  def gpgKeyBinary = 'gpg --armor --detach-sign --no-tty --batch --yes --passphrase-fd 0'
  if (agentType == 'Darwin') {
    shaSumBinary = 'shasum -a 256'
    md5SumBinary = 'md5 -r'
    gpgKeyBinary = 'GPG_TTY=\$(tty) gpg --pinentry-mode loopback --armor --detach-sign --no-tty --batch --yes --passphrase-fd 0'
  }

  withCredentials([file(credentialsId: 'ci_gpg_privkey', variable: 'CI_GPG_PRIVKEY'), string(credentialsId: 'ci_gpg_masterkey', variable: 'CI_GPG_MASTERKEY')]) {
    if (!agentType.contains('MSYS_NT')) {
      sh "gpg --yes --batch --no-tty --import ${CI_GPG_PRIVKEY} || true"
    }
    filePathsConverted.each {
      sh "$shaSumBinary ${it} | cut -d' ' -f1 > \$(pwd)/\$(basename ${it}).sha256"
      sh "$md5SumBinary ${it} | cut -d' ' -f1 > \$(pwd)/\$(basename ${it}).md5"
      sh "echo \"${CI_GPG_MASTERKEY}\" | $gpgKeyBinary -o \$(pwd)/\$(basename ${it}).asc ${it}"
    }
  }

// withCredentials([usernamePassword(credentialsId: 'ci_nexus', passwordVariable: 'NEXUS_PASS', usernameVariable: 'NEXUS_USER')]) {
//     artifactServers.each {
//       filePath.each {
//         sh(script: "for file in ${it}/*; do curl -u ${NEXUS_USER}:${NEXUS_PASS} --upload-file \${file} https://nexus.iroha.tech/repository/artifacts/${uploadPath}/; done", reutrnStdout: true)
//       }
//     }
//   }
}

return this