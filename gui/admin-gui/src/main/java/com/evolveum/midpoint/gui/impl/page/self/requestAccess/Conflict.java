/*
 * Copyright (c) 2022 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.gui.impl.page.self.requestAccess;

import java.io.Serializable;

/**
 * Created by Viliam Repan (lazyman).
 */
public class Conflict implements Serializable {

    private ConflictItem added;

    private ConflictItem exclusion;

    private ConflictState state = ConflictState.UNRESOLVED;

    private boolean warning;

    public Conflict(ConflictItem added, ConflictItem exclusion, boolean warning) {
        this.added = added;
        this.exclusion = exclusion;
        this.warning = warning;
    }

    public ConflictItem getAdded() {
        return added;
    }

    public ConflictItem getExclusion() {
        return exclusion;
    }

    public ConflictState getState() {
        return state;
    }

    public void setState(ConflictState state) {
        this.state = state;
    }

    public boolean isWarning() {
        return warning;
    }
}
