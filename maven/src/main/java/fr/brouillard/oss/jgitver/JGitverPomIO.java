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

import com.google.inject.Singleton;
import javax.enterprise.context.SessionScoped;
import javax.inject.Named;
import org.commonjava.maven.ext.io.PomIO;

/**
 * A specialized version of the {@link PomIO} class that is designed to work with JGitver. It
 * provides a method to write down a project's POM (Project Object Model) instrumented by JGitver to
 * disk, This class is annotated with {@link Named} and {@link Singleton} to indicate that it should
 * be used as a singleton component in a dependency injection context, and that it can be referred
 * to by the name "jgitver".
 */
@Named("jgitver")
@SessionScoped
@Singleton
public class JGitverPomIO extends PomIO {

  public JGitverPomIO() {
    super();
  }
}
