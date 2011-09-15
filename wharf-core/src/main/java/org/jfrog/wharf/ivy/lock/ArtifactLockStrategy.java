/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.jfrog.wharf.ivy.lock;

import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.plugins.lock.LockStrategy;

import java.io.File;

public class ArtifactLockStrategy implements LockStrategy {

    private WharfLockFactory factory;

    private String name;

    public ArtifactLockStrategy(WharfLockFactory factory, String name) {
        this.factory = factory;
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean lockArtifact(Artifact artifact, File artifactFileToDownload) throws InterruptedException {
        return factory.getOrCreateLockHolder(artifactFileToDownload).acquireLock();
    }

    @Override
    public void unlockArtifact(Artifact artifact, File artifactFileToDownload) {
        LockHolder lockHolder = factory.getLockHolder(artifactFileToDownload);
        if (lockHolder != null) {
            lockHolder.releaseLock();
        }
    }
}
