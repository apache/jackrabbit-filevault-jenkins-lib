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
import java.io.File
import groovy.io.FileType

// valid node labels in https://cwiki.apache.org/confluence/display/INFRA/ci-builds.apache.org
def call(List<String> additionalNodeLabels, List<Integer> additionalJdkVersions, List<String> additionalMavenVersions) {
    PipelineSupport pipelineSupport = PipelineSupport.getInstance()
    parallel pipelineSupport.stepsFor('Integration Tests', additionalNodeLabels.toSet(), additionalJdkVersions.toSet(), additionalMavenVersions.toSet(), 
        {String nodeLabel, int jdkVersion, String mavenVersion, boolean isMainBuild -> 
            return {
                stage("Run Integration Tests  (JDK ${jdkVersion}, Maven ${mavenVersion}, ${nodeLabel})") {
                    node(nodeLabel) {
                        timeout(60) {
                            echo "Running on node ${env.NODE_NAME}"
                            // running ITs needs pom.xml
                            checkout scm
                            // Unstash the previously stashed build results.
                            unstash name: 'integration-test-classes'
                            try {
                                // install to be tested artifact to local repository
                                // https://www.jenkins.io/doc/pipeline/steps/pipeline-utility-steps/#findfiles-find-files-in-the-workspace
                                def jarFiles = findFiles(glob: '**/target/*.jar')
                                if (jarFiles.length == 0) {
                                    echo "Found no JAR artifact to install in local repository"
                                } else {
                                    pipelineSupport.executeMaven(this, "install:install-file -Dfile=${jarFiles[0].path} -DpomFile=pom.xml", false)
                                }
                                String mavenOpts = '';
                                // workaround for https://bugs.openjdk.java.net/browse/JDK-8057894
                                if (!isUnix()) {
                                    mavenOpts = '-Djava.security.egd=file:/dev/urandom';
                                }
                                // execute ITs
                                pipelineSupport.executeMaven(this, jdkVersion, mavenVersion, 'failsafe:integration-test failsafe:verify', mavenOpts, false)
                            } finally {
                                junit '**/target/failsafe-reports*/**/*.xml'
                            }
                        }
                    }
                }
            }
        } )
}
