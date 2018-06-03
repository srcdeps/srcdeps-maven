/**
 * Copyright 2015-2018 Maven Source Dependencies
 * Plugin contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.srcdeps.mvn.itest;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.srcdeps.core.util.SrcdepsCoreUtils;

import io.takari.maven.testing.executor.MavenExecutionResult;

public class WrappedMavenExecutionResult {
    private final MavenExecutionResult delegate;

    public WrappedMavenExecutionResult(MavenExecutionResult delegate) {
        super();
        this.delegate = delegate;
    }

    public WrappedMavenExecutionResult assertErrorFreeLog() throws Exception {
        try {
            delegate.assertErrorFreeLog();
        } catch (AssertionError e) {
            dumpLog();
            e.printStackTrace();
            throw e;
        }
        return this;
    }

    public WrappedMavenExecutionResult assertLogText(String text) {
        try {
            delegate.assertLogText(text);
        } catch (AssertionError e) {
            dumpLog();
            e.printStackTrace();
            throw e;
        }
        return this;
    }

    public WrappedMavenExecutionResult assertLogTextPath(String text) {
        try {
            if (SrcdepsCoreUtils.isWindows()) {
                delegate.assertLogText(text.replace('/', File.separatorChar));
            } else {
                delegate.assertLogText(text);
            }
        } catch (AssertionError e) {
            dumpLog();
            e.printStackTrace();
            throw e;
        }
        return this;
    }

    public WrappedMavenExecutionResult assertNoLogText(String text) {
        try {
            delegate.assertNoLogText(text);
        } catch (AssertionError e) {
            dumpLog();
            e.printStackTrace();
            throw e;
        }
        return this;
    }

    private void dumpLog() {
        Path logPath = delegate.getBasedir().toPath().resolve("log.txt");

        System.out.println("");
        System.out.println("");
        System.out.println(" Maven test execution log start " + logPath);
        System.out.println("");
        System.out.println("");

        try {
            for (String line : Files.readAllLines(logPath, StandardCharsets.UTF_8)) {
                System.out.println(line);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println("");
        System.out.println("");
        System.out.println(" End " + logPath);
        System.out.println("");
        System.out.println("");
    }

    @Override
    public boolean equals(Object obj) {
        return delegate.equals(obj);
    }

    public File getBasedir() {
        return delegate.getBasedir();
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }

    @Override
    public String toString() {
        return delegate.toString();
    }
}
