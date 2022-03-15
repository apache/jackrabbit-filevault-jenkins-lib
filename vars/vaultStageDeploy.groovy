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


def deployStage(final PipelineSupport pipelineSupport) {
    final String wagonPluginGav = "org.codehaus.mojo:wagon-maven-plugin:2.0.2"
    stage("Deployment to Maven Repository") {
        node('nexus-deploy') {
            timeout(60) {
                echo "Running on node ${env.NODE_NAME}"
                // Nexus deployment needs pom.xml
                checkout scm
                // Unstash the previously stashed build results.
                unstash name: 'local-snapshots-dir'
                // https://www.mojohaus.org/wagon-maven-plugin/merge-maven-repos-mojo.html
                String mavenArguments = "${wagonPluginGav}:merge-maven-repos -Dwagon.target=https://repository.apache.org/content/repositories/snapshots -Dwagon.targetId=apache.snapshots.https -Dwagon.source=file:${env.WORKSPACE}/local-snapshots-dir"
                pipelineSupport.executeMaven(this, mavenArguments, false)
            }
        }
    }
}

def call() {
    PipelineSupport pipelineSupport = PipelineSupport.getInstance()
    if (pipelineSupport.isOnMainBranch) {
        deployStage(pipelineSupport)
    }
}