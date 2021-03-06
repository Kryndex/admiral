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

package com.vmware.admiral.host;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.request.ContainerControlLoopService;
import com.vmware.admiral.service.common.AbstractInitialBootService;
import com.vmware.xenon.common.Operation;

/**
 * Initial boot service for creating system default documents for the common module.
 */
public class RequestInitialBootService extends AbstractInitialBootService {
    public static final String SELF_LINK = ManagementUriParts.CONFIG + "/request-initial-boot";

    @Override
    public void handlePost(Operation post) {
        initInstances(post, ContainerControlLoopService.buildDefaultStateInstance());
    }

}