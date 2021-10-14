// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.cosmos.benchmark;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.CosmosAsyncContainer;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.benchmark.ctl.AsyncCtlWorkload;
import com.azure.cosmos.models.PartitionKey;
import com.beust.jcommander.ParameterException;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class Main {

    private final static Logger LOGGER = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        try {
            System.out.println(SystemUtils.IS_OS_LINUX);
            System.out.println(SystemUtils.IS_OS_UNIX);
            System.out.println(SystemUtils.OS_NAME);
            System.out.println(SystemUtils.OS_VERSION);
            System.out.println(SystemUtils.OS_ARCH);
            CosmosAsyncClient client = new CosmosClientBuilder()
                .endpoint("")
                .key("dXjOXH6YGBqR5E0xT9DaPZbb3ahYtT6NRBvGdzvDNt9vvUSOVZE2pRzDWITd074hYe7Fc2dG7an7PD57jPag1A==")
                .consistencyLevel(ConsistencyLevel.STRONG)
                .buildAsyncClient();


            CosmosAsyncContainer container = client.getDatabase("testdb").getContainer("testContainer");

            try {
                container.readItem(UUID.randomUUID().toString(), new PartitionKey("mypk"), TestItem.class).block();
                //container.readItem("a1302ed6-7e8e-4571-8644-73594d0dda2e", new PartitionKey("mypk"), TestItem.class).block();
            }
            catch (Exception e) {
                System.out.println(e.getStackTrace());
            }
        } catch (ParameterException e) {
            // if any error in parsing the cmd-line options print out the usage help
            System.err.println("INVALID Usage: " + e.getMessage());
            System.err.println("Try '-help' for more information.");
            throw e;
        }
    }

    private static void validateConfiguration(Configuration cfg) {
        switch (cfg.getOperationType()) {
            case WriteLatency:
            case WriteThroughput:
                break;
            default:
                if (!Boolean.parseBoolean(cfg.isContentResponseOnWriteEnabled())) {
                    throw new IllegalArgumentException("contentResponseOnWriteEnabled parameter can only be set to false " +
                        "for write latency and write throughput operations");
                }
        }
    }

    private static void syncBenchmark(Configuration cfg) throws Exception {
        LOGGER.info("Sync benchmark ...");
        SyncBenchmark<?> benchmark = null;
        try {
            switch (cfg.getOperationType()) {
                case ReadThroughput:
                case ReadLatency:
                    benchmark = new SyncReadBenchmark(cfg);
                    break;

                default:
                    throw new RuntimeException(cfg.getOperationType() + " is not supported");
            }

            LOGGER.info("Starting {}", cfg.getOperationType());
            benchmark.run();
        } finally {
            if (benchmark != null) {
                benchmark.shutdown();
            }
        }
    }

    private static void asyncBenchmark(Configuration cfg) throws Exception {
        LOGGER.info("Async benchmark ...");
        AsyncBenchmark<?> benchmark = null;
        try {
            switch (cfg.getOperationType()) {
                case WriteThroughput:
                case WriteLatency:
                    benchmark = new AsyncWriteBenchmark(cfg);
                    break;

                case ReadThroughput:
                case ReadLatency:
                    benchmark = new AsyncReadBenchmark(cfg);
                    break;

                case QueryCross:
                case QuerySingle:
                case QueryParallel:
                case QueryOrderby:
                case QueryAggregate:
                case QueryTopOrderby:
                case QueryAggregateTopOrderby:
                case QueryInClauseParallel:
                    benchmark = new AsyncQueryBenchmark(cfg);
                    break;

                case Mixed:
                    benchmark = new AsyncMixedBenchmark(cfg);
                    break;

                case QuerySingleMany:
                    benchmark = new AsyncQuerySinglePartitionMultiple(cfg);
                    break;

                case ReadMyWrites:
                    benchmark = new ReadMyWriteWorkflow(cfg);
                    break;

                default:
                    throw new RuntimeException(cfg.getOperationType() + " is not supported");
            }

            LOGGER.info("Starting {}", cfg.getOperationType());
            benchmark.run();
        } finally {
            if (benchmark != null) {
                benchmark.shutdown();
            }
        }
    }

    private static void asyncMultiClientBenchmark(Configuration cfg) throws Exception {
        LOGGER.info("Async multi client benchmark ...");
        AsynReadWithMultipleClients<?> benchmark = null;
        try {
            benchmark = new AsynReadWithMultipleClients<>(cfg);
            LOGGER.info("Starting {}", cfg.getOperationType());
            benchmark.run();
        } finally {
            if (benchmark != null) {
                benchmark.shutdown();
            }
        }
    }

    private static void asyncCtlWorkload(Configuration cfg) throws Exception {
        LOGGER.info("Async ctl workload");
        AsyncCtlWorkload benchmark = null;
        try {
            benchmark = new AsyncCtlWorkload(cfg);
            LOGGER.info("Starting {}", cfg.getOperationType());
            benchmark.run();
        } finally {
            if (benchmark != null) {
                benchmark.shutdown();
            }
        }
    }
}
