package com.example.oslaucher.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppGridOrderTest {
    @Test
    fun orderItemsKeepsSavedSlotsAndAppendsNewItems() {
        val result = AppGridOrder.orderItems(
            items = listOf("mail", "camera", "music"),
            orderedKeys = listOf("music", null, "removed", "music"),
            keyOf = { it }
        )

        assertEquals(listOf("music", null, null, null, "mail", "camera"), result)
    }

    @Test
    fun moveAppPlacesItemInEmptySlot() {
        val result = AppGridOrder.moveApp(
            packages = listOf("mail", "camera", null, null),
            packageName = "mail",
            pageIndex = 0,
            pagePosition = 2,
            pageSize = 4,
            dropAfterTarget = false
        )

        assertEquals(listOf(null, "camera", "mail", null), result)
    }

    @Test
    fun moveAppInsertsRelativeToOccupiedTarget() {
        val result = AppGridOrder.moveApp(
            packages = listOf("mail", "camera", "music", null),
            packageName = "mail",
            pageIndex = 0,
            pagePosition = 2,
            pageSize = 4,
            dropAfterTarget = false
        )

        assertEquals(listOf(null, "camera", "mail", "music", null), result)
    }

    @Test
    fun moveAppReturnsNullWhenSourceDoesNotMove() {
        val result = AppGridOrder.moveApp(
            packages = listOf("mail", "camera", null, null),
            packageName = "mail",
            pageIndex = 0,
            pagePosition = 0,
            pageSize = 4,
            dropAfterTarget = false
        )

        assertNull(result)
    }
}
