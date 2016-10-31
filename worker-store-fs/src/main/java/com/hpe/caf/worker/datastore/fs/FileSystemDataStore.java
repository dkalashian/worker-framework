package com.hpe.caf.worker.datastore.fs;


import com.hpe.caf.api.HealthResult;
import com.hpe.caf.api.worker.DataStoreException;
import com.hpe.caf.api.worker.DataStoreMetricsReporter;
import com.hpe.caf.api.worker.ManagedDataStore;
import com.hpe.caf.api.worker.ReferenceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * This is a simple DataStore that reads and writes files to and from
 * a directory upon the file system. The store directory must be an
 * absolute path.
 */
public class FileSystemDataStore implements ManagedDataStore
{
    private Path dataStorePath;
    private final AtomicInteger errors = new AtomicInteger(0);
    private final AtomicInteger numRx = new AtomicInteger(0);
    private final AtomicInteger numTx = new AtomicInteger(0);
    private final DataStoreMetricsReporter metrics = new FileSystemDataStoreMetricsReporter();
    private static final Logger LOG = LoggerFactory.getLogger(FileSystemDataStore.class);


    /**
     * Determine the directory for the data store, and create it if necessary.
     */
    public FileSystemDataStore(final FileSystemDataStoreConfiguration config)
            throws DataStoreException
    {
        dataStorePath = FileSystems.getDefault().getPath(config.getDataDir());
        if ( !Files.exists(dataStorePath) ) {
            try {
                Files.createDirectory(dataStorePath);
            } catch (IOException e) {
                throw new DataStoreException("Cannot create data store directory", e);
            }
        }
        LOG.debug("Initialised");
    }


    @Override
    public void shutdown()
    {
        // nothing to do
    }


    @Override
    public void delete(String reference) throws DataStoreException {
        // no-op
        // Not throwing not supported exception to avoid breaking things. FS store will be retired.
    }

    /**
     * {@inheritDoc}
     * Read a file from disk in the data directory.
     * @throws ReferenceNotFoundException if the requested reference does not exist in the file system
     * @throws DataStoreException if the reference is found but cannot be accessed or retrieved
     * @throws InvalidPathException if the reference cannot be converted to a Path
     */
    @Override
    public InputStream retrieve(final String reference)
            throws DataStoreException
    {
        Objects.requireNonNull(reference);
        try {
            numRx.incrementAndGet();
            LOG.debug("Requesting {}", reference);
            return Files.newInputStream(checkReferenceExists(verifyReference(reference)));
        } catch (IOException e) {
            errors.incrementAndGet();
            throw new DataStoreException("Failed to retrieve data", e);
        }
    }


    /**
     * @throws ReferenceNotFoundException if the requested reference does not exist in the file system
     * @throws DataStoreException if the reference is found but cannot be accessed or retrieved
     * @throws InvalidPathException if the reference cannot be converted to a Path
     */
    @Override
    public long size(final String reference)
        throws DataStoreException
    {
        Objects.requireNonNull(reference);
        try {
            return Files.size(checkReferenceExists(verifyReference(reference)));
        } catch (IOException e) {
            errors.incrementAndGet();
            throw new DataStoreException("Failed to get data size", e);
        }
    }


    @Override
    public DataStoreMetricsReporter getMetrics()
    {
        return metrics;
    }


    /**
     * @throws DataStoreException if the reference is found but cannot be accessed or retrieved
     * @throws InvalidPathException if the reference cannot be converted to a Path
     */
    @Override
    public String store(final InputStream dataStream, final String partialReference)
        throws DataStoreException
    {
        try {
            Path ref = getStoreReference(partialReference);
            Files.copy(dataStream, ref);
            return dataStorePath.relativize(ref).toString();
        } catch (IOException e) {
            errors.incrementAndGet();
            throw new DataStoreException("Failed to get output stream for store", e);
        }
    }


    /**
     * @throws DataStoreException if the reference is found but cannot be accessed or retrieved
     * @throws InvalidPathException if the reference cannot be converted to a Path
     */
    @Override
    public String store(byte[] data, String partialReference)
        throws DataStoreException
    {
        try {
            Path ref = getStoreReference(partialReference);
            Files.write(ref, data);
            return dataStorePath.relativize(ref).toString();
        } catch (IOException e) {
            errors.incrementAndGet();
            throw new DataStoreException("Failed to get output stream for store", e);
        }
    }


    /**
     * @throws DataStoreException if the reference is found but cannot be accessed or retrieved
     * @throws InvalidPathException if the reference cannot be converted to a Path
     */
    @Override
    public String store(Path dataPath, String partialReference)
        throws DataStoreException
    {
        try {
            Path ref = getStoreReference(partialReference);
            Files.copy(dataPath, ref);
            return dataStorePath.relativize(ref).toString();
        } catch (IOException e) {
            errors.incrementAndGet();
            throw new DataStoreException("Failed to get output stream for store", e);
        }
    }


    @Override
    public HealthResult healthCheck()
    {
        return HealthResult.RESULT_HEALTHY;
    }


    /**
     * The returned Path will be the partial reference resolved relative to the
     * store's dataStorePath (from its configuration) and a randomly generated UUID file
     * name will be made relative to that. Subdirectories under the dataStorePath will
     * automatically be created.
     * @param partialReference the partial reference, typically subdirectories, may be null
     * @return the absolute Path to the location to store data
     * @throws DataStoreException if the partial reference cannot be verified
     * @throws IOException if the subdirectories cannot be created
     */
    private Path getStoreReference(final String partialReference)
        throws DataStoreException, IOException
    {
        Path p;
        if ( partialReference != null && !partialReference.isEmpty() ) {
            p = verifyReference(partialReference);
            if ( !Files.exists(p) ) {
                Files.createDirectories(p);
            }
        } else {
            p = dataStorePath;
        }
        return p.resolve(UUID.randomUUID().toString());
    }


    /**
     * Prevent a caller trying to "break out" of the root dataStorePath by performing
     * a full path resolution of the reference.
     * @param reference the data store reference relative to dataStorePath to resolve
     * @return the resolved path, if valid
     * @throws DataStoreException if the reference is invalid or cannot be resolved
     * @throws InvalidPathException if the reference cannot be converted to a Path
     */
    private Path verifyReference(final String reference)
        throws DataStoreException
    {
        Path p = dataStorePath.resolve(reference).normalize();
        if ( !p.startsWith(dataStorePath) ) {
            throw new DataStoreException("Invalid reference");
        }
        return p;
    }


    /**
     * Take a Path, verify it exists. If it does, return it, else throw ReferenceNotFoundException.
     * @param reference the reference Path to check
     * @return the Path, if it exists
     * @throws ReferenceNotFoundException if the Path does not exist
     */
    private Path checkReferenceExists(final Path reference)
        throws ReferenceNotFoundException
    {
        if ( !Files.exists(reference) ) {
            throw new ReferenceNotFoundException("Reference not found: "+ reference);
        }
        return reference;
    }


    private class FileSystemDataStoreMetricsReporter implements DataStoreMetricsReporter
    {
        @Override
        public int getDeleteRequests() {
            // delete is not implemented for FS datastore but it'll be no-op instead of
            // exception to avoid breaking things. FS datastore will be retired.
            return 0;
        }

        @Override
        public int getStoreRequests()
        {
            return numTx.get();
        }


        @Override
        public int getRetrieveRequests()
        {
            return numRx.get();
        }


        @Override
        public int getErrors()
        {
            return errors.get();
        }
    }
}