package com.github.sam0delkin.intellijpsa.util

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class UndoUtilsTest : BasePlatformTestCase() {
    fun testExecuteWithUndoRunsOperationAndRegistersUndoableAction() {
        var operationRuns = 0
        var undoRuns = 0

        UndoUtils.executeWithUndo(
            project,
            operation = { operationRuns++ },
            undoOperation = { undoRuns++ },
            commandName = "Test Command",
        )

        assertEquals(1, operationRuns)
        assertEquals(0, undoRuns)
    }

    fun testExecuteWithUndoWithGroupId() {
        var operationRuns = 0

        UndoUtils.executeWithUndo(
            project,
            operation = { operationRuns++ },
            undoOperation = {},
            commandName = "Test Command",
            groupId = "test-group",
        )

        assertEquals(1, operationRuns)
    }
}
