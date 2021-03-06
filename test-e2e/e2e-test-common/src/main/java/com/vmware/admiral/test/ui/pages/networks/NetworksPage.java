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

package com.vmware.admiral.test.ui.pages.networks;

import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.actions;

import java.util.Objects;

import com.codeborne.selenide.SelenideElement;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.HomeTabAdvancedPage;
import com.vmware.admiral.test.ui.pages.common.PageProxy;

public class NetworksPage extends HomeTabAdvancedPage<NetworksPage, NetworksPageValidator> {

    private NetworksPageValidator validator;
    private CreateNetworkPage createNetworkPage;

    public CreateNetworkPage createNetwork() {
        LOG.info("Creating network");
        if (Objects.isNull(createNetworkPage)) {
            createNetworkPage = new CreateNetworkPage(new PageProxy(this));
        }
        executeInFrame(0, () -> $(CREATE_RESOURCE_BUTTON).click());
        createNetworkPage.waitToLoad();
        return createNetworkPage;
    }

    public NetworksPage deleteNetwork(String namePrefix) {
        LOG.info(String.format("Deleting network with name prefix: [%s]", namePrefix));
        executeInFrame(0, () -> {
            SelenideElement card = waitForElementToStopMoving(getNetworkCardSelector(namePrefix));
            actions().moveToElement(card)
                    .moveToElement(card.$(CARD_RELATIVE_DELETE_BUTTON))
                    .click()
                    .build()
                    .perform();
            card.$(CARD_RELATIVE_DELETE_CONFIRMATION_BUTTON).click();
        });
        return this;
    }

    By getNetworkCardSelector(String namePrefix) {
        return By.xpath(String.format(CARD_SELECTOR_BY_NAME_PREFIX_XPATH, namePrefix));
    }

    @Override
    public NetworksPageValidator validate() {
        if (Objects.isNull(validator)) {
            validator = new NetworksPageValidator(this);
        }
        return validator;
    }

    @Override
    public void waitToLoad() {
        validate().validateIsCurrentPage();
        executeInFrame(0, () -> waitForSpinner());
    }

    @Override
    public NetworksPage getThis() {
        return this;
    }

}
