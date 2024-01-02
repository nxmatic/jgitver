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
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.building.ModelProcessor;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.logging.Logger;
import org.commonjava.maven.ext.core.ManipulatingExtensionBridge;

@Named("jgitver")
@Singleton
public class JGitverExtension extends AbstractMavenLifecycleParticipant {
  @Inject private Logger logger;

  @Inject private ModelProcessor modelProcessor;

  @Inject private JGitverSessionHolder sessionHolder;

  @Inject private JGitverConfiguration configurationProvider;

  @Inject private ManipulatingExtensionBridge manipulatingBridge;

  /**
   * Called after the Maven session starts. If jgitver is not skipped, it opens a new JGitverSession
   * and associates it with the MavenSession.
   *
   * @param mavenSession the MavenSession that just started
   * @throws MavenExecutionException if an error occurs while opening the JGitverSession
   */
  @Override
  public void afterSessionStart(MavenSession mavenSession) throws MavenExecutionException {
    if (JGitverUtils.shouldSkip(mavenSession)) {
      logger.info("jgitver execution has been skipped by request of the user");
      sessionHolder.closeSession(mavenSession);
    } else {
      sessionHolder.ensureSessionOpened(mavenSession);
    }
  }

  /**
   * Called after the Maven session ends. It removes the JGitverSession associated with the
   * MavenSession.
   *
   * @param session the MavenSession that just ended
   * @throws MavenExecutionException if an error occurs while removing the JGitverSession
   */
  @Override
  public void afterSessionEnd(MavenSession mavenSession) throws MavenExecutionException {
    sessionHolder.closeSession(mavenSession);
  }

  /**
   * Called after the projects have been read. This method ensures that a JGitverSession exists for
   * the MavenSession, as the afterSessionStart method is not invoked by M2E. If jgitver is not
   * skipped, it opens a new JGitverSession and associates it with the MavenSession. Note that a
   * single MavenSession can be involved in multiple projects when invoked by M2E.
   *
   * @param session the MavenSession that just started
   * @param projects the projects that have been read
   * @throws MavenExecutionException if an error occurs while opening the JGitverSession
   */
  @Override
  public void afterProjectsRead(MavenSession mavenSession) throws MavenExecutionException {
    if (JGitverUtils.shouldSkip(mavenSession)) {
      return;
    }
    if (!JGitverModelProcessor.class.isAssignableFrom(modelProcessor.getClass())) {
      return;
    }

    File projectBaseDir = mavenSession.getCurrentProject().getBasedir();
    if (projectBaseDir == null) {
      return;
    }

    Try.run(
            () -> {
              if (configurationProvider.ignore(
                  projectBaseDir.toPath().resolve("pom.xml").toFile())) {
                return;
              }
              if (isRunningInM2E(mavenSession)) {
                m2eInvoker.invoke(() -> sessionHolder.ensureSessionOpened(mavenSession));
              }
              sessionHolder
                  .session(mavenSession)
                  .andThenTry(
                      jgitverSession -> {
                        // log report
                        logger.info(
                            "jgitver-maven-plugin is about to change project(s) version(s)");

                        jgitverSession
                            .getProjects()
                            .forEach(
                                gav ->
                                    logger.info(
                                        "    "
                                            + jgitverSession.getOriginalGAV(
                                                gav, manipulatingBridge.readReport(mavenSession))
                                            + " -> "
                                            + jgitverSession.getVersion()));
                      });
            })
        .getOrElseThrow(
            cause -> new MavenExecutionException("cannot evaluate : " + projectBaseDir, cause));
  }

  private final Invoker m2eInvoker =
      new ChainedInvoker(new ThreadSafeInvoker(), new ClassRealmContextInjector());

  @FunctionalInterface
  private interface Invokable<T> {
    T invoke();
  }

  @FunctionalInterface
  private interface Runnable<T> {
    void invoke();
  }

  private interface Invoker {
    <T> T invoke(Invokable<T> invokable);

    default void invoke(Runnable<Void> runnable) {
      invoke(
          () -> {
            runnable.invoke();
            return null;
          });
    }
  }

  private class ChainedInvoker implements Invoker {
    private final List<Invoker> invokers;

    public ChainedInvoker(Invoker... invokers) {
      this.invokers = Arrays.asList(invokers);
    }

    @Override
    public <T> T invoke(Invokable<T> invokable) {
      Invokable<T> chainedInvokable = invokable;
      for (int i = invokers.size() - 1; i >= 0; i--) {
        Invoker invoker = invokers.get(i);
        final Invokable<T> finalChainedInvokable = chainedInvokable;
        chainedInvokable = () -> invoker.invoke(finalChainedInvokable);
      }
      return chainedInvokable.invoke();
    }
  }

  private class ClassRealmContextInjector implements Invoker {
    final ClassRealm classRealm = loadClassRealm();

    public <T> T invoke(Invokable<T> invokable) {
      ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
      try {
        Thread.currentThread().setContextClassLoader(classRealm);
        return invokable.invoke();
      } finally {
        Thread.currentThread().setContextClassLoader(originalClassLoader);
      }
    }

    ClassRealm loadClassRealm() {
      return (ClassRealm) GitVersionCalculatorBuilder.class.getClassLoader();
    }
  }

  private class ThreadSafeInvoker implements Invoker {

    @SuppressWarnings("unused")
    private static final long serialVersionUID = 1L;

    private final ReentrantLock lock = new ReentrantLock();

    public <T> T invoke(Invokable<T> invokable) {
      lock.lock(); // block until condition holds
      try {
        return invokable.invoke();
      } finally {
        lock.unlock();
      }
    }
  }

  private boolean isRunningInM2E(MavenSession session) {
    return session.getUserProperties().containsKey("m2e.version");
  }
}
