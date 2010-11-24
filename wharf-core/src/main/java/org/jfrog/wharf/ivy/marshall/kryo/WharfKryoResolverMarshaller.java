package org.jfrog.wharf.ivy.marshall.kryo;


import com.esotericsoftware.kryo.ObjectBuffer;
import org.jfrog.wharf.ivy.marshall.api.WharfResolverMarshaller;
import org.jfrog.wharf.ivy.model.WharfResolverMetadata;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Tomer Cohen
 */
public class WharfKryoResolverMarshaller implements WharfResolverMarshaller {
    private static final String RESOLVERS_FILE_PATH = ".wharf/resolvers.kryo";

    @Override
    public String getResolversFilePath() {
        return RESOLVERS_FILE_PATH;
    }

    @Override
    public void save(File baseDir, Set<WharfResolverMetadata> wharfResolverMetadatas) {
        File resolversFile = new File(baseDir, RESOLVERS_FILE_PATH);
        OutputStream stream = null;
        try {
            File dir = resolversFile.getParentFile();
            if (!dir.exists()) {
                dir.mkdirs();
            }
            stream = new FileOutputStream(resolversFile);
            ObjectBuffer buffer = KryoFactory.createWharfResolverObjectBuffer(WharfResolverMetadata.class);
            buffer.writeObject(stream, wharfResolverMetadatas);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if ((stream != null)) {
                try {
                    stream.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    @Override
    public Set<WharfResolverMetadata> getWharfMetadatas(File baseDir) {
        File resolversFile = new File(baseDir, RESOLVERS_FILE_PATH);
        if (resolversFile.exists()) {
            InputStream stream = null;
            try {
                stream = new FileInputStream(resolversFile);
                ObjectBuffer buffer = KryoFactory.createWharfResolverObjectBuffer(WharfResolverMetadata.class);
                return buffer.readObject(stream, HashSet.class);
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            } finally {
                if ((stream != null)) {
                    try {
                        stream.close();
                    } catch (IOException e) {
                        // ignore
                    }
                }
            }
        }
        return new HashSet<WharfResolverMetadata>();
    }
}
