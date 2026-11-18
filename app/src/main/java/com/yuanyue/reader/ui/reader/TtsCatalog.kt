package com.yuanyue.reader.ui.reader

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

data class TtsVoiceMeta(
    val name: String,
    val label: String? = null,
    val locale: String? = null,
)

data class TtsEngineMeta(
    val packageName: String,
    val label: String? = null,
    val voices: List<TtsVoiceMeta> = emptyList(),
)

data class TtsCatalog(
    val engines: List<TtsEngineMeta> = emptyList(),
) {
    fun engineLabel(packageName: String): String? =
        engines.firstOrNull { it.packageName == packageName }?.label

    fun voiceLabel(packageName: String?, voiceName: String): String? {
        if (packageName == null) return null
        return engines.firstOrNull { it.packageName == packageName }
            ?.voices
            ?.firstOrNull { it.name == voiceName }
            ?.label
    }

    fun engine(packageName: String): TtsEngineMeta? =
        engines.firstOrNull { it.packageName == packageName }

    companion object {
        val Empty = TtsCatalog()

        fun parse(json: String?): TtsCatalog {
            if (json.isNullOrBlank()) return Empty
            return runCatching { parseOrThrow(json) }.getOrDefault(Empty)
        }

        fun parseOrThrow(json: String): TtsCatalog {
            val root = runCatching { JSONTokener(json).nextValue() }
                .getOrElse { throw IllegalArgumentException("JSON 格式不正确") }
            val engineArray = when (root) {
                is JSONArray -> root
                is JSONObject -> firstArray(root, "engines", "ttsEngines", "tts_engines", "data")
                    ?: throw IllegalArgumentException("JSON 中缺少 engines 数组")
                else -> throw IllegalArgumentException("JSON 根节点必须是对象或数组")
            }
            val engines = mutableListOf<TtsEngineMeta>()
            for (index in 0 until engineArray.length()) {
                val item = engineArray.optJSONObject(index) ?: continue
                val packageName = firstString(item, "package", "packageName", "engine", "id")
                    ?: continue
                val label = firstString(item, "name", "label", "displayName", "title")
                val voicesArray = firstArray(item, "voices", "voiceList")
                val voices = mutableListOf<TtsVoiceMeta>()
                if (voicesArray != null) {
                    for (voiceIndex in 0 until voicesArray.length()) {
                        val voiceObject = voicesArray.optJSONObject(voiceIndex) ?: continue
                        val voiceName = firstString(voiceObject, "name", "id", "voiceName")
                            ?: continue
                        voices += TtsVoiceMeta(
                            name = voiceName,
                            label = firstString(voiceObject, "label", "displayName", "title"),
                            locale = firstString(voiceObject, "locale", "language", "lang"),
                        )
                    }
                }
                if (engines.none { it.packageName == packageName }) {
                    engines += TtsEngineMeta(
                        packageName = packageName,
                        label = label,
                        voices = voices.distinctBy { it.name },
                    )
                }
            }
            if (engines.isEmpty()) {
                throw IllegalArgumentException("JSON 中没有有效的 TTS 引擎配置")
            }
            return TtsCatalog(engines)
        }

        private fun firstArray(obj: JSONObject, vararg keys: String): JSONArray? {
            for (key in keys) {
                val value = obj.optJSONArray(key)
                if (value != null) return value
            }
            return null
        }

        private fun firstString(obj: JSONObject, vararg keys: String): String? {
            for (key in keys) {
                val value = obj.optString(key, "").trim()
                if (value.isNotBlank() && value != "null") return value
            }
            return null
        }
    }
}
