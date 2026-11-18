package com.yuanyue.reader.data

data class AiProvider(
    val id: String,
    val label: String,
    val baseUrl: String,
    val models: List<String>,
)

val AiProviders = listOf(
    AiProvider(
        id = "openai",
        label = "OpenAI",
        baseUrl = "https://api.openai.com/v1",
        models = listOf("gpt-4o-mini", "gpt-4o"),
    ),
    AiProvider(
        id = "deepseek",
        label = "DeepSeek",
        baseUrl = "https://api.deepseek.com/v1",
        models = listOf("deepseek-chat", "deepseek-reasoner"),
    ),
    AiProvider(
        id = "moonshot",
        label = "Moonshot",
        baseUrl = "https://api.moonshot.cn/v1",
        models = listOf("moonshot-v1-8k", "moonshot-v1-32k"),
    ),
    AiProvider(
        id = "zhipu",
        label = "智谱",
        baseUrl = "https://open.bigmodel.cn/api/paas/v4",
        models = listOf("glm-4-flash", "glm-4-plus"),
    ),
    AiProvider(
        id = "siliconflow",
        label = "硅基流动",
        baseUrl = "https://api.siliconflow.cn/v1",
        models = listOf("deepseek-ai/DeepSeek-V3", "Qwen/Qwen2.5-72B-Instruct"),
    ),
    AiProvider(
        id = "custom",
        label = "自定义",
        baseUrl = "",
        models = emptyList(),
    ),
)

fun aiProviderForBaseUrl(baseUrl: String): AiProvider? {
    val normalized = baseUrl.trim().trimEnd('/')
    return AiProviders.firstOrNull { it.id != "custom" && it.baseUrl.trimEnd('/') == normalized }
}

fun aiProviderById(id: String): AiProvider? = AiProviders.firstOrNull { it.id == id }
