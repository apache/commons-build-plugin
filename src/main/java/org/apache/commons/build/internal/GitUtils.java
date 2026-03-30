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

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;

/**
 * Utilities for Git operations.
 */
public final class GitUtils {

    /**
     * Returns the Git tree hash for the given directory.
     *
     * @param path A directory path.
     * @return A hex-encoded SHA-1 tree hash.
     * @throws IOException If the path is not a directory or an I/O error occurs.
     */
    public static String gitTree(Path path) throws IOException {
        if (!Files.isDirectory(path)) {
            throw new IOException("Path is not a directory: " + path);
        }
        MessageDigest digest = DigestUtils.getSha1Digest();
        return Hex.encodeHexString(DigestUtils.gitTree(digest, path));
    }

    /**
     * Returns the Git blob hash for the given file.
     *
     * @param path A regular file path.
     * @return A hex-encoded SHA-1 blob hash.
     * @throws IOException If the path is not a regular file or an I/O error occurs.
     */
    public static String gitBlob(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IOException("Path is not a regular file: " + path);
        }
        MessageDigest digest = DigestUtils.getSha1Digest();
        return Hex.encodeHexString(DigestUtils.gitBlob(digest, path));
    }

    /**
     * Converts an SCM URI to a download URI suffixed with the current branch name.
     *
     * @param scmUri A Maven SCM URI starting with {@code scm:git}.
     * @param repositoryPath A path inside the Git repository.
     * @return A download URI of the form {@code git+<url>@<branch>}.
     * @throws IOException If the current branch cannot be determined.
     */
    public static String scmToDownloadUri(String scmUri, Path repositoryPath) throws IOException {
        if (!scmUri.startsWith("scm:git")) {
            throw new IllegalArgumentException("Invalid scmUri: " + scmUri);
        }
        String currentBranch = getCurrentBranch(repositoryPath);
        return "git+" + scmUri.substring(8) + "@" + currentBranch;
    }

    /**
     * Returns the current branch name for the given repository path.
     *
     * <p>Returns the commit SHA if the repository is in a detached HEAD state.
     *
     * @param repositoryPath A path inside the Git repository.
     * @return The current branch name, or the commit SHA for a detached HEAD.
     * @throws IOException If the {@code .git} directory cannot be found or read.
     */
    public static String getCurrentBranch(Path repositoryPath) throws IOException {
        Path gitDir = findGitDir(repositoryPath);
        String head = new String(Files.readAllBytes(gitDir.resolve("HEAD")), StandardCharsets.UTF_8).trim();
        if (head.startsWith("ref: refs/heads/")) {
            return head.substring("ref: refs/heads/".length());
        }
        // detached HEAD — return the commit SHA
        return head;
    }

    private static Path findGitDir(Path path) throws IOException {
        Path current = path.toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(".git");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            if (Files.isRegularFile(candidate)) {
                // git worktree: .git is a file containing "gitdir: /path/to/real/.git"
                String content = new String(Files.readAllBytes(candidate), StandardCharsets.UTF_8).trim();
                if (content.startsWith("gitdir: ")) {
                    return Paths.get(content.substring("gitdir: ".length()));
                }
            }
            current = current.getParent();
        }
        throw new IOException("No .git directory found above: " + path);
    }

    private GitUtils() {
        // no instantiation
    }
}
