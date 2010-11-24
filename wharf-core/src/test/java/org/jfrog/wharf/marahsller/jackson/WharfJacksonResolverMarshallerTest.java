package org.jfrog.wharf.marahsller.jackson;

import org.jfrog.wharf.ivy.marshall.jackson.WharfJacksonResolverMarshallerImpl;
import org.jfrog.wharf.ivy.model.WharfResolverMetadata;
import org.jfrog.wharf.util.CacheCleaner;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * @author Tomer Cohen
 */
public class WharfJacksonResolverMarshallerTest {

    WharfJacksonResolverMarshallerImpl wharfJacksonResolverMarshaller = new WharfJacksonResolverMarshallerImpl();
    private File cacheDir;

    @Before
    public void setup() {
        cacheDir = new File("build/test/cache");
    }

    @After
    public void tearDown() {
        CacheCleaner.deleteDir(cacheDir);
    }

    @Test
    public void saveAndRead() {
        Set<WharfResolverMetadata> wharfResolverMetadatas = new HashSet<WharfResolverMetadata>();
        WharfResolverMetadata metadataA = new WharfResolverMetadata();
        metadataA.name = "a";
        metadataA.type = "typeA";
        metadataA.authentication = "auth";
        metadataA.proxy = "proxy";
        wharfResolverMetadatas.add(metadataA);

        WharfResolverMetadata metadataB = new WharfResolverMetadata();
        metadataB.name = "b";
        metadataB.type = "typeB";
        metadataB.authentication = "authB";
        metadataB.proxy = "proxyB";
        wharfResolverMetadatas.add(metadataB);
        wharfJacksonResolverMarshaller.save(cacheDir, wharfResolverMetadatas);

        Set<WharfResolverMetadata> metadatas = wharfJacksonResolverMarshaller.getWharfMetadatas(cacheDir);
        Iterator<WharfResolverMetadata> iterator = metadatas.iterator();
        Assert.assertEquals(2, metadatas.size());
        Assert.assertEquals(metadataA, iterator.next());
        Assert.assertEquals(metadataB, iterator.next());
    }
}
