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
import io.vavr.CheckedConsumer;
import io.vavr.CheckedFunction0;
import io.vavr.control.Try;
import java.io.Closeable;
import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import javax.inject.Inject;
import javax.inject.Named;
import org.apache.maven.execution.MavenSession;

/**
 * Holds JGitverSession instances for different Maven sessions. This class is designed to be
 * compatible with m2e, where each project in the workspace is built separately with its own
 * MavenSession. Therefore, each MavenSession (identified by its root directory) can have its own
 * JGitverSession.
 */
@Named
@Singleton
public class JGitverSessionHolder {

  private Map<File, Try<JGitverSession>> sessions = new ConcurrentHashMap<>();

  private @Inject JGitverSessionOpener sessionOpener;

  /**
   * Retrieves the JGitverSession associated with a MavenSession. The JGitverSession is identified
   * by the root directory of the MavenSession. The root directory is retrieved from the
   * MavenSession's request's multi-module project directory.
   *
   * @param mavenSession the MavenSession, its request's multi-module project directory is used as
   *     the key
   * @return the JGitverSession associated with the MavenSession, or an empty Optional if no session
   *     is associated
   */
  Locker session(MavenSession mavenSession) {
    return session(
        mavenSession,
        () -> {
          throw new UnsupportedOperationException();
        });
  }

  /**
   * Associates a JGitverSession with a MavenSession. The JGitverSession is identified by the root
   * directory of the MavenSession. The root directory is retrieved from the MavenSession's
   * request's multi-module project directory.
   *
   * @param mavenSession the MavenSession, its request's multi-module project directory is used as
   *     the key
   * @param supplier the JGitverSession supplier to be associated with the MavenSession
   */
  Locker session(MavenSession mavenSession, CheckedFunction0<JGitverSession> supplier) {
    return new Locker(sessions.computeIfAbsent(sessionKey(mavenSession), k -> Try.of(supplier)));
  }

  /**
   * Ensure that the JGitverSession associated with a MavenSession is opened. The JGitverSession is
   * identified by the root directory of the project. The root directory is retrieved from the
   * MavenSession's request's multi-module project directory.
   *
   * @param mavenSession the MavenSession, its request's multi-module project directory is used as
   *     the key
   */
  void ensureSessionOpened(MavenSession mavenSession) {
    session(mavenSession, () -> sessionOpener.openSession(mavenSession));
  }

  /**
   * Removes the JGitverSession associated with a MavenSession. The JGitverSession is identified by
   * the root directory of the MavenSession. The root directory is retrieved from the MavenSession's
   * request's multi-module project directory.
   *
   * @param mavenSession the MavenSession, its request's multi-module project directory is used as
   *     the key
   */
  void closeSession(MavenSession mavenSession) {
    sessions.remove(sessionKey(mavenSession));
  }

  File sessionKey(MavenSession mavenSession) {
    return mavenSession.getRequest().getMultiModuleProjectDirectory();
  }

  // ensure sessions are not entered concurrently

  final Semaphore semaphore = new Semaphore(1);

  class CloseableLock implements Closeable {
    public CloseableLock() {
      try {
        semaphore.acquire();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException("Failed to acquire semaphore", e);
      }
    }

    @Override
    public void close() {
      semaphore.release();
    }
  }

  class Locker {
    private final Try<JGitverSession> resource;

    Locker(Try<JGitverSession> resource) {
      this.resource = resource;
    }

    Try<JGitverSession> andThenTry(CheckedConsumer<JGitverSession> consumer) {
      try (CloseableLock lock = new CloseableLock()) {
        return resource.andThenTry(consumer);
      }
    }

    <X extends Throwable> JGitverSession getOrElseThrow(
        Function<? super Throwable, X> exceptionProvider) throws X {
      return resource.getOrElseThrow(exceptionProvider);
    }
  }
}
