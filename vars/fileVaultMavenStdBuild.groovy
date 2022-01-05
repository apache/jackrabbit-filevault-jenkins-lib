/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

def isOnMainBranch() {
    return env.BRANCH_NAME == 'master'
}

def executeMaven(String jdkLabel, String mavenArguments, String publisherStrategy) {
    executeMaven(jdkLabel, getMavenLabel('3'), mavenArguments, publisherStrategy)
}

def executeMaven(String jdkLabel, String mavenLabel, String mavenArguments, String publisherStrategy) {
    withMaven(
        maven: mavenLabel,
        jdk: jdkLabel,
        mavenLocalRepo: '.repository',
        publisherStrategy: publisherStrategy) {
        if (isUnix()) {
            sh "mvn -B ${mavenArguments}"
        } else {
            bat "mvn -B ${mavenArguments}"
        }
    }
}

// always major.minor.qualifier version parts or just one (which means latest of that major version)
String getMavenLabel(String mavenVersion) {
    final String versionLabel
    if (mavenVersion ==~ /\d+/) {
        versionLabel = "${mavenVersion}_latest"
    } else if (mavenVersion ==~ /\d+\.\d+\.\d+/) {
        // make sure it
        final String suffix
        if (isUnix()) {
            suffix = ""
        } else {
            suffix = "_windows"
        }
        versionLabel = "${mavenVersion}${suffix}"
    } else {
        error('mavenVersion must be either one integer or three integers separated by dot')
    }
    // valid installation names in https://cwiki.apache.org/confluence/display/INFRA/Maven+Installation+Matrix and https://github.com/apache/infrastructure-p6/blob/production/modules/jenkins_client_master/files/hudson.tasks.Maven.xml
    return "maven_${versionLabel}"
}

def buildStage(final String jdkLabel, final String nodeLabel, final String mavenVersion, final boolean isMainBuild, final String sonarProjectKey) {
    return {
        final String wagonPluginGav = "org.codehaus.mojo:wagon-maven-plugin:2.0.2"
        final String sonarPluginGav = "org.sonarsource.scanner.maven:sonar-maven-plugin:3.9.1.2184"
        node(label: nodeLabel) {
            stage("${isMainBuild ? 'Main ' : ''}Maven Build (JDK ${jdkLabel}, Maven ${mavenVersion}, ${nodeLabel})") {
                timeout(60) {
                    final String mavenLabel = getMavenLabel(mavenVersion) // this requires a node context
                    echo "Running on node ${env.NODE_NAME}"
                    checkout scm
                    try {
                        String mavenArguments
                        if (isMainBuild) {
                            String localRepoPath = "${env.WORKSPACE}/local-snapshots-dir"
                            // Make sure the directory is wiped.
                            dir(localRepoPath) {
                                deleteDir()
                            }
                            mavenArguments = "-U clean site deploy -DskipITs -DaltDeploymentRepository=snapshot-repo::default::file:${localRepoPath} -Pjacoco-report -Dlogback.configurationFile=vault-core/src/test/resources/logback-only-errors.xml"
                        } else {
                            mavenArguments = '-U clean package site'
                        }
                        executeMaven(jdkLabel, mavenLabel, mavenArguments, 'IMPLICIT')
                        if (isMainBuild) {
                            // stash the integration test classes for later execution
                            stash name: 'integration-test-classes', includes: '**/target/test-classes/**'
                            if (isOnMainBranch()) {
                                // Stash the build results so we can deploy them on another node
                                stash name: 'local-snapshots-dir', includes: 'local-snapshots-dir/**'
                            }
                        }
                    } finally {
                        junit '**/target/surefire-reports/**/*.xml'
                    }
                }
            }
            /*
            if (isMainBuild) {
                stage("SonarCloud Analysis") {
                    timeout(60) {
                        withCredentials([string(credentialsId: 'sonarcloud-filevault-token', variable: 'SONAR_TOKEN')]) {
                            String mavenArguments = "${sonarPluginGav}:sonar -Dsonar.host.url=https://sonarcloud.io -Dsonar.organization=apache -Dsonar.projectKey=${sonarProjectKey}"
                            executeMaven(jdkLabel, mavenArguments, 'EXPLICIT')
                        }
                    }
                }
            }*/
        }
        if (isMainBuild && isOnMainBranch()) {
            stage("Deployment") {
                node('nexus-deploy') {
                    timeout(60) {
                        // Nexus deployment needs pom.xml
                        checkout scm
                        // Unstash the previously stashed build results.
                        unstash name: 'local-snapshots-dir'
                        // https://www.mojohaus.org/wagon-maven-plugin/merge-maven-repos-mojo.html
                        String mavenArguments = "${wagonPluginGav}:merge-maven-repos -Dwagon.target=https://repository.apache.org/content/repositories/snapshots -Dwagon.targetId=apache.snapshots.https -Dwagon.source=file:${env.WORKSPACE}/local-snapshots-dir"
                        executeMaven(jdkLabel, mavenArguments, 'EXPLICIT')
                    }
                }
            }
        }
    }
}

def stageIT(final String jdkLabel, final String nodeLabel, final String mavenVersion) {
    stage("Run Integration Tests") {
        node(nodeLabel) {
            timeout(60) {
                // running ITs needs pom.xml
                checkout scm
                // Unstash the previously stashed build results.
                unstash name: 'integration-test-classes'
                try {
                    final String mavenLabel = getMavenLabel(mavenVersion) // this requires a node context
                    // populate test source directory
                    String mavenArguments = '-X failsafe:integration-test failsafe:verify'
                    executeMaven(jdkLabel, mavenLabel, mavenArguments, 'EXPLICIT')
                } finally {
                    junit '**/target/failsafe-reports*/**/*.xml'
                }
            }
        }
    }
}

def stagesFor(List<Integer> jdkVersions, int mainJdkVersion, List<String> nodeLabels, String mainNodeLabel, List<String> mavenVersions, String mainMavenVersion, String sonarProjectKey) {
    def stageMap = [:]
    // https://cwiki.apache.org/confluence/display/INFRA/JDK+Installation+Matrix
    def availableJDKs = [ 8: 'jdk_1.8_latest', 9: 'jdk_1.9_latest', 10: 'jdk_10_latest', 11: 'jdk_11_latest', 12: 'jdk_12_latest', 13: 'jdk_13_latest', 14: 'jdk_14_latest', 15: 'jdk_15_latest', 16: 'jdk_16_latest', 17: 'jdk_17_latest', 18: 'jdk_18_latest', 19: 'jdk_19_latest']
    
    for (nodeLabel in nodeLabels) {
        for (jdkVersion in jdkVersions) {
            final String jdkLabel = availableJDKs[jdkVersion]
            for (mavenVersion in mavenVersions) {
                boolean isMainBuild = (jdkVersion == mainJdkVersion && nodeLabel == mainNodeLabel && mainMavenVersion == mavenVersion)
                stageMap["JDK ${jdkVersion}, ${nodeLabel}, Maven ${mavenVersion} ${isMainBuild ? ' (Main)' : ''}"] = buildStage(jdkLabel, nodeLabel, mavenVersion, isMainBuild, sonarProjectKey)
            }
        }
    }
    return stageMap
}

// valid node labels in https://cwiki.apache.org/confluence/display/INFRA/ci-builds.apache.org
def call(List<Integer> jdkVersions, int mainJdkVersion, List<String> nodeLabels, String mainNodeLabel, String sonarProjectKey) {
    call(jdkVersions, mainJdkVersion, nodeLabels, mainNodeLabel, ["3"], "3", sonarProjectKey)
}

def call(List<Integer> jdkVersions, int mainJdkVersion, List<String> nodeLabels, String mainNodeLabel, List<String> mavenVersions, String mainMavenVersion, String sonarProjectKey) {
    // adjust some job properties (https://www.jenkins.io/doc/pipeline/steps/workflow-multibranch/#properties-set-job-properties)
    def buildProperties = []
    if (isOnMainBranch()) {
      // set build retention time first
      buildProperties.add(buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '5', daysToKeepStr: '15', numToKeepStr: '10')))
      // ensure a build is done every month
      buildProperties.add(pipelineTriggers([cron('@monthly')]))
    } else {
      buildProperties.add(buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '2', daysToKeepStr: '7', numToKeepStr: '3')))
    }
    properties(buildProperties)
    parallel stagesFor(jdkVersions, mainJdkVersion, nodeLabels, mainNodeLabel, mavenVersions, mainMavenVersion, sonarProjectKey)
    // TODO: trigger ITs separately
    stageIT('jdk_1.8_latest', mainNodeLabel, '3.3.9')
    // finally do deploy
}
