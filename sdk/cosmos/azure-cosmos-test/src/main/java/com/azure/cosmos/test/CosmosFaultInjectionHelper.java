// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.cosmos.test;

import com.azure.cosmos.BridgeInternal;
import com.azure.cosmos.CosmosAsyncContainer;
import com.azure.cosmos.implementation.AsyncDocumentClient;
import com.azure.cosmos.implementation.ImplementationBridgeHelpers;
import com.azure.cosmos.implementation.Utils;
import com.azure.cosmos.test.implementation.FaultInjectionRulesProcessor;
import com.azure.cosmos.test.models.FaultInjectionRule;
import com.azure.cosmos.test.util.Beta;
import com.azure.cosmos.test.util.Warning;
import reactor.core.publisher.Mono;

import java.util.List;

import static com.azure.cosmos.test.util.Warning.FAULT_INJECTION_TEST_USE_ONLY_WARNING;


/***
 * Helper class to configure fault injection rules on container.
 * Please only use this for testing, no use in prod environment.
 */
@Warning(value = FAULT_INJECTION_TEST_USE_ONLY_WARNING)
@Beta(value = Beta.SinceVersion.V4_0_0, warningText = Beta.PREVIEW_SUBJECT_TO_CHANGE_WARNING)
public final class CosmosFaultInjectionHelper {

    /***
     * Configure fault injection rules.
     *
     * @param cosmosAsyncContainer the {@link CosmosAsyncContainer}.
     * @param rules the list of {@link FaultInjectionRule} to be configured.
     * @return the mono.
     */
    @Warning(value = FAULT_INJECTION_TEST_USE_ONLY_WARNING)
    @Beta(value = Beta.SinceVersion.V4_0_0, warningText = Beta.PREVIEW_SUBJECT_TO_CHANGE_WARNING)
    public static Mono<Void> configureFaultInjectionRules(CosmosAsyncContainer cosmosAsyncContainer, List<FaultInjectionRule> rules) {
        AsyncDocumentClient client = BridgeInternal.getContextClient(
            ImplementationBridgeHelpers
                .CosmosAsyncDatabaseHelper
                .getCosmosAsyncDatabaseAccessor()
                .getCosmosAsyncClient(cosmosAsyncContainer.getDatabase())
        );

        FaultInjectionRulesProcessor rulesProcessor = new FaultInjectionRulesProcessor(
            client.getConnectionPolicy().getConnectionMode(),
            client.getStoreModel(),
            client.getGatewayProxy(),
            client.getCollectionCache(),
            client.getGlobalEndpointManager(),
            client.getPartitionKeyRangeCache(),
            client.getAddressSelector(),
            client.getConnectionPolicy().getThrottlingRetryOptions());

        String containerNameLink =  Utils.trimBeginningAndEndingSlashes(BridgeInternal.extractContainerSelfLink(cosmosAsyncContainer));
        return rulesProcessor.processFaultInjectionRules(rules, containerNameLink)
            .doOnSuccess(effectiveRules ->
                ImplementationBridgeHelpers
                    .CosmosAsyncContainerHelper
                    .getCosmosAsyncContainerAccessor()
                    .configureFaultInjectionRules(cosmosAsyncContainer, effectiveRules))
            .then();
    }
}
