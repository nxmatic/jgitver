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

import io.vavr.control.Try;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Named;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Model;
import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.common.model.Project;
import org.commonjava.maven.ext.io.PomIO;

@Named
public class JGitverProjectVersioner {

  @Inject JGitverPomIO pomIO;

  @Inject JGitverSessionHolder sessionHolder;

  @Inject JGitverModelProcessor processor;

  /**
   * Parses the requested project POM file into a list of Project modules and updates their version
   * using the JGitver computed version.
   *
   * <p>This method uses the {@link PomIO} to write the POM file. The logic for this method is
   * mainly based onto the maven-pom-manipulation plugin IO module.
   *
   * @param session the current JGitver session
   * @param mavenSession the related maven session
   * @throws ManipulationException if an error occurs while manipulating the POM file (sneaky
   *     thrown)
   */
  void version(MavenSession mavenSession, JGitverSession session) {
    Try.run(
            () -> {
              Set<Project> modules =
                  pomIO.parseProject(mavenSession.getCurrentProject().getFile()).stream()
                      .peek(project -> setVersionOf(mavenSession, session, project))
                      .collect(Collectors.toSet());
              pomIO.writeTemporaryPOMs(modules);
              modules.stream()
                  .filter(Project::isExecutionRoot)
                  .findFirst()
                  .ifPresent(project -> mavenSession.getRequest().setPom(project.getPom()));
            })
        .get();
  }

  /**
   * Set the module maven version according to the JGitver computed version.
   *
   * @param session the JGitver session
   * @return module the manipulated model of the module
   */
  void setVersionOf(MavenSession mavenSession, JGitverSession session, Project module) {
    final Model model = module.getModel();
    final String version = session.getVersion();
    model.setVersion(version);
    if (!module.isExecutionRoot()) {
      model.getParent().setVersion(version);
    }
  }
}
