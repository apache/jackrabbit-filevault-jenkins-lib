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
def call(String mainNodeLabel, int mainJdkVersion, String mainMavenVersion, Closure body) {
    PipelineSupport pipelineSupport = PipelineSupport.createInstance(mainNodeLabel, mainJdkVersion, mainMavenVersion, env.BRANCH_NAME == 'master')
    // adjust some job properties (https://www.jenkins.io/doc/pipeline/steps/workflow-multibranch/#properties-set-job-properties)
    def buildProperties = []
    if (pipelineSupport.isOnMainBranch) {
      echo "Building main branch ${env.BRANCH_NAME}"
      // set build retention time first
      buildProperties.add(buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '5', daysToKeepStr: '15', numToKeepStr: '10')))
      // ensure a build is done every month
      buildProperties.add(pipelineTriggers([cron('@monthly')]))
    } else {
      echo "Building auxiliary branch ${env.BRANCH_NAME}"
      buildProperties.add(buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '2', daysToKeepStr: '7', numToKeepStr: '3')))
    }
    properties(buildProperties)
    body()
}

