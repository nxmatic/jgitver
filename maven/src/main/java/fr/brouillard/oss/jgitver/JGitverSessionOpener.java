/*
 * Copyright (C) 2016 Matthieu Brouillard [http://oss.brouillard.fr/jgitver-maven-plugin] (matthieu@brouillard.fr)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fr.brouillard.oss.jgitver;

import fr.brouillard.oss.jgitver.cfg.Configuration;
import fr.brouillard.oss.jgitver.metadata.Metadatas;
import io.vavr.control.Try;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.codehaus.plexus.logging.Logger;

class JGitverSessionOpener {

  @Inject Logger logger;

  @Inject JGitverProjectVersioner projectVersioner;

  @Inject JGitverConfiguration configurationProvider;

  JGitverSession openSession(MavenSession mavenSession) throws MavenExecutionException {
    final File rootDirectory = mavenSession.getRequest().getMultiModuleProjectDirectory();
    logger.debug(
        String.format(
            "Opening JGitver session for %s using class loader (%x)",
            rootDirectory, JGitverSessionOpener.class.getClassLoader().hashCode()));
    try (GitVersionCalculator calculator = GitVersionCalculator.location(rootDirectory)) {
      return openSession(mavenSession, calculator);
    } catch (Exception ex) {
      throw new MavenExecutionException(
          "cannot autoclose GitVersionCalculator object for project: " + rootDirectory, ex);
    }
  }

  Try<JGitverSession> tryOpen(MavenSession mavenSession) {
    return Try.of(() -> openSession(mavenSession))
        .peek(jgitverSession -> projectVersioner.version(mavenSession, jgitverSession));
  }

  private JGitverSession openSession(
      MavenSession mavenSession, GitVersionCalculator gitVersionCalculator)
      throws MavenExecutionException {
    final File rootDirectory = mavenSession.getRequest().getMultiModuleProjectDirectory();

    logger.debug("using " + JGitverUtils.EXTENSION_PREFIX + " on directory: " + rootDirectory);

    Configuration cfg = configurationProvider.getConfiguration();

    if (cfg.strategy != null) {
      gitVersionCalculator.setStrategy(cfg.strategy);
    } else {
      gitVersionCalculator.setMavenLike(cfg.mavenLike);
    }

    if (cfg.policy != null) {
      gitVersionCalculator.setLookupPolicy(cfg.policy);
    }

    gitVersionCalculator
        .setAutoIncrementPatch(cfg.autoIncrementPatch)
        .setUseDirty(cfg.useDirty)
        .setUseDistance(cfg.useCommitDistance)
        .setUseGitCommitTimestamp(cfg.useGitCommitTimestamp)
        .setUseGitCommitId(cfg.useGitCommitId)
        .setUseSnapshot(cfg.useSnapshot)
        .setGitCommitIdLength(cfg.gitCommitIdLength)
        .setUseDefaultBranchingPolicy(cfg.useDefaultBranchingPolicy)
        .setNonQualifierBranches(cfg.nonQualifierBranches)
        .setVersionPattern(cfg.versionPattern)
        .setTagVersionPattern(cfg.tagVersionPattern)
        .setScript(cfg.script)
        .setScriptType(cfg.scriptType);

    if (cfg.maxSearchDepth >= 1 && cfg.maxSearchDepth != Configuration.UNSET_DEPTH) {
      // keep redundant test in case we change UNSET_DEPTH value
      gitVersionCalculator.setMaxDepth(cfg.maxSearchDepth);
    }

    if (JGitverUtils.shouldForceComputation(mavenSession)) {
      gitVersionCalculator.setForceComputation(true);
    }

    if (cfg.regexVersionTag != null) {
      gitVersionCalculator.setFindTagVersionPattern(cfg.regexVersionTag);
    }

    if (cfg.branchPolicies != null && !cfg.branchPolicies.isEmpty()) {
      List<BranchingPolicy> policies =
          cfg.branchPolicies.stream()
              .map(bp -> new BranchingPolicy(bp.pattern, bp.transformations))
              .collect(Collectors.toList());

      gitVersionCalculator.setQualifierBranchingPolicies(policies);
    }

    logger.info(
        String.format(
            "Using jgitver-maven-plugin [%s] (sha1: %s)",
            JGitverMavenPluginProperties.getVersion(), JGitverMavenPluginProperties.getSHA1()));
    long start = System.currentTimeMillis();

    String computedVersion = gitVersionCalculator.getVersion();

    long duration = System.currentTimeMillis() - start;
    logger.info(String.format("    version '%s' computed in %d ms", computedVersion, duration));
    logger.info("");

    boolean isDirty =
        gitVersionCalculator.meta(Metadatas.DIRTY).map(Boolean::parseBoolean).orElse(Boolean.FALSE);

    if (cfg.failIfDirty && isDirty) {
      throw new IllegalStateException("repository is dirty");
    }

    JGitverInformationProvider infoProvider = Providers.decorate(gitVersionCalculator);
    JGitverInformationProvider finalInfoProvider = infoProvider;
    infoProvider =
        JGitverUtils.versionOverride(mavenSession, logger)
            .map(version -> Providers.fixVersion(version, finalInfoProvider))
            .orElse(infoProvider);

    // Put metadatas into Maven session properties
    JGitverUtils.fillPropertiesFromMetadatas(
        mavenSession.getUserProperties(), infoProvider, logger);

    // Put metadatas into exportedProps file (if requested)
    Optional<String> exportPropsPathOpt = JGitverUtils.exportPropertiesPath(mavenSession, logger);
    if (exportPropsPathOpt.isPresent()) {
      Properties exportedProps = new Properties();
      JGitverUtils.fillPropertiesFromMetadatas(exportedProps, infoProvider, logger);
      String exportPropsPath = exportPropsPathOpt.get();
      try (OutputStream os = new FileOutputStream(exportPropsPath)) {
        exportedProps.store(
            os, "Output from " + JGitverUtils.EXTENSION_ARTIFACT_ID + " execution.");
        logger.info("Properties exported to file \"" + exportPropsPath + "\"");
      } catch (IOException ex) {
        throw new IllegalArgumentException(
            "Cannot write properties to file \"" + exportPropsPath + "\"", ex);
      }
    }

    return new JGitverSession(infoProvider, rootDirectory);
  }
}
