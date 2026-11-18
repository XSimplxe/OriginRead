package com.yuanyue.reader.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsCatalogTest {
    @Test
    fun `parses engines and voice labels`() {
        val catalog = TtsCatalog.parseOrThrow(
            """
            {
              "engines": [
                {
                  "package": "com.google.android.tts",
                  "name": "Google 语音服务",
                  "voices": [
                    {"name": "cmn-cn-x-ccc-local", "label": "中文女声 · 离线", "locale": "zh-CN"},
                    {"name": "cmn-cn-x-ccd-local", "label": "中文男声 · 离线"}
                  ]
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(1, catalog.engines.size)
        assertEquals("Google 语音服务", catalog.engineLabel("com.google.android.tts"))
        assertEquals(
            "中文女声 · 离线",
            catalog.voiceLabel("com.google.android.tts", "cmn-cn-x-ccc-local"),
        )
        assertNull(catalog.voiceLabel("com.google.android.tts", "missing"))
    }

    @Test
    fun `accepts a top level engine array`() {
        val catalog = TtsCatalog.parseOrThrow(
            """
            [
              {"package": "com.example.tts", "label": "示例引擎",
               "voices": [{"name": "voice-a", "displayName": "示例音色"}]}
            ]
            """.trimIndent(),
        )

        assertEquals("示例引擎", catalog.engineLabel("com.example.tts"))
        assertEquals("示例音色", catalog.voiceLabel("com.example.tts", "voice-a"))
    }

    @Test
    fun `invalid json returns empty catalog for display`() {
        val catalog = TtsCatalog.parse("not json")
        assertTrue(catalog.engines.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `empty engine list is rejected on import`() {
        TtsCatalog.parseOrThrow("""{"engines":[]}""")
    }
}
