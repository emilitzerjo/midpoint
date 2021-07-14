/*
 * Copyright (c) 2010-2018 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

package com.evolveum.midpoint.model.intest.sync;

import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.task.ActivityStateUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.test.util.TestUtil;
import com.evolveum.midpoint.util.exception.*;

import javax.xml.datatype.XMLGregorianCalendar;

/**
 * TODO
 */
public class TestValidityRecomputeTaskPartitioned extends TestValidityRecomputeTask {

    @Override
    protected String getValidityScannerTaskFileName() {
        return TASK_PARTITIONED_VALIDITY_SCANNER_FILENAME;
    }

    @Override
    protected void waitForValidityTaskFinish() throws Exception {
        waitForTaskTreeNextFinishedRun(TASK_VALIDITY_SCANNER_OID, DEFAULT_TASK_WAIT_TIMEOUT);
    }

    @Override
    protected void waitForValidityNextRunAssertSuccess() throws Exception {
        OperationResult result = waitForTaskTreeNextFinishedRun(TASK_VALIDITY_SCANNER_OID, DEFAULT_TASK_WAIT_TIMEOUT);
        TestUtil.assertSuccess(result);
    }

    @Override
    protected void assertLastScanTimestamp(XMLGregorianCalendar startCal, XMLGregorianCalendar endCal)
            throws ObjectNotFoundException, SchemaException {
        OperationResult result = getTestOperationResult();
        Task master = taskManager.getTaskPlain(TASK_VALIDITY_SCANNER_OID, result);
        for (Task subtask : master.listSubtasks(result)) {
            assertTask(subtask.getOid(), "")
                    .assertLastScanTimestamp(
                            ActivityStateUtil.getLocalRootPath(subtask.getActivitiesStateOrClone()),
                            startCal, endCal);
        }
    }
}
