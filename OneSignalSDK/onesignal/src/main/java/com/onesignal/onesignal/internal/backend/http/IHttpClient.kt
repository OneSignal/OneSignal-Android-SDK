package com.onesignal.onesignal.internal.backend.http

import org.json.JSONObject

interface IHttpClient  {
    suspend fun postAsync(url: String, body: JSONObject) : JSONObject
    suspend fun getAsync(url: String) : JSONObject
    suspend fun putAsync(url: String, body: JSONObject) : JSONObject
    suspend fun patchAsync(url: String, body: JSONObject) : JSONObject
    suspend fun deleteAsync(url: String)
}