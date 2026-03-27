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

/**
 * SLSA 1.2 Build Attestation Models.
 *
 * <p>This package provides Jackson-annotated model classes that implement the <a
 * href="https://slsa.dev/spec/v1.2">Supply-chain Levels for Software Artifacts (SLSA) v1.2
 * specification</a>.
 *
 * <h2>Overview</h2>
 *
 * <p>SLSA is a framework for evaluating and improving the security posture of build systems. SLSA
 * v1.2 defines a standard for recording build provenance - information about how software
 * artifacts were produced.
 *
 * <h2>Core Models</h2>
 *
 * <ul>
 *   <li><b>{@link org.apache.commons.build.models.slsa.v1_2.Provenance}</b> - Root object
 *       describing the build provenance. Contains BuildDefinition and RunDetails.
 *   <li><b>{@link org.apache.commons.build.models.slsa.v1_2.BuildDefinition}</b> - Specifies
 *       the inputs that define the build, including build type, configuration, external
 *       parameters, and resolved dependencies.
 *   <li><b>{@link org.apache.commons.build.models.slsa.v1_2.RunDetails}</b> - Specifies the
 *       details about the build invocation and environment, including the builder identity and
 *       build metadata.
 * </ul>
 *
 * <h2>Supporting Models</h2>
 *
 * <ul>
 *   <li><b>{@link org.apache.commons.build.models.slsa.v1_2.Builder}</b> - Identifies the
 *       entity that executed the build.
 *   <li><b>{@link org.apache.commons.build.models.slsa.v1_2.BuildMetadata}</b> - Contains
 *       metadata about the build invocation, including timing information.
 *   <li><b>{@link org.apache.commons.build.models.slsa.v1_2.ResourceDescriptor}</b> - Describes
 *       an artifact or resource referenced in the build by URI and cryptographic digest.
 * </ul>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>
 * // Create a builder
 * Builder builder = new Builder();
 * builder.setId("https://github.com/actions");
 * builder.setVersion("1.0");
 *
 * // Create build metadata
 * BuildMetadata buildMetadata = new BuildMetadata();
 * buildMetadata.setInvocationId("build-12345");
 * buildMetadata.setStartedOn(OffsetDateTime.now(ZoneOffset.UTC));
 * buildMetadata.setFinishedOn(OffsetDateTime.now(ZoneOffset.UTC));
 *
 * // Create run details
 * RunDetails runDetails = new RunDetails();
 * runDetails.setBuilder(builder);
 * runDetails.setMetadata(buildMetadata);
 *
 * // Create build definition
 * BuildDefinition buildDefinition = new BuildDefinition();
 * buildDefinition.setBuildType("https://github.com/actions");
 * buildDefinition.setExternalParameters(new HashMap<>());
 *
 * // Create provenance
 * Provenance provenance = new Provenance();
 * provenance.setBuildDefinition(buildDefinition);
 * provenance.setRunDetails(runDetails);
 *
 * // Serialize with Jackson
 * ObjectMapper mapper = new ObjectMapper();
 * String json = mapper.writeValueAsString(provenance);
 * </pre>
 *
 * <h2>Jackson Integration</h2>
 *
 * <p>All models use Jackson annotations for JSON serialization/deserialization:
 *
 * <ul>
 *   <li>{@code @JsonProperty} - Maps field names to JSON properties
 *   <li>{@code @JsonInclude} - Controls inclusion of null/empty values in serialization
 *   <li>{@code @JsonFormat} - Specifies date/time formatting
 * </ul>
 *
 * <p>The models are compatible with Jackson's ObjectMapper and support both serialization to
 * JSON and deserialization from JSON.
 *
 * <h2>Validation</h2>
 *
 * <p>Some models include Jakarta Validation annotations:
 *
 * <ul>
 *   <li>{@code @NotBlank} - Ensures required string fields are not empty
 * </ul>
 *
 * <p>Users can enable validation using a Jakarta Validation provider to ensure provenance
 * integrity.
 *
 * <h2>Reference</h2>
 *
 * @see <a href="https://slsa.dev/spec/v1.2">SLSA v1.2 Specification</a>
 * @see <a href="https://github.com/in-toto/attestation">In-toto Attestation Framework</a>
 * @see <a href="https://github.com/FasterXML/jackson">Jackson JSON processor</a>
 */
package org.apache.commons.build.models.slsa.v1_2;

