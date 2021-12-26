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
    withMaven(
        maven: 'maven_3_latest',
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

def buildStage(final int jdkVersion, final String nodeLabel, final boolean isMainBuild, final String sonarProjectKey) {
    return {
        // https://cwiki.apache.org/confluence/display/INFRA/JDK+Installation+Matrix
        def availableJDKs = [ 8: 'jdk_1.8_latest', 9: 'jdk_1.9_latest', 10: 'jdk_10_latest', 11: 'jdk_11_latest', 12: 'jdk_12_latest', 13: 'jdk_13_latest', 14: 'jdk_14_latest', 15: 'jdk_15_latest', 16: 'jdk_16_latest', 17: 'jdk_17_latest', 18: 'jdk_18_latest']
        final String jdkLabel = availableJDKs[jdkVersion]
        final String wagonPluginGav = "org.codehaus.mojo:wagon-maven-plugin:2.0.2"
        final String sonarPluginGav = "org.sonarsource.scanner.maven:sonar-maven-plugin:3.9.0.2155"
        node(label: nodeLabel) {
            stage("${isMainBuild ? 'Main ' : ''}Maven Build (JDK ${jdkVersion}, ${nodeLabel})") {
                timeout(60) {
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
                            mavenArguments = "-U clean site deploy -DaltDeploymentRepository=snapshot-repo::default::file:${localRepoPath} -Pjacoco-report -Dlogback.configurationFile=vault-core/src/test/resources/logback-only-errors.xml"
                        } else {
                            mavenArguments = '-U clean verify site'
                        }
                        executeMaven(jdkLabel, mavenArguments, 'IMPLICIT')
                        if (isMainBuild && isOnMainBranch()) {
                            // Stash the build results so we can deploy them on another node
                            stash name: 'local-snapshots-dir', includes: 'local-snapshots-dir/**'
                        }
                    } finally {
                        junit '**/target/surefire-reports/**/*.xml,**/target/failsafe-reports*/**/*.xml'
                    }
                }
            }
            if (isMainBuild) {
                stage("SonarCloud Analysis") {
                    timeout(60) {
                        withCredentials([string(credentialsId: 'sonarcloud-filevault-token', variable: 'SONAR_TOKEN')]) {
                            String mavenArguments = "${sonarPluginGav}:sonar -Dsonar.host.url=https://sonarcloud.io -Dsonar.organization=apache -Dsonar.projectKey=${sonarProjectKey}"
                            executeMaven(jdkLabel, mavenArguments, 'EXPLICIT')
                        }
                    }
                }
            }
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

def stagesFor(List<Integer> jdkVersions, int mainJdkVersion, List<String> nodeLabels, String mainNodeLabel, String sonarProjectKey) {
    def stageMap = [:]
    for (nodeLabel in nodeLabels) {
        for (jdkVersion in jdkVersions) {
            boolean isMainBuild = (jdkVersion == mainJdkVersion && nodeLabel == mainNodeLabel)
            stageMap["JDK ${jdkVersion}, ${nodeLabel}${isMainBuild ? ' (Main)' : ''}"] = buildStage(jdkVersion, nodeLabel, isMainBuild, sonarProjectKey)
        }
    }
    return stageMap
}

// valid node labels in https://cwiki.apache.org/confluence/display/INFRA/ci-builds.apache.org
def call(List<Integer> jdkVersions, int mainJdkVersion, List<String> nodeLabels, String mainNodeLabel, String sonarProjectKey) {
    // adjust some job properties (https://www.jenkins.io/doc/pipeline/steps/workflow-multibranch/#properties-set-job-properties)
    properties([
        buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '10'))
    ])
    parallel stagesFor(jdkVersions, mainJdkVersion, nodeLabels, mainNodeLabel, sonarProjectKey)
}
