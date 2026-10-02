package com.example.data.network

import com.example.data.model.MistralChatRequest
import com.example.data.model.MistralChatResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Retrofit service definition for interacting with the Mistral AI API (Free Tier models).
 * Free tier models:
 * - mistral-small-latest (Mistral Small 3 / 24B)
 * - open-mistral-nemo (Mistral NeMo 12B)
 * - open-mistral-7b (Mistral 7B v0.3)
 * - codestral-latest (Codestral 22B)
 * - open-mixtral-8x7b (Mixtral 8x7B)
 */
interface MistralApiService {

    @POST("v1/chat/completions")
    suspend fun createChatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: MistralChatRequest
    ): Response<MistralChatResponse>

    @GET("v1/models")
    suspend fun listModels(
        @Header("Authorization") authorization: String
    ): Response<ResponseBody>
}

