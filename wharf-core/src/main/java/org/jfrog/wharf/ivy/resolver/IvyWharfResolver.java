package org.jfrog.wharf.ivy.resolver;

import org.apache.ivy.core.LogOptions;
import org.apache.ivy.core.cache.ArtifactOrigin;
import org.apache.ivy.core.cache.RepositoryCacheManager;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.report.ArtifactDownloadReport;
import org.apache.ivy.core.report.DownloadReport;
import org.apache.ivy.core.report.DownloadStatus;
import org.apache.ivy.core.resolve.DownloadOptions;
import org.apache.ivy.core.resolve.IvyNode;
import org.apache.ivy.core.resolve.ResolveData;
import org.apache.ivy.core.resolve.ResolvedModuleRevision;
import org.apache.ivy.plugins.parser.ModuleDescriptorParser;
import org.apache.ivy.plugins.parser.ModuleDescriptorParserRegistry;
import org.apache.ivy.plugins.repository.ArtifactResourceResolver;
import org.apache.ivy.plugins.repository.Resource;
import org.apache.ivy.plugins.resolver.IBiblioResolver;
import org.apache.ivy.plugins.resolver.util.ResolvedResource;
import org.apache.ivy.util.Checks;
import org.apache.ivy.util.ChecksumHelper;
import org.apache.ivy.util.FileUtil;
import org.apache.ivy.util.Message;
import org.jfrog.wharf.ivy.cache.WharfCacheManager;
import org.jfrog.wharf.ivy.model.ArtifactMetadata;
import org.jfrog.wharf.ivy.model.ModuleRevisionMetadata;
import org.jfrog.wharf.ivy.util.WharfUtils;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * @author Tomer Cohen
 */
public class IvyWharfResolver extends IBiblioResolver {
    protected static final String SHA1_ALGORITHM = "sha1";
    protected static final String MD5_ALGORITHM = "md5";

    protected static final String getChecksumAlgoritm() {
        return SHA1_ALGORITHM;
    }

    private final WharfResourceDownloader downloader = new WharfResourceDownloader(this);
    private final ArtifactResourceResolver artifactResourceResolver
            = new ArtifactResourceResolver() {
        @Override
        public ResolvedResource resolve(Artifact artifact) {
            artifact = fromSystem(artifact);
            return getArtifactRef(artifact, null);
        }
    };
    protected CacheTimeoutStrategy snapshotTimeout = DAILY;

    public IvyWharfResolver() {
        setChecksums(MD5_ALGORITHM + ", " + SHA1_ALGORITHM);
    }


    /**
     * Returns the timeout strategy for a Maven Snapshot in the cache
     */
    public CacheTimeoutStrategy getSnapshotTimeout() {
        return snapshotTimeout;
    }

    /**
     * Sets the time in ms a Maven Snapshot in the cache is not checked for a newer version
     *
     * @param snapshotLifetime The lifetime in ms
     */
    public void setSnapshotTimeout(long snapshotLifetime) {
        this.snapshotTimeout = new Interval(snapshotLifetime);
    }

    /**
     * Sets a timeout strategy for a Maven Snapshot in the cache
     *
     * @param cacheTimeoutStrategy The strategy
     */
    public void setSnapshotTimeout(CacheTimeoutStrategy cacheTimeoutStrategy) {
        this.snapshotTimeout = cacheTimeoutStrategy;
    }

    @Override
    protected ResolvedModuleRevision findModuleInCache(DependencyDescriptor dd, ResolveData data) {
        setChangingPattern(null);
        ResolvedModuleRevision moduleRevision = super.findModuleInCache(dd, data);
        if (moduleRevision == null) {
            setChangingPattern(".*-SNAPSHOT");
            return null;
        }
        ModuleRevisionMetadata metadata = getCacheProperties(dd, moduleRevision);
        WharfCacheManager cacheManager = (WharfCacheManager) getRepositoryCacheManager();
        updateCachePropertiesToCurrentTime(metadata);
        Long lastResolvedTime = getLastResolvedTime(metadata);
        cacheManager.getMetadataHandler().saveModuleRevisionMetadata(moduleRevision.getId(), metadata);
        if (snapshotTimeout.isCacheTimedOut(lastResolvedTime)) {
            setChangingPattern(".*-SNAPSHOT");
            return null;
        } else {
            return moduleRevision;
        }
    }

    @Override
    public ArtifactDownloadReport download(final ArtifactOrigin origin, DownloadOptions options) {
        Checks.checkNotNull(origin, "origin");
        return getRepositoryCacheManager().download(
                origin.getArtifact(),
                new ArtifactResourceResolver() {
                    @Override
                    public ResolvedResource resolve(Artifact artifact) {
                        try {
                            Resource resource = getResource(origin.getLocation());
                            if (resource == null) {
                                return null;
                            }
                            String revision = origin.getArtifact().getModuleRevisionId().getRevision();
                            return new ResolvedResource(resource, revision);
                        } catch (IOException e) {
                            return null;
                        }
                    }
                },
                downloader,
                getCacheDownloadOptions(options));
    }

    @Override
    public ResolvedModuleRevision parse(final ResolvedResource mdRef, DependencyDescriptor dd,
            ResolveData data) throws ParseException {

        DependencyDescriptor nsDd = dd;
        dd = toSystem(nsDd);

        ModuleRevisionId mrid = dd.getDependencyRevisionId();
        ModuleDescriptorParser parser = ModuleDescriptorParserRegistry
                .getInstance().getParser(mdRef.getResource());
        if (parser == null) {
            Message.warn("no module descriptor parser available for " + mdRef.getResource());
            return null;
        }
        Message.verbose("\t" + getName() + ": found md file for " + mrid);
        Message.verbose("\t\t=> " + mdRef);
        Message.debug("\tparser = " + parser);

        ModuleRevisionId resolvedMrid = mrid;

        // first check if this dependency has not yet been resolved
        if (getSettings().getVersionMatcher().isDynamic(mrid)) {
            resolvedMrid = ModuleRevisionId.newInstance(mrid, mdRef.getRevision());
            IvyNode node = data.getNode(resolvedMrid);
            if (node != null && node.getModuleRevision() != null) {
                // this revision has already be resolved : return it
                if (node.getDescriptor() != null && node.getDescriptor().isDefault()) {
                    Message.verbose("\t" + getName() + ": found already resolved revision: "
                            + resolvedMrid
                            + ": but it's a default one, maybe we can find a better one");
                } else {
                    Message.verbose("\t" + getName() + ": revision already resolved: "
                            + resolvedMrid);
                    node.getModuleRevision().getReport().setSearched(true);
                    return node.getModuleRevision();
                }
            }
        }

        Artifact moduleArtifact = parser.getMetadataArtifact(resolvedMrid, mdRef.getResource());
        return getRepositoryCacheManager().cacheModuleDescriptor(this, mdRef, dd, moduleArtifact, downloader,
                getCacheOptions(data));
    }

    @Override
    public DownloadReport download(Artifact[] artifacts, DownloadOptions options) {
        RepositoryCacheManager cacheManager = getRepositoryCacheManager();

        clearArtifactAttempts();
        DownloadReport dr = new DownloadReport();
        for (Artifact artifact : artifacts) {
            ArtifactDownloadReport adr = cacheManager.download(
                    artifact, artifactResourceResolver, downloader, getCacheDownloadOptions(options));
            if (DownloadStatus.FAILED == adr.getDownloadStatus()) {
                if (!ArtifactDownloadReport.MISSING_ARTIFACT.equals(adr.getDownloadDetails())) {
                    Message.warn("\t" + adr);
                }
            } else if (DownloadStatus.NO == adr.getDownloadStatus()) {
                Message.verbose("\t" + adr);
            } else if (LogOptions.LOG_QUIET.equals(options.getLog())) {
                Message.verbose("\t" + adr);
            } else {
                Message.info("\t" + adr);
            }
            dr.addArtifactReport(adr);
            checkInterrupted();
        }
        return dr;
    }

    @Override
    public long getAndCheck(Resource resource, File dest) throws IOException {
        // First get the checksum for this resource
        // TODO: [by fsi] The checksum value should be part of the resource object already (populated during the HEAD request)
        Resource csRes = resource.clone(resource.getName() + "." + getChecksumAlgoritm());
        String checksumValue;
        // TODO: [by fsi] Doing a stupid HEAD request on the XX.sha1 file! Waste of time...
        if (csRes.exists()) {
            File tempChecksum = File.createTempFile("temp", "." + getChecksumAlgoritm());
            get(csRes, tempChecksum);
            try {
                checksumValue = WharfUtils.getCleanChecksum(tempChecksum);
            } finally {
                FileUtil.forceDelete(tempChecksum);
            }
        } else {
            // The Wharf system enforce the presence of checksums on the remote repo
            throw new IOException(
                    "invalid " + getChecksumAlgoritm() + " checksum file " + csRes.getName() + " not found!");
        }
        WharfCacheManager cacheManager = (WharfCacheManager) getRepositoryCacheManager();
        File storageFile = cacheManager.getStorageFile(checksumValue);
        if (!storageFile.exists()) {
            // Not in storage cache
            if (!storageFile.getParentFile().exists()) {
                storageFile.getParentFile().mkdirs();
            }
            get(resource, storageFile);
            String downloadChecksum =
                    ChecksumHelper.computeAsString(storageFile, getChecksumAlgoritm()).trim().toLowerCase(Locale.US);
            if (!checksumValue.equals(downloadChecksum)) {
                FileUtil.forceDelete(storageFile);
                throw new IOException("invalid " + getChecksumAlgoritm() + ": expected=" + checksumValue + " computed="
                        + downloadChecksum);
            }
        }
        // If we get here, then the file was found in cache with the good checksum! just need to copy it
        // to the destination.
        WharfUtils.copyCacheFile(storageFile, dest);
        return dest.length();
    }


    private int getResolverIdByMd5(ModuleRevisionMetadata metadata, String md5) {
        for (ArtifactMetadata artMd : metadata.artifactMetadata) {
            if (md5.equals(artMd.md5)) {
                return artMd.artResolverId;
            }
        }
        return 0;
    }

    private int getResolverIdBySha1(ModuleRevisionMetadata metadata, String sha1) {
        for (ArtifactMetadata artMd : metadata.artifactMetadata) {
            if (sha1.equals(artMd.sha1)) {
                return artMd.artResolverId;
            }
        }
        return 0;
    }

    private void updateCachePropertiesToCurrentTime(ModuleRevisionMetadata cacheProperties) {
        cacheProperties.latestResolvedTime = String.valueOf(System.currentTimeMillis());
    }

    private Long getLastResolvedTime(ModuleRevisionMetadata cacheProperties) {
        String lastResolvedProp = cacheProperties.latestResolvedTime;
        Long lastResolvedTime = lastResolvedProp != null ? Long.parseLong(lastResolvedProp) : 0;
        return lastResolvedTime;
    }

    private ModuleRevisionMetadata getCacheProperties(DependencyDescriptor dd, ResolvedModuleRevision moduleRevision) {
        WharfCacheManager cacheManager = (WharfCacheManager) getRepositoryCacheManager();
        return cacheManager.getMetadataHandler().getModuleRevisionMetadata(moduleRevision.getId());
    }

    public interface CacheTimeoutStrategy {
        boolean isCacheTimedOut(long lastResolvedTime);
    }

    public static class Interval implements CacheTimeoutStrategy {
        private long interval;

        public Interval(long interval) {
            this.interval = interval;
        }

        @Override
        public boolean isCacheTimedOut(long lastResolvedTime) {
            return System.currentTimeMillis() - lastResolvedTime > interval;
        }
    }

    public static final CacheTimeoutStrategy NEVER = new CacheTimeoutStrategy() {
        @Override
        public boolean isCacheTimedOut(long lastResolvedTime) {
            return false;
        }
    };

    public static final CacheTimeoutStrategy ALWAYS = new CacheTimeoutStrategy() {
        @Override
        public boolean isCacheTimedOut(long lastResolvedTime) {
            return true;
        }
    };

    public static final CacheTimeoutStrategy DAILY = new CacheTimeoutStrategy() {
        @Override
        public boolean isCacheTimedOut(long lastResolvedTime) {
            Calendar calendarCurrent = Calendar.getInstance();
            calendarCurrent.setTime(new Date());
            int dayOfYear = calendarCurrent.get(Calendar.DAY_OF_YEAR);
            int year = calendarCurrent.get(Calendar.YEAR);

            Calendar calendarLastResolved = Calendar.getInstance();
            calendarLastResolved.setTime(new Date(lastResolvedTime));
            if (calendarLastResolved.get(Calendar.YEAR) == year &&
                    calendarLastResolved.get(Calendar.DAY_OF_YEAR) == dayOfYear) {
                return false;
            }
            return true;
        }
    };
}
