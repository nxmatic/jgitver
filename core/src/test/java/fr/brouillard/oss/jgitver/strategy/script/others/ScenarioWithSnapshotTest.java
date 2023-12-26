/**
 * Copyright (C) 2016 Matthieu Brouillard [http://oss.brouillard.fr/jgitver] (matthieu@brouillard.fr)
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
package fr.brouillard.oss.jgitver.strategy.script.others;

import fr.brouillard.oss.jgitver.ScenarioTest;
import fr.brouillard.oss.jgitver.Scenarios;
import fr.brouillard.oss.jgitver.Strategies;
import fr.brouillard.oss.jgitver.impl.VersionCalculationException;
import fr.brouillard.oss.jgitver.metadata.Metadatas;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static fr.brouillard.oss.jgitver.impl.Lambdas.unchecked;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScenarioWithSnapshotTest {

    @Nested
    class InvalidVersion1 extends ScenarioTest {
        InvalidVersion1() {
            super(
                    Scenarios::s1_linear_with_only_annotated_tags,
                    calculator -> calculator
                            .setStrategy(Strategies.SCRIPT)
                    .setScript("print '1'")); // Only major
        }

        @Test
        void should_fail() {
            final IllegalStateException exception = assertThrows(IllegalStateException.class, () -> versionCalculator.getVersion());

            assertThat(exception.getCause(), instanceOf(VersionCalculationException.class));
        }

    }

    @Nested
    class InvalidVersion12 extends ScenarioTest {
        InvalidVersion12() {
            super(
                    Scenarios::s1_linear_with_only_annotated_tags,
                    calculator -> calculator
                            .setStrategy(Strategies.SCRIPT)
                            .setScript("print '1;2'")); // Missing patch
        }

        @Test
        void should_fail() {
            final IllegalStateException exception = assertThrows(IllegalStateException.class, () -> versionCalculator.getVersion());

            assertThat(exception.getCause(), instanceOf(VersionCalculationException.class));
        }
    }

    @Nested
    class InvalidVersionA00 extends ScenarioTest {
        InvalidVersionA00() {
            super(
                    Scenarios::s1_linear_with_only_annotated_tags,
                    calculator -> calculator
                            .setStrategy(Strategies.SCRIPT)
                            .setScript("print 'A;0;0'")); // Missing patch
        }

        @Test
        void should_fail() {
            final IllegalStateException exception = assertThrows(IllegalStateException.class, () -> versionCalculator.getVersion());

            assertThat(exception.getCause(), instanceOf(VersionCalculationException.class));
        }
    }

    @Nested
    class InvalidVersion1B0 extends ScenarioTest {
        InvalidVersion1B0() {
            super(
                    Scenarios::s1_linear_with_only_annotated_tags,
                    calculator -> calculator
                            .setStrategy(Strategies.SCRIPT)
                            .setScript("print '1;B;0'")); // Missing patch
        }

        @Test
        void should_fail() {
            final IllegalStateException exception = assertThrows(IllegalStateException.class, () -> versionCalculator.getVersion());

            assertThat(exception.getCause(), instanceOf(VersionCalculationException.class));
        }
    }

    @Nested
    class InvalidVersion12C extends ScenarioTest {
        InvalidVersion12C() {
            super(
                    Scenarios::s1_linear_with_only_annotated_tags,
                    calculator -> calculator
                            .setStrategy(Strategies.SCRIPT)
                            .setScript("print '1;2;C'")); // Missing patch
        }

        @Test
        void should_fail() {
            final IllegalStateException exception = assertThrows(IllegalStateException.class, () -> versionCalculator.getVersion());

            assertThat(exception.getCause(), instanceOf(VersionCalculationException.class));
        }
    }

    @Nested
    class InvalidVersionScript extends ScenarioTest {
        InvalidVersionScript() {
            super(
                    Scenarios::s1_linear_with_only_annotated_tags,
                    calculator -> calculator
                            .setStrategy(Strategies.SCRIPT)
                            .setScript("invalid; print '1;0;0'"));
        }

        @Test
        void should_fail() {
            final IllegalStateException exception = assertThrows(IllegalStateException.class, () -> versionCalculator.getVersion());

            assertThat(exception.getCause(), instanceOf(VersionCalculationException.class));
        }
    }

    @Nested
    class MetadataQualifiers extends ScenarioTest {
        private static final String s =
            "print '1;2;3;'"
            + " + metadata.CURRENT_VERSION_MAJOR + ';'"
            + " + metadata.HEAD_TAGS + ';'"
            + " + metadata.QUALIFIED_BRANCH_NAME + ';'"
            + " + metadata.DIRTY + ';'"
            + " + metadata.COMMIT_DISTANCE + ';'";

        MetadataQualifiers() {
            super(
                    Scenarios::s1_linear_with_only_annotated_tags,
                    calculator -> calculator
                            .setStrategy(Strategies.SCRIPT).setScript(s));
        }

        @Test
        void foo() {
            assertThat(versionCalculator.getVersion(),
                       is("1.2.3-2--null-false-1"));
        }
    }

    @Nested
    class Scenario1 extends ScenarioTest {
        private static final String s =
            "def qualifiers = [];" +
            "if (!metadata.HEAD_TAGS) {" +
            "  qualifiers.add(metadata.COMMIT_DISTANCE)" +
            "};" +
            "print"
            + " metadata.CURRENT_VERSION_MAJOR + ';'"
            + "+ metadata.CURRENT_VERSION_MINOR + ';'"
            + "+ metadata.CURRENT_VERSION_PATCH + ';'"
            + "+ qualifiers.join(';')";

        /**
         * Builds the following repository
         * <pre>
         * $ git log --graph --abbrev-commit --decorate --format=format:'%h - (%ar) %s - %an %d'
         * * 80eee6d - (18 seconds ago) content E - Matthieu Brouillard (HEAD -> master)
         * * 98358d0 - (18 seconds ago) content D - Matthieu Brouillard (tag: 2.0.0)
         * * 00a993e - (18 seconds ago) content C - Matthieu Brouillard
         * * 183ccc6 - (18 seconds ago) content B - Matthieu Brouillard (tag: 1.0.0)
         * * b048402 - (18 seconds ago) content A - Matthieu Brouillard
         * </pre>
         * @return the scenario object corresponding to the above git repository
         */
        Scenario1() {
            super(
                    Scenarios::s1_linear_with_only_annotated_tags,
                    calculator -> calculator
                            .setStrategy(Strategies.SCRIPT).setScript(s));
        }

        @Test
        void head_is_on_master_by_default() throws Exception {
            assertThat(repository.getBranch(), is("master"));
        }

        @Test
        void version_on_normal_tag_is_tag_value() {
            Arrays.asList("1.0.0", "2.0.0").forEach(tag -> {
                // when tag is checkout
                unchecked(() -> git.checkout().setName(tag).call());
                // the version matches the tag
                assertThat(versionCalculator.getVersion(), is(tag));
            });
        }

        @Test
        void version_of_A_commit() {
            ObjectId cCommit = scenario.getCommits().get("A");

            // checkout the commit in scenario
            unchecked(() -> git.checkout().setName(cCommit.name()).call());
            assertThat(versionCalculator.getVersion(), is("0.0.0-0"));
        }

        @Test
        void version_of_B_commit() {
            ObjectId cCommit = scenario.getCommits().get("B");

            // checkout the commit in scenario
            unchecked(() -> git.checkout().setName(cCommit.name()).call());
            assertThat(versionCalculator.getVersion(), is("1.0.0"));
        }

        @Test
        void version_of_C_commit() {
            ObjectId cCommit = scenario.getCommits().get("C");

            // checkout the commit in scenario
            unchecked(() -> git.checkout().setName(cCommit.name()).call());
            assertThat(versionCalculator.getVersion(), is("1.0.0-1"));
        }

        @Test
        void version_of_D_commit() {
            ObjectId cCommit = scenario.getCommits().get("D");

            // checkout the commit in scenario
            unchecked(() -> git.checkout().setName(cCommit.name()).call());
            assertThat(versionCalculator.getVersion(), is("2.0.0"));
        }

        @Test
        void version_of_E_commit() {
            ObjectId cCommit = scenario.getCommits().get("E");

            // checkout the commit in scenario
            unchecked(() -> git.checkout().setName(cCommit.name()).call());
            assertThat(versionCalculator.getVersion(), is("2.0.0-1"));
        }

        @Test
        void version_of_master() {
            // checkout the commit in scenario
            unchecked(() -> git.checkout().setName("master").call());
            assertThat(versionCalculator.getVersion(), is("2.0.0-1"));

            assertThat(versionCalculator.meta(Metadatas.NEXT_MAJOR_VERSION).get(), is("3.0.0"));
            assertThat(versionCalculator.meta(Metadatas.NEXT_MINOR_VERSION).get(), is("2.1.0"));
            assertThat(versionCalculator.meta(Metadatas.NEXT_PATCH_VERSION).get(), is("2.0.1"));
        }
    }
}
