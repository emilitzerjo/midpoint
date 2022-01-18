/*
 * Copyright (c) 2021 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.web.component.search.refactored;

import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.util.DisplayableValue;
import com.evolveum.midpoint.web.component.search.SearchValue;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ScopeSearchItemConfigurationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.SearchBoxModeType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.SearchBoxScopeType;

import javax.xml.namespace.QName;

public class ScopeSearchItemWrapper extends AbstractRoleSearchItemWrapper {

    public ScopeSearchItemWrapper(SearchConfigurationWrapper searchConfig) {
        super(searchConfig);
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    public boolean isVisible() {
        return true;
    }

    @Override
    public Class<ScopeSearchItemPanel> getSearchItemPanelClass() {
        return ScopeSearchItemPanel.class;
    }

    @Override
    public DisplayableValue<SearchBoxScopeType> getDefaultValue() {
        return new SearchValue<>(SearchBoxScopeType.ONE_LEVEL);
    }

    @Override
    public String getName() {
        if (getSearchConfig().getConfig().getScopeConfiguration() == null
                || getSearchConfig().getConfig().getScopeConfiguration().getDisplay() == null) {
            return "";
        }
        return WebComponentUtil.getTranslatedPolyString(getSearchConfig().getConfig().getScopeConfiguration().getDisplay().getLabel());
    }

    @Override
    public String getHelp() {
        if (getSearchConfig().getConfig().getScopeConfiguration() == null
                || getSearchConfig().getConfig().getScopeConfiguration().getDisplay() == null) {
            return "";
        }
        return WebComponentUtil.getTranslatedPolyString(getSearchConfig().getConfig().getScopeConfiguration().getDisplay().getHelp());
    }

    @Override
    public String getTitle() {
        return ""; //todo
    }

    @Override
    public boolean isApplyFilter(SearchBoxModeType searchBoxMode) {
        ScopeSearchItemConfigurationType config = getSearchConfig().getConfig().getScopeConfiguration();
        return  config != null && config.getDefaultValue() == SearchBoxScopeType.SUBTREE;
    }

}
