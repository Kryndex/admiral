/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.request.allocation.filter;

import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import com.vmware.admiral.compute.ElasticPlacementZoneConfigurationService;
import com.vmware.admiral.compute.ElasticPlacementZoneConfigurationService.ElasticPlacementZoneConfigurationState;
import com.vmware.admiral.compute.ElasticPlacementZoneService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.request.PlacementHostSelectionTaskService;
import com.vmware.admiral.request.PlacementHostSelectionTaskService.PlacementHostSelectionTaskState;
import com.vmware.admiral.request.ReservationTaskFactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
*
*  A filter implementing {@link HostSelectionFilter} aimed to provide host selection based on Placement - BINPACK.
*
*  Algorithm will filter out the docker hosts leaving only the most loaded in terms of memory one.
*   1) Let h ∈ { h(1)....h(n-1), h(n) }
*   2) P(h) = min{h(1)...h(n)}
*   3) ∃! h: P(h)
*
*   Constraint (1) means that most suitable host belongs to set of hosts which have been filtered from other affinity filters.
*   Constraint (2) means that hosts will be sorted by available memory in ascending order. Host with smallest available memory will be returned.
*   Constraint (3) means there is exactly one host such that P(h)  is true.
*
*/
public class BinpackAffinityHostFilter
        implements
        HostSelectionFilter<PlacementHostSelectionTaskService.PlacementHostSelectionTaskState> {

    private static final Long MINIMAL_AVAILABLE_MEMORY_IN_BYTES = 3000000000L; // 3 GB

    private final ServiceHost host;

    private Map<String, Long> dockerHostToMemory = new ConcurrentHashMap<>();

    public BinpackAffinityHostFilter(ServiceHost host, ContainerDescription desc) {
        this.host = host;
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public Map<String, AffinityConstraint> getAffinityConstraints() {
        return Collections.emptyMap();
    }

    @Override
    public void filter(PlacementHostSelectionTaskState state,
            Map<String, HostSelection> hostSelectionMap,
            HostSelectionFilterCompletion callback) {

        // Nothing to filter here.
        if (hostSelectionMap.size() <= 1) {
            host.log(Level.INFO, "Only one host in selection. BinPack filtering will be skipped.");
            callback.complete(hostSelectionMap, null);
            return;
        }

        String serviceLink = state.serviceTaskCallback != null
                ? state.serviceTaskCallback.serviceSelfLink
                : null;
        // Filter should be ignored on Reservation stage.
        if (serviceLink != null
                && serviceLink.startsWith(ReservationTaskFactoryService.SELF_LINK)) {
            callback.complete(hostSelectionMap, null);
            return;
        }
        String resourcePoolLink = state.resourcePoolLinks.get(0);

        filterBasedOnBinpackPolicy(resourcePoolLink, hostSelectionMap, callback);

    }

    private void filterBasedOnBinpackPolicy(String resourcePoolLink,
            Map<String, HostSelection> hostSelectionMap, HostSelectionFilterCompletion callback) {

        URI uri = UriUtils.buildUri(host, String.format("%s/%s",
                ElasticPlacementZoneConfigurationService.SELF_LINK, resourcePoolLink));

        host.sendRequest(Operation.createGet(uri)
                .setReferer(host.getUri())
                .setCompletion((o, ex) -> {

                    if (ex != null) {
                        host.log(Level.WARNING, Utils.toString(ex));
                        callback.complete(hostSelectionMap, null);
                        return;
                    }

                    ElasticPlacementZoneConfigurationState epz = o
                            .getBody(ElasticPlacementZoneConfigurationState.class);
                    if (epz != null && epz.epzState != null
                            && epz.epzState.placementPolicy == ElasticPlacementZoneService.PlacementPolicy.BINPACK) {

                        hostSelectionMap.forEach((host, hostSelection) -> {
                            dockerHostToMemory.put(host, hostSelection.availableMemory);
                        });

                        returnMaxLoadedHost(hostSelectionMap, callback);

                    } else {
                        callback.complete(hostSelectionMap, null);
                    }

                }));
    }

    // Get max loaded in terms of memory host.
    private void returnMaxLoadedHost(Map<String, HostSelection> hostSelectionMap,
            HostSelectionFilterCompletion callback) {

        // This should never happen.
        if (dockerHostToMemory.isEmpty()) {
            callback.complete(hostSelectionMap, null);
            return;
        }

        Map<String, HostSelection> result = new LinkedHashMap<>();
        Map<String, Long> sortedMap = new LinkedHashMap<>();

        // Sort map ascending based on available memory.
        dockerHostToMemory.entrySet().stream()
                .sorted(Map.Entry.<String, Long> comparingByValue())
                .forEachOrdered(x -> sortedMap.put(x.getKey(), x.getValue()));

        // Traverse trough sorted hosts to memory map and find first max loaded which has at least 3
        // GB memory available.
        Optional<Entry<String, Long>> hostToMemoryEntry = sortedMap.entrySet().stream()
                .filter(obj -> obj.getValue() > MINIMAL_AVAILABLE_MEMORY_IN_BYTES).findFirst();

        if (!hostToMemoryEntry.isPresent()) {
            callback.complete(null, new Throwable("All hosts are overloaded."));
            return;
        }

        String mostLoadedHost = hostToMemoryEntry.get().getKey();

        result.put(mostLoadedHost, hostSelectionMap.get(mostLoadedHost));
        callback.complete(result, null);
    }
}
