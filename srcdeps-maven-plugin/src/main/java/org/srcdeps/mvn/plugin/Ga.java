/**
 * Copyright 2015-2017 Maven Source Dependencies
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
package org.srcdeps.mvn.plugin;

/**
 * An immutable {@link #groupId}, {@link #artifactId} pair with a fast {@link #hashCode()} and {@link #equals(Object)}.
 *
 * @author <a href="https://github.com/ppalaga">Peter Palaga</a>
 */
public class Ga implements Comparable<Ga> {
    private final String artifactId;
    private final String groupId;
    private final int hashCode;

    public Ga(String groupId, String artifactId) {
        super();
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.hashCode = 31 * (31 + artifactId.hashCode()) + groupId.hashCode();
    }

    @Override
    public int compareTo(Ga other) {
        int g = this.groupId.compareTo(other.groupId);
        if (g != 0) {
            return g;
        } else {
            return this.artifactId.compareTo(other.artifactId);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Ga other = (Ga) obj;
        return this.artifactId.equals(other.artifactId) && this.groupId.equals(other.groupId);
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getGroupId() {
        return groupId;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return groupId + ":" + artifactId;
    }
}
