// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.cosmos.benchmark.linkedin;

import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.CosmosAsyncContainer;
import com.azure.cosmos.CosmosAsyncDatabase;
import com.azure.cosmos.benchmark.BenchmarkHelper;
import com.azure.cosmos.benchmark.TenantWorkloadConfig;
import com.azure.cosmos.benchmark.linkedin.data.EntityConfiguration;
import com.azure.cosmos.benchmark.linkedin.data.Key;
import com.azure.cosmos.benchmark.linkedin.data.KeyGenerator;
import com.azure.cosmos.benchmark.linkedin.impl.Accessor;
import com.azure.cosmos.benchmark.linkedin.impl.CosmosDBDataAccessor;
import com.azure.cosmos.benchmark.linkedin.impl.DocumentTransformer;
import com.azure.cosmos.benchmark.linkedin.impl.IdentityDocumentTransformer;
import com.azure.cosmos.benchmark.linkedin.impl.OperationsLogger;
import com.azure.cosmos.benchmark.linkedin.impl.ResponseHandler;
import com.azure.cosmos.benchmark.linkedin.impl.datalocator.StaticDataLocator;
import com.azure.cosmos.benchmark.linkedin.impl.exceptions.AccessorException;
import com.azure.cosmos.benchmark.linkedin.impl.keyextractor.KeyExtractor;
import com.azure.cosmos.benchmark.linkedin.impl.keyextractor.KeyExtractorImpl;
import com.azure.cosmos.benchmark.linkedin.impl.models.CollectionKey;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Preconditions;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Abstract class defining the test's framework template. The CosmosDB operation we are testing, such as
 * GET, QUERY, UPDATE are implemented as
 */
public abstract class TestRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestRunner.class);
    private static final Duration TERMINATION_WAIT_DURATION = Duration.ofSeconds(60);

    protected final TenantWorkloadConfig _workloadConfig;
    protected final EntityConfiguration _entityConfiguration;
    protected final Accessor<Key, JsonNode> _accessor;
    protected final ExecutorService _executorService;
    protected final AtomicLong _successCount;
    protected final AtomicLong _errorCount;
    private final Semaphore _semaphore;

    TestRunner(final TenantWorkloadConfig workloadConfig,
        final CosmosAsyncClient client,
        final EntityConfiguration entityConfiguration) {
        Preconditions.checkNotNull(workloadConfig,
            "The Workload configuration defining the parameters can not be null");
        Preconditions.checkNotNull(client,
            "Need a non-null client for setting up the Database and containers for the test");
        Preconditions.checkNotNull(entityConfiguration,
            "The Test entity configuration can not be null");

        _workloadConfig = workloadConfig;
        _entityConfiguration = entityConfiguration;
        _accessor = createAccessor(workloadConfig, client);
        // Executor and semaphore are sized lazily in run() based on orchestrator concurrency.
        // Use ingestionConcurrency as a default for init.
        _executorService = null; // created in run()
        _successCount = new AtomicLong(0);
        _errorCount = new AtomicLong(0);
        _semaphore = null; // created in run()
    }

    public void init() {
        LOGGER.info("Initializing the TestRunner");
        _accessor.initialize();
    }

    /**
     * Run the test with orchestrator-provided dispatch parameters.
     *
     * @param concurrency max concurrent operations
     * @param numberOfOperations total operations (ignored when maxRunningTime is set)
     * @param maxRunningTime wall-clock time limit (null = use numberOfOperations)
     */
    public void run(int concurrency, long numberOfOperations, Duration maxRunningTime) {
        LOGGER.info("Executing Tests for the configured Scenario (concurrency={}, ops={}, maxTime={})",
            concurrency, numberOfOperations, maxRunningTime);

        ExecutorService executorService = Executors.newFixedThreadPool(concurrency);
        Semaphore semaphore = new Semaphore(concurrency);

        try {
            KeyGenerator keyGenerator = getNewKeyGenerator();
            final long runStartTime = System.currentTimeMillis();
            long i = 0;
            for (; BenchmarkHelper.shouldContinue(runStartTime, i, maxRunningTime, (int) numberOfOperations); i++) {
                if (i > _workloadConfig.getNumberOfPreCreatedDocuments()) {
                    keyGenerator = getNewKeyGenerator();
                }
                final Key documentKey = keyGenerator.key();
                try {
                    semaphore.acquire();
                } catch (InterruptedException e) {
                    _errorCount.incrementAndGet();
                    continue;
                }
                executorService.submit(() -> {
                    try {
                        runOperation(documentKey);
                    } finally {
                        semaphore.release();
                    }
                });
            }

            // Log the completion details
            final Instant runEndTime = Instant.now();
            LOGGER.info("Number of iterations: {}, Errors: {}, Runtime: {} millis",
                _successCount.get(),
                _errorCount.get(),
                runEndTime.minusMillis(runStartTime).toEpochMilli());
        } finally {
            LOGGER.info("Waiting " + TERMINATION_WAIT_DURATION + " before shutting down the ExecutorService");
            try {
                executorService.awaitTermination(TERMINATION_WAIT_DURATION.getSeconds(), TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                LOGGER.error("Error awaiting the completion of all tasks", e);
            }
            executorService.shutdown();
        }
    }

    public void cleanup() {
        // Executor cleanup is handled within run() now.
        LOGGER.info("TestRunner cleanup complete.");
    }

    /**
     * Control which test scenario is executed e.g. GET, QUERY.
     *
     * @param key The Document's id/partitionKey we are operating on
     * @throws AccessorException on errors in executing the test operation
     */
    protected abstract void testOperation(final Key key) throws AccessorException;

    private void runOperation(final Key key) {
        try {
            testOperation(key);
            _successCount.getAndIncrement();
        } catch (AccessorException e) {
            LOGGER.error("Received running exception", e);
            _errorCount.getAndIncrement();
        } finally {
            _semaphore.release();
        }
    }

    private Accessor<Key, JsonNode> createAccessor(final TenantWorkloadConfig workloadConfig,
        final CosmosAsyncClient client) {

        final StaticDataLocator dataLocator = createDataLocator(workloadConfig, client);
        final KeyExtractor<Key> keyExtractor = new KeyExtractorImpl();
        final DocumentTransformer<JsonNode, JsonNode> documentTransformer = new IdentityDocumentTransformer<>();
        final Clock clock = Clock.systemUTC();
        return new CosmosDBDataAccessor<>(dataLocator,
            keyExtractor,
            new ResponseHandler<>(documentTransformer, keyExtractor),
            clock,
            new OperationsLogger(Duration.ofSeconds(10)));
    }

    private StaticDataLocator createDataLocator(TenantWorkloadConfig workloadConfig, CosmosAsyncClient client) {
        final CollectionKey collectionKey = new CollectionKey(workloadConfig.getServiceEndpoint(),
            workloadConfig.getDatabaseId(),
            workloadConfig.getContainerId());
        final CosmosAsyncDatabase database = client.getDatabase(workloadConfig.getDatabaseId());
        final CosmosAsyncContainer container = database.getContainer(workloadConfig.getContainerId());
        return new StaticDataLocator(collectionKey, container);
    }

    private KeyGenerator getNewKeyGenerator() {
        return _entityConfiguration.keyGenerator();
    }
}
