/*
 * Copyright (C) 2010-2023 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.schema.validator.processor;

import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.schema.validator.UpgradeObjectProcessor;
import com.evolveum.midpoint.schema.validator.UpgradePhase;
import com.evolveum.midpoint.schema.validator.UpgradePriority;
import com.evolveum.midpoint.schema.validator.UpgradeType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ActivityDefinitionType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ActivityExecutionModeDefinitionType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ExecutionModeType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.TaskType;

@SuppressWarnings("unused")
public class ExecutionModeProcessor implements UpgradeObjectProcessor<TaskType> {

    @Override
    public UpgradePhase getPhase() {
        return UpgradePhase.BEFORE;
    }

    @Override
    public UpgradePriority getPriority() {
        return UpgradePriority.NECESSARY;
    }

    @Override
    public UpgradeType getType() {
        return UpgradeType.SEAMLESS;
    }

    @Override
    public boolean isApplicable(PrismObject<?> object, ItemPath path) {
        return matchParentTypeAndItemName(object, path, ActivityDefinitionType.class, ActivityDefinitionType.F_EXECUTION_MODE);
    }

    @Override
    public boolean process(PrismObject<TaskType> object, ItemPath path) throws Exception {
        ActivityDefinitionType activity = object.asObjectable().getActivity();
        ExecutionModeType mode = activity.getExecutionMode();

        ActivityExecutionModeDefinitionType execution = activity.getExecution();
        if (execution == null) {
            execution = new ActivityExecutionModeDefinitionType();
            activity.setExecution(execution);
        }

        execution.setMode(mode);
        activity.setExecutionMode(null);

        return true;
    }
}
