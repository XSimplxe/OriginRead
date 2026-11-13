package com.yuanyue.reader.data

object BookTags {
    fun parse(raw: String?): List<String> =
        raw.orEmpty()
            .split(',', '，', ';', '；')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    fun normalize(raw: String?): String = parse(raw).joinToString(", ")

    fun toggle(raw: String?, tag: String): String {
        val tags = parse(raw).toMutableList()
        if (!tags.remove(tag)) tags += tag
        return tags.joinToString(", ")
    }
}
