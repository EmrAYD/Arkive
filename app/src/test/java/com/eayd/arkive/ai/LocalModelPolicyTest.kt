package com.eayd.arkive.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Telefondaki modelin kuralları. */
class LocalModelPolicyTest {

    private val gb = 1_000_000_000L

    @Test
    fun `6 GB ve ustu 64 bit telefonlar desteklenir`() {
        assertTrue(LocalModelPolicy.isDeviceSupported((5.6 * gb).toLong(), listOf("arm64-v8a", "armeabi-v7a")))
        assertTrue(LocalModelPolicy.isDeviceSupported(12 * gb, listOf("x86_64")))
        assertFalse(LocalModelPolicy.isDeviceSupported((3.6 * gb).toLong(), listOf("arm64-v8a")))
        assertFalse(LocalModelPolicy.isDeviceSupported(8 * gb, listOf("armeabi-v7a")))
    }

    @Test
    fun `indirme icin model kadar yer ve pay gerekir`() {
        val model = 2_588_147_712L
        assertTrue(LocalModelPolicy.hasRoom(model + LocalModelPolicy.SPACE_MARGIN_BYTES, model))
        assertFalse(LocalModelPolicy.hasRoom(model + 1, model))
    }

    @Test
    fun `yalnizca tam boyuttaki dosya hazir sayilir`() {
        assertTrue(LocalModelPolicy.isComplete(2_588_147_712L, 2_588_147_712L))
        assertFalse(LocalModelPolicy.isComplete(2_588_147_711L, 2_588_147_712L))
    }

    @Test
    fun `indirme yuzdesi`() {
        assertEquals(50, LocalModelState.Downloading(50, 100).percent)
        assertEquals(100, LocalModelState.Downloading(150, 100).percent)
        assertNull(LocalModelState.Downloading(10, 0).percent)
    }
}
