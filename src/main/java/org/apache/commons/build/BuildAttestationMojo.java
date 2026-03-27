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
package org.apache.commons.build;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.inject.Inject;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.commons.build.internal.ArtifactUtils;
import org.apache.commons.build.internal.BuildToolDescriptors;
import org.apache.commons.build.internal.GitUtils;
import org.apache.commons.build.models.slsa.v1_2.BuildDefinition;
import org.apache.commons.build.models.slsa.v1_2.BuildMetadata;
import org.apache.commons.build.models.slsa.v1_2.Builder;
import org.apache.commons.build.models.slsa.v1_2.Provenance;
import org.apache.commons.build.models.slsa.v1_2.ResourceDescriptor;
import org.apache.commons.build.models.slsa.v1_2.RunDetails;
import org.apache.commons.build.models.slsa.v1_2.Statement;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.rtinfo.RuntimeInformation;
import org.apache.maven.scm.CommandParameters;
import org.apache.maven.scm.ScmException;
import org.apache.maven.scm.ScmFileSet;
import org.apache.maven.scm.command.info.InfoItem;
import org.apache.maven.scm.command.info.InfoScmResult;
import org.apache.maven.scm.manager.ScmManager;
import org.apache.maven.scm.repository.ScmRepository;

/**
 * This plugin generates an in-toto attestation for all the artifacts
 */
@Mojo(name = "build-attestation", defaultPhase = LifecyclePhase.VERIFY, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class BuildAttestationMojo extends AbstractMojo {

    private static final String ATTESTATION_EXTENSION = "intoto.json";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static {
        OBJECT_MAPPER.findAndRegisterModules();
        OBJECT_MAPPER.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Parameter(defaultValue = "${project.scm.connection}", readonly = true)
    private String scmConnectionUrl;

    @Parameter(defaultValue = "${project.scm.developerConnection}", readonly = true)
    private String scmDeveloperConnectionUrl;

    @Parameter(defaultValue = "${project.scm.tag}", readonly = true)
    private String scmTag;

    @Parameter(defaultValue = "${maven.home}", readonly = true)
    private File mavenHome;

    /**
     * Issue SCM actions at this local directory
     */
    @Parameter(property = "commons.build.scmDirectory", defaultValue = "${basedir}")
    private File scmDirectory;

    @Parameter(property = "commons.build.outputDirectory", defaultValue = "${project.build.directory}")
    private File outputDirectory;

    @Parameter(property = "commons.build.skipAttach")
    private boolean skipAttach;

    /**
     * The current Maven project.
     */
    private final MavenProject project;

    /**
     * SCM manager to detect the Git revision.
     */
    private final ScmManager scmManager;

    /**
     * Runtime information
     */
    private final RuntimeInformation runtimeInformation;

    /**
     * The current Maven session, used to resolve plugin dependencies.
     */
    private final MavenSession session;

    /**
     * Helper to attach artifacts to the project.
     */
    private final MavenProjectHelper mavenProjectHelper;

    @Inject
    public BuildAttestationMojo(MavenProject project, ScmManager scmManager, RuntimeInformation runtimeInformation, MavenSession session,
            MavenProjectHelper mavenProjectHelper) {
        this.project = project;
        this.scmManager = scmManager;
        this.runtimeInformation = runtimeInformation;
        this.session = session;
        this.mavenProjectHelper = mavenProjectHelper;
    }

    void setOutputDirectory(File outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    public File getScmDirectory() {
        return scmDirectory;
    }

    public void setScmDirectory(File scmDirectory) {
        this.scmDirectory = scmDirectory;
    }

    void setScmConnectionUrl(String scmConnectionUrl) {
        this.scmConnectionUrl = scmConnectionUrl;
    }

    void setScmDeveloperConnectionUrl(String scmDeveloperConnectionUrl) {
        this.scmDeveloperConnectionUrl = scmDeveloperConnectionUrl;
    }

    void setScmTag(String scmTag) {
        this.scmTag = scmTag;
    }

    void setMavenHome(File mavenHome) {
        this.mavenHome = mavenHome;
    }

    @Override
    public void execute() throws MojoFailureException, MojoExecutionException {
        getLog().info("This is a build attestation.");
        // Build definition
        BuildDefinition buildDefinition = new BuildDefinition();
        buildDefinition.setExternalParameters(getExternalParameters());
        buildDefinition.setResolvedDependencies(getBuildDependencies());
        // Builder
        Builder builder = new Builder();
        // RunDetails
        RunDetails runDetails = new RunDetails();
        runDetails.setBuilder(builder);
        runDetails.setMetadata(getBuildMetadata());
        // Provenance
        Provenance provenance = new Provenance();
        provenance.setBuildDefinition(buildDefinition);
        provenance.setRunDetails(runDetails);
        // Statement
        Statement statement = new Statement();
        statement.setSubject(getSubjects());
        statement.setPredicate(provenance);

        writeStatement(statement);
    }

    private void writeStatement(Statement statement) throws MojoExecutionException {
        final Path outputPath = outputDirectory.toPath();
        try {
            if (!Files.exists(outputPath)) {
                Files.createDirectories(outputPath);
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Could not create output directory.", e);
        }
        final Artifact mainArtifact = project.getArtifact();
        final Path artifactPath = outputPath.resolve(ArtifactUtils.getFileName(mainArtifact, ATTESTATION_EXTENSION));
        getLog().info("Writing attestation statement to: " + artifactPath);
        try (OutputStream os = Files.newOutputStream(artifactPath)) {
            OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(os, statement);
        } catch (IOException e) {
            throw new MojoExecutionException("Could not write attestation statement to: " + artifactPath, e);
        }
        if (!skipAttach) {
            getLog().info(String.format("Attaching attestation statement as %s-%s.%s", mainArtifact.getArtifactId(), mainArtifact.getVersion(),
                    ATTESTATION_EXTENSION));
            mavenProjectHelper.attachArtifact(project, ATTESTATION_EXTENSION, null, artifactPath.toFile());
        }
    }

    /**
     * Get the artifacts generated by the build.
     *
     * @return A list of resource descriptors for the build artifacts.
     */
    private List<ResourceDescriptor> getSubjects() throws MojoExecutionException {
        List<ResourceDescriptor> subjects = new ArrayList<>();
        subjects.add(ArtifactUtils.toResourceDescriptor(project.getArtifact()));
        for (Artifact artifact : project.getAttachedArtifacts()) {
            subjects.add(ArtifactUtils.toResourceDescriptor(artifact));
        }
        return subjects;
    }

    private Map<String, Object> getExternalParameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("jvm.args", ManagementFactory.getRuntimeMXBean().getInputArguments());
        MavenExecutionRequest request = session.getRequest();
        params.put("maven.goals", request.getGoals());
        params.put("maven.profiles", request.getActiveProfiles());
        params.put("maven.user.properties", request.getUserProperties());
        params.put("maven.cmdline", getCommandLine(request));
        Map<String, Object> env = new HashMap<>();
        params.put("env", env);
        for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
            String key = entry.getKey();
            if ("TZ".equals(key) || "LANG".equals(key) || key.startsWith("LC_")) {
                env.put(key, entry.getValue());
            }
        }
        return params;
    }

    private String getCommandLine(MavenExecutionRequest request) {
        StringBuilder sb = new StringBuilder();
        for (String goal : request.getGoals()) {
            sb.append(goal);
            sb.append(" ");
        }
        List<String> activeProfiles = request.getActiveProfiles();
        if (activeProfiles != null && !activeProfiles.isEmpty()) {
            sb.append("-P");
            for (String profile : activeProfiles) {
                sb.append(profile);
                sb.append(",");
            }
            removeLast(sb);
            sb.append(" ");
        }
        Properties userProperties = request.getUserProperties();
        for (String propertyName : userProperties.stringPropertyNames()) {
            sb.append("-D");
            sb.append(propertyName);
            sb.append("=");
            sb.append(userProperties.get(propertyName));
            sb.append(" ");
        }
        removeLast(sb);
        return sb.toString();
    }

    private static void removeLast(StringBuilder sb) {
        if (sb.length() > 0) {
            sb.setLength(sb.length() - 1);
        }
    }

    private List<ResourceDescriptor> getBuildDependencies() throws MojoExecutionException {
        List<ResourceDescriptor> dependencies = new ArrayList<>();
        try {
            dependencies.add(BuildToolDescriptors.jvm(Paths.get(System.getProperty("java.home"))));
            dependencies.add(BuildToolDescriptors.maven(runtimeInformation.getMavenVersion(), mavenHome.toPath()));
            dependencies.add(getScmDescriptor());
        } catch (IOException e) {
            throw new MojoExecutionException(e);
        }
        dependencies.addAll(getProjectDependencies());
        return dependencies;
    }

    private List<ResourceDescriptor> getProjectDependencies() throws MojoExecutionException {
        List<ResourceDescriptor> dependencies = new ArrayList<>();
        for (Artifact artifact : project.getArtifacts()) {
            dependencies.add(ArtifactUtils.toResourceDescriptor(artifact));
        }
        return dependencies;
    }

    private ResourceDescriptor getScmDescriptor() throws IOException, MojoExecutionException {
        ResourceDescriptor scmDescriptor = new ResourceDescriptor();
        String scmUri = GitUtils.scmToDownloadUri(scmConnectionUrl, scmDirectory.toPath());
        scmDescriptor.setUri(scmUri);
        // Compute the revision
        Map<String, String> digest = new HashMap<>();
        digest.put("gitCommit", getScmRevision());
        scmDescriptor.setDigest(digest);
        return scmDescriptor;
    }

    private ScmRepository getScmRepository() throws MojoExecutionException {
        try {
            return scmManager.makeScmRepository(scmConnectionUrl);
        } catch (ScmException e) {
            throw new MojoExecutionException("Failed to create SCM repository", e);
        }
    }

    private String getScmRevision() throws MojoExecutionException {
        ScmRepository scmRepository = getScmRepository();
        CommandParameters commandParameters = new CommandParameters();
        try {
            InfoScmResult result = scmManager.getProviderByRepository(scmRepository).info(scmRepository.getProviderRepository(), new ScmFileSet(scmDirectory)
                    , commandParameters);

            return getScmRevision(result);
        } catch (ScmException e) {
            throw new MojoExecutionException("Failed to retrieve SCM revision", e);
        }
    }

    private String getScmRevision(InfoScmResult result) throws MojoExecutionException {
        if (!result.isSuccess()) {
            throw new MojoExecutionException("Failed to retrieve SCM revision: " + result.getProviderMessage());
        }

        if (result.getInfoItems() == null || result.getInfoItems().isEmpty()) {
            throw new MojoExecutionException("No SCM revision information found for " + scmDirectory);
        }

        InfoItem item = result.getInfoItems().get(0);

        String revision = item.getRevision();
        if (revision == null) {
            throw new MojoExecutionException("Empty SCM revision returned for " + scmDirectory);
        }
        return revision;
    }

    private BuildMetadata getBuildMetadata() {
        OffsetDateTime startedOn = session.getStartTime().toInstant().atOffset(ZoneOffset.UTC);
        OffsetDateTime finishedOn = OffsetDateTime.now(ZoneOffset.UTC);
        return new BuildMetadata(session.getRequest().getBuilderId(), startedOn, finishedOn);
    }
}
