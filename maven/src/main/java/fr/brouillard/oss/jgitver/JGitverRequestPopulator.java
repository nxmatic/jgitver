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
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import javax.inject.Inject;
import org.apache.maven.bridge.MavenRepositorySystem;
import org.apache.maven.cli.MavenCli;
import org.apache.maven.cli.event.DefaultEventSpyContext;
import org.apache.maven.eventspy.internal.EventSpyDispatcher;
import org.apache.maven.execution.DefaultMavenExecutionRequestPopulator;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequestPopulationException;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;

public class JGitverRequestPopulator extends DefaultMavenExecutionRequestPopulator {

  PlexusContainer container;

  @Inject
  public JGitverRequestPopulator(
      MavenRepositorySystem repositorySystem, PlexusContainer container) {
    super(repositorySystem);
    this.container = container;
  }

  @Override
  public MavenExecutionRequest populateDefaults(MavenExecutionRequest request)
      throws MavenExecutionRequestPopulationException {
    return Try.of(() -> super.populateDefaults(request))
        .andThenTry(this::populateEventSpyDispatcher)
        .get();
  }

  MavenExecutionRequest populateEventSpyDispatcher(MavenExecutionRequest request)
      throws ComponentLookupException {
    EventSpyDispatcher dispatcher = container.lookup(EventSpyDispatcher.class);

    DefaultEventSpyContext eventSpyContext = new DefaultEventSpyContext();
    Map<String, Object> data = eventSpyContext.getData();
    data.put("plexus", container);
    data.put("workingDirectory", request.getBaseDirectory()); // to adapt
    data.put("systemProperties", request.getSystemProperties());
    data.put("userProperties", request.getUserProperties());
    data.put("versionProperties", getBuildProperties());
    dispatcher.init(eventSpyContext);

    return request;
  }

  Properties getBuildProperties() {
    Properties properties = new Properties();

    try (InputStream resourceAsStream =
        MavenCli.class.getResourceAsStream("/org/apache/maven/messages/build.properties")) {

      if (resourceAsStream != null) {
        properties.load(resourceAsStream);
      }
    } catch (IOException e) {
      System.err.println("Unable determine version from JAR file: " + e.getMessage());
    }

    return properties;
  }
}
