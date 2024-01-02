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

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.maven.eventspy.AbstractEventSpy;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.ExecutionEvent.Type;
import org.apache.maven.execution.MavenSession;

/**
 * An event spy for Maven that ensures the project version is replaced with the JGitver calculated
 * version before Maven loads the model for processing. This class extends Maven's {@link
 * AbstractEventSpy} and is designed to work with JGitver. This class is annotated with {@link
 * Named} and {@link Singleton} to indicate that it should be used as a singleton component in a
 * dependency injection context, and that it can be referred to by the name "jgitver".
 */
@Named("jgitver")
@Singleton
public class JGitverEventSpy extends AbstractEventSpy {

  @Inject JGitverPomIO pomIO;

  @Inject JGitverSessionHolder sessionHolder;

  @Inject JGitverProjectVersioner projectVersioner;

  @Inject JGitverModelProcessor processor;

  /**
   * Handles Maven execution events. When a ProjectDiscoveryStarted event is received, this method
   * rewrites the execution root POM file with the version calculated by JGitver.
   *
   * @param event the Maven execution event
   * @throws Exception if an error occurs while handling the event
   */
  @Override
  public void onEvent(Object event) throws Exception {
    try {
      if (!(event instanceof ExecutionEvent)) {
        return;
      }

      final ExecutionEvent ee = (ExecutionEvent) event;
      if (ee.getType() != Type.ProjectDiscoveryStarted) {
        return;
      }

      MavenSession mavenSession = ee.getSession();

      sessionHolder.ensureSessionOpened(mavenSession);
    } finally {
      super.onEvent(event);
    }
  }
}
