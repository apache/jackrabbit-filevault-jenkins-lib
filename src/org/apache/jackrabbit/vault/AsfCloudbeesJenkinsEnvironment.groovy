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
package org.apache.jackrabbit.vault

class AsfCloudbeesJenkinsEnvironment {
    
    // always major.minor.qualifier version parts or just one (which means latest of that major version)
    public static String getMavenLabel(boolean isWindows, String mavenVersion) {
        final String versionLabel
        if (mavenVersion ==~ /\d+/) {
            versionLabel = "${mavenVersion}_latest"
        } else if (mavenVersion ==~ /\d+\.\d+\.\d+/) {
            // make sure it
            final String suffix
            if (isWindows) {
                suffix = "_windows"
            } else {
                suffix = ""
            }
            versionLabel = "${mavenVersion}${suffix}"
        } else {
            throw new IllegalArgumentException('mavenVersion must be either one integer or three integers separated by dot')
        }
        // valid installation names in https://cwiki.apache.org/confluence/display/INFRA/Maven+Installation+Matrix and https://github.com/apache/infrastructure-p6/blob/production/modules/jenkins_client_master/files/hudson.tasks.Maven.xml
        return "maven_${versionLabel}"
    }

    public static String getJdkLabel(int jdkVersion) {
        // https://cwiki.apache.org/confluence/display/INFRA/JDK+Installation+Matrix
        def availableJDKs = [ 
            8: 'jdk_1.8_latest', 9: 'jdk_1.9_latest', 10: 'jdk_10_latest', 11: 'jdk_11_latest', 12: 'jdk_12_latest', 13: 'jdk_13_latest',
            14: 'jdk_14_latest', 15: 'jdk_15_latest', 16: 'jdk_16_latest', 17: 'jdk_17_latest', 18: 'jdk_18_latest', 19: 'jdk_19_latest',
            20: 'jdk_20_latest', 21: 'jdk_21_latest', 22: 'jdk_22_latest'
            ]
        return availableJDKs[jdkVersion]
    }
}
