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

import org.apache.jackrabbit.vault.PipelineSupport

// valid node labels in https://cwiki.apache.org/confluence/display/INFRA/ci-builds.apache.org
def call(List<String> additionalNodeLabels, List<Integer> additionalJdkVersions, List<String> additionalMavenVersions, String sonarProjectKey, Map options=[:]) {
    boolean hasSeparateItExecution = options.hasSeparateItExecution ?: false
    String mainBuildArguments = options.mainBuildArguments ?: "-U clean site deploy -Pjacoco-report -Dlogback.configurationFile=vault-core/src/test/resources/logback-only-errors.xml"
    String additionalBuildArguments = options.additionalBuildArguments ?: "-U clean ${hasSeparateItExecution?'package':'verify'} site"
    PipelineSupport pipelineSupport = PipelineSupport.getInstance()
    parallel pipelineSupport.stepsForMainAndAdditional('Maven Build', additionalNodeLabels.toSet(), additionalJdkVersions.toSet(), additionalMavenVersions.toSet(), 
        { String nodeLabel, Integer jdkVersion, String mavenVersion, boolean isMainBuild -> 
            return {
                final String sonarPluginGav = "org.sonarsource.scanner.maven:sonar-maven-plugin:3.9.1.2184"
                node(label: nodeLabel) {
                    stage("${isMainBuild ? 'Main ' : ''}Maven Build (JDK ${jdkVersion}, Maven ${mavenVersion}, ${nodeLabel})") {
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
                                    // main build with IT for properly calculating coverage
                                    mavenArguments = "${mainBuildArguments} -DaltDeploymentRepository=snapshot-repo::default::file:${localRepoPath}"
                                } else {
                                    mavenArguments = additionalBuildArguments
                                }
                                PipelineSupport.executeMaven(this, jdkVersion, mavenVersion, mavenArguments, false)
                                if (isMainBuild) {
                                    if (hasSeparateItExecution) {
                                        // stash the integration test classes and the build artifact for later execution
                                        stash name: 'integration-test-classes', includes: '**/target/test-classes/**,**/target/*.jar'
                                    }
                                    if (pipelineSupport.isOnMainBranch) {
                                        // Stash the build results so we can deploy them on another node
                                        stash name: 'local-snapshots-dir', includes: 'local-snapshots-dir/**'
                                    }
                                }
                            } catch (Throwable e) {
                                 error 'Error during building ' + e.toString()
                            }  finally {
                                junit '**/target/surefire-reports/**/*.xml,**/target/failsafe-reports*/**/*.xml'
                            }
                        }
                    }
                    if (isMainBuild) {
                        stage("SonarCloud Analysis") {
                            timeout(60) {
                                withCredentials([string(credentialsId: 'sonarcloud-filevault-token', variable: 'SONAR_TOKEN')]) {
                                    String mavenArguments = "${sonarPluginGav}:sonar -Dsonar.host.url=https://sonarcloud.io -Dsonar.organization=apache -Dsonar.projectKey=${sonarProjectKey}"
                                    pipelineSupport.executeMaven(this, mavenArguments, false)
                                }
                            }
                        }
                    }
                }
            }
        })
}
