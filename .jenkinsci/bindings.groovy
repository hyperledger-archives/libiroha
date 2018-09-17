#!/usr/bin/env groovy

def doJavaBindings(os, packageName, buildType="Release") {
  def currentPath = sh(script: "pwd", returnStdout: true).trim()
  def commit = GIT_COMMIT
  def artifactsPath = sprintf('%1$s/java-bindings-%2$s-%3$s-%4$s-%5$s.zip',
    [currentPath, buildType, os, sh(script: 'date "+%Y%m%d"', returnStdout: true).trim(), commit.substring(0,6)])
  def cmakeOptions = ""
  if (os == 'windows') {
    sh "mkdir -p /tmp/${GIT_COMMIT}/bindings-artifact"
    cmakeOptions = '-DCMAKE_TOOLCHAIN_FILE=/c/Users/Administrator/Downloads/vcpkg-master/vcpkg-master/scripts/buildsystems/vcpkg.cmake -G "NMake Makefiles"'
  }
  if (os == 'linux') {
    // do not use preinstalled libed25519
    sh "rm -rf /usr/local/include/ed25519*; unlink /usr/local/lib/libed25519.so; rm -f /usr/local/lib/libed25519.so.1.2.2"
  }
  sh """
    cmake . \
      -Bbuild \
      -DCMAKE_BUILD_TYPE=$buildType \
      -DSWIG_JAVA=ON \
      -DSWIG_JAVA_PKG="$packageName" \
      ${cmakeOptions}
  """
  def parallelismParam = (os == 'windows') ? '' : "-j${params.PARALLELISM}"
  sh "cmake --build build --target irohajava -- ${parallelismParam}"
  // TODO 29.05.18 @bakhtin Java tests never finishes on Windows Server 2016. IR-1380
  sh "pushd build/bindings; \
      zip -r $artifactsPath *.dll *.lib *.manifest *.exp libirohajava.so \$(echo ${packageName} | cut -d '.' -f1); \
      popd"
  if (os == 'windows') {
    sh "cp $artifactsPath /tmp/${GIT_COMMIT}/bindings-artifact"
  }
  else {
    sh "cp $artifactsPath /tmp/bindings-artifact"
  }
  return artifactsPath
}

def doPythonBindings(PBversion, os, buildType="Release") {
  def currentPath = sh(script: "pwd", returnStdout: true).trim()
  def commit = GIT_COMMIT
  def supportPython2 = PBversion == "2" ? "ON" : "OFF"
  def version = PBversion == "2" ? "python2" : "python3"
  def artifactsPath = sprintf('%1$s/python-bindings-%2$s-%3$s-%4$s-%5$s-%6$s.zip',
    [currentPath, version, buildType, os, sh(script: 'date "+%Y%m%d"', returnStdout: true).trim(), commit.substring(0,6)])
  def cmakeOptions = ""
  if (os == 'windows') {
    sh "mkdir -p /tmp/${GIT_COMMIT}/bindings-artifact"
    cmakeOptions = '-DCMAKE_TOOLCHAIN_FILE=/c/Users/Administrator/Downloads/vcpkg-master/vcpkg-master/scripts/buildsystems/vcpkg.cmake -G "NMake Makefiles"'
  }
  if (os == 'linux') {
    // do not use preinstalled libed25519
    sh "rm -rf /usr/local/include/ed25519*; unlink /usr/local/lib/libed25519.so; rm -f /usr/local/lib/libed25519.so.1.2.2"
  }
  sh """
    cmake . \
      -Bbuild \
      -DCMAKE_BUILD_TYPE=$buildType \
      -DSWIG_PYTHON=ON \
      -DSUPPORT_PYTHON2=$supportPython2 \
      ${cmakeOptions}
  """
  def parallelismParam = (os == 'windows') ? '' : "-j${params.PARALLELISM}"
  sh "cmake --build build --target irohapy -- ${parallelismParam}"
  sh "cmake --build build --target python_tests"
  sh "cd build; ctest -R python --output-on-failure"
  if (os == 'linux') {
    sh """
      protoc --proto_path=schema \
        --python_out=build/bindings schema/*.proto
    """
    sh """
      ${version} -m grpc_tools.protoc --proto_path=schema --python_out=build/bindings \
        --grpc_python_out=build/bindings schema/endpoint.proto
    """
  }
  else if (os == 'windows') {
    sh """
      protoc --proto_path=schema \
        --proto_path=/c/Users/Administrator/Downloads/vcpkg-master/vcpkg-master/buildtrees/protobuf/src/protobuf-3.5.1-win32/include \
        --python_out=build/bindings schema/*.proto
    """
    sh """
      ${version} -m grpc_tools.protoc \
        --proto_path=/c/Users/Administrator/Downloads/vcpkg-master/vcpkg-master/buildtrees/protobuf/src/protobuf-3.5.1-win32/include \
        --proto_path=schema --python_out=build/bindings --grpc_python_out=build/bindings \
        schema/endpoint.proto
    """
  }
  sh """
    zip -j $artifactsPath build/bindings/*.py build/bindings/*.dll build/bindings/*.so \
      build/bindings/*.py build/bindings/*.pyd build/bindings/*.lib build/bindings/*.dll \
      build/bindings/*.exp build/bindings/*.manifest
    """
  if (os == 'windows') {
    sh "cp $artifactsPath /tmp/${GIT_COMMIT}/bindings-artifact"
  }
  else {
    sh "cp $artifactsPath /tmp/bindings-artifact"
  }
  return artifactsPath
}

return this
