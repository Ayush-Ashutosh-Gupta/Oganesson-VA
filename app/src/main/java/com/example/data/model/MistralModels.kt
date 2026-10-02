package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MistralChatRequest(
    @Json(name = "model") val model: String,
    @Json(name = "messages") val messages: List<MistralMessage>,
    @Json(name = "temperature") val temperature: Double = 0.2,
    @Json(name = "max_tokens") val maxTokens: Int = 4096,
    @Json(name = "response_format") val responseFormat: MistralResponseFormat? = MistralResponseFormat("json_object")
)

@JsonClass(generateAdapter = true)
data class MistralResponseFormat(
    @Json(name = "type") val type: String = "json_object"
)

@JsonClass(generateAdapter = true)
data class MistralMessage(
    @Json(name = "role") val role: String,
    @Json(name = "content") val content: String
)

@JsonClass(generateAdapter = true)
data class MistralChatResponse(
    @Json(name = "id") val id: String?,
    @Json(name = "choices") val choices: List<MistralChoice>?,
    @Json(name = "error") val error: MistralError?
)

@JsonClass(generateAdapter = true)
data class MistralChoice(
    @Json(name = "index") val index: Int?,
    @Json(name = "message") val message: MistralMessage?,
    @Json(name = "finish_reason") val finishReason: String?
)

@JsonClass(generateAdapter = true)
data class MistralError(
    @Json(name = "message") val message: String?,
    @Json(name = "type") val type: String?,
    @Json(name = "code") val code: String?
)
