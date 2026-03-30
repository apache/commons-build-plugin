/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.build.internal;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.build.models.slsa.v1_2.ResourceDescriptor;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;

public final class ArtifactUtils {

    private ArtifactUtils() {
        // prevent instantiation
    }

    public static String getFileName(Artifact artifact) {
        return getFileName(artifact, artifact.getArtifactHandler().getExtension());
    }

    public static String getFileName(Artifact artifact, String extension) {
        StringBuilder fileName = new StringBuilder();
        fileName.append(artifact.getArtifactId()).append("-").append(artifact.getVersion());
        if (artifact.getClassifier() != null) {
            fileName.append("-").append(artifact.getClassifier());
        }
        fileName.append(".").append(extension);
        return fileName.toString();
    }

    public static String getPackageUrl(Artifact artifact) {
        StringBuilder sb = new StringBuilder();
        sb.append("pkg:maven/").append(artifact.getGroupId()).append("/").append(artifact.getArtifactId()).append("@").append(artifact.getVersion())
                .append("?");
        String classifier = artifact.getClassifier();
        if (classifier != null) {
            sb.append("classifier=").append(classifier).append("&");
        }
        sb.append("type=").append(artifact.getType());
        return sb.toString();
    }

    public static Map<String, String> getChecksums(Artifact artifact) throws IOException {
        Map<String, String> checksums = new HashMap<>();
        DigestUtils digest = new DigestUtils(DigestUtils.getSha256Digest());
        String sha256sum = digest.digestAsHex(artifact.getFile());
        checksums.put("sha256", sha256sum);
        return checksums;
    }

    public static ResourceDescriptor toResourceDescriptor(Artifact artifact) throws MojoExecutionException {
        ResourceDescriptor descriptor = new ResourceDescriptor();
        descriptor.setName(getFileName(artifact));
        descriptor.setUri(getPackageUrl(artifact));
        if (artifact.getFile() != null) {
            try {
                descriptor.setDigest(getChecksums(artifact));
            } catch (IOException e) {
                throw new MojoExecutionException("Unable to compute hash for artifact file: " + artifact.getFile(), e);
            }
        }
        return descriptor;
    }
}
