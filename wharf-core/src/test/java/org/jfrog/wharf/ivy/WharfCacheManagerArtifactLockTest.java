/*
 *
 *  Copyright (C) 2010 JFrog Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * /
 */
package org.jfrog.wharf.ivy;

import org.apache.ivy.core.cache.RepositoryCacheManager;
import org.apache.ivy.core.event.EventManager;
import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.resolve.ResolveData;
import org.apache.ivy.core.resolve.ResolveEngine;
import org.apache.ivy.core.resolve.ResolveOptions;
import org.apache.ivy.core.resolve.ResolvedModuleRevision;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.core.sort.SortEngine;
import org.apache.ivy.plugins.lock.ArtifactLockStrategy;
import org.apache.ivy.plugins.repository.RepositoryCopyProgressListener;
import org.apache.ivy.plugins.repository.file.FileRepository;
import org.apache.ivy.plugins.resolver.FileSystemResolver;
import org.apache.ivy.util.CopyProgressEvent;
import org.apache.ivy.util.FileUtil;
import org.apache.ivy.util.Message;
import org.jfrog.wharf.ivy.cache.WharfCacheManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.text.ParseException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class WharfCacheManagerArtifactLockTest {

    private File cache = new File("build/test/cache");

    @Before
    public void setUp() throws Exception {
        FileUtil.forceDelete(cache);
    }

    @After
    public void tearDown() throws Exception {
        FileUtil.forceDelete(cache);
    }

    @Test
    public void testConcurrentResolve() throws Exception {
        // we use different settings because Ivy do not support multi thread resolve with the same
        // settings yet and this is not what this test is about: the focus of this test is running
        // concurrent resolves in separate vms but using the same cache. We don't span the test on
        // multiple vms, but using separate settings we should only run into shared cache related
        // issues, and not multi thread related issues.
        IvySettings settings1 = new IvySettings();
        settings1.setDefaultCache(cache);
        IvySettings settings2 = new IvySettings();
        settings2.setDefaultCache(cache);
        IvySettings settings3 = new IvySettings();
        settings3.setDefaultCache(cache);
        IvySettings settings4 = new IvySettings();
        settings4.setDefaultCache(cache);

        // run 3 concurrent resolves, one taking 100ms to download files, one 20ms and one 5ms
        // the first one do 10 resolves, the second one 20 and the third 50
        // note that the download time is useful only at the very beginning, then the cached file is used
        ResolveThread t1 = asyncResolve(
                settings1, createSlowResolver(settings1, 100), "org6#mod6.4;3", 10);
        ResolveThread t2 = asyncResolve(
                settings2, createSlowResolver(settings2, 20), "org6#mod6.4;3", 20);
        ResolveThread t3 = asyncResolve(
                settings3, createSlowResolver(settings3, 5), "org6#mod6.4;3", 50);
        ResolveThread t4 = asyncResolve(
                settings3, createSlowResolver(settings4, 5), "org6#mod6.2;2.0", 50);
        t1.join(100000);
        t2.join(20000);
        t3.join(20000);
        t4.join(20000);
        assertEquals(10, t1.getCount());
        assertFound("org6#mod6.4;3", t1.getFinalResult());
        assertEquals(20, t2.getCount());
        assertFound("org6#mod6.4;3", t2.getFinalResult());
        assertEquals(50, t3.getCount());
        assertFound("org6#mod6.4;3", t3.getFinalResult());
        assertEquals(50, t4.getCount());
        assertFound("org6#mod6.2;2.0", t4.getFinalResult());
    }

    private RepositoryCacheManager newCacheManager(IvySettings settings) {
        WharfCacheManager cacheManager = WharfCacheManager.newInstance(settings, "cache", cache);
        cacheManager.getMetadataHandler().setLockStrategy(new ArtifactLockStrategy());
        return cacheManager;
    }


    private FileSystemResolver createSlowResolver(IvySettings settings, final int sleep) {
        FileSystemResolver resolver = new FileSystemResolver();
        resolver.setRepositoryCacheManager(newCacheManager(settings));
        resolver.setRepository(new FileRepository() {
            private RepositoryCopyProgressListener progress = new RepositoryCopyProgressListener(this) {
                @Override
                public void progress(CopyProgressEvent evt) {
                    super.progress(evt);
                    sleepSilently(sleep); // makes the file copy longer to test concurrency issues
                }
            };

            @Override
            protected RepositoryCopyProgressListener getProgressListener() {
                return progress;
            }
        });
        resolver.setName("test");
        resolver.setSettings(settings);
        File baseDir = new File(settings.getBaseDir().getParentFile(), "src");
        resolver.addIvyPattern(baseDir +
                "/test/repositories/1/[organisation]/[module]/[type]s/[artifact]-[revision].[ext]");
        resolver.addArtifactPattern(baseDir +
                "/test/repositories/1/[organisation]/[module]/[type]s/[artifact]-[revision].[ext]");
        settings.addResolver(resolver);
        return resolver;
    }


    private ResolveThread asyncResolve(IvySettings settings, FileSystemResolver resolver, String module, int loop) {
        ResolveThread thread = new ResolveThread(settings, resolver, module, loop);
        thread.start();
        return thread;
    }


    private void assertFound(String module, ResolvedModuleRevision rmr) {
        assertNotNull(rmr);
        assertEquals(module, rmr.getId().toString());
    }

    private ResolvedModuleRevision resolveModule(IvySettings settings, FileSystemResolver resolver, String module)
            throws ParseException {
        return resolver.getDependency(new DefaultDependencyDescriptor(ModuleRevisionId.parse(module), false),
                new ResolveData(new ResolveEngine(settings, new EventManager(), new SortEngine(settings)),
                        new ResolveOptions()));
    }

    private void sleepSilently(int timeout) {
        try {
            Thread.sleep(timeout);
        } catch (InterruptedException e) {
        }
    }

    private class ResolveThread extends Thread {
        private IvySettings settings;
        private FileSystemResolver resolver;
        private String module;
        private final int loop;

        private ResolvedModuleRevision finalResult;
        private int count;

        public ResolveThread(IvySettings settings, FileSystemResolver resolver, String module, int loop) {
            this.settings = settings;
            this.resolver = resolver;
            this.module = module;
            this.loop = loop;
        }

        public synchronized ResolvedModuleRevision getFinalResult() {
            return finalResult;
        }

        public synchronized int getCount() {
            return count;
        }

        @Override
        public void run() {
            ResolvedModuleRevision rmr = null;
            for (int i = 0; i < loop; i++) {
                try {
                    rmr = resolveModule(settings, resolver, module);
                    if (rmr == null) {
                        throw new RuntimeException("module not found: " + module);
                    }
                    synchronized (this) {
                        //Message.info(this.toString() + " count = " + count);
                        count++;
                    }
                } catch (ParseException e) {
                    Message.info("parse exception " + e);
                } catch (RuntimeException e) {
                    Message.info("exception " + e);
                    e.printStackTrace();
                    throw e;
                } catch (Error e) {
                    Message.info("exception " + e);
                    e.printStackTrace();
                    throw e;
                }
            }
            synchronized (this) {
                finalResult = rmr;
            }
        }
    }

}
