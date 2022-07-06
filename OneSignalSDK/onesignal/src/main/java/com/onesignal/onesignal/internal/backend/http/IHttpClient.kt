package com.onesignal.onesignal.internal.backend.http

import org.json.JSONObject

interface IHttpClient  {
    suspend fun post(url: String, body: JSONObject) : JSONObject
    suspend fun get(url: String) : JSONObject
    suspend fun put(url: String, body: JSONObject) : JSONObject
    suspend fun patch(url: String, body: JSONObject) : JSONObject
    suspend fun delete(url: String)
}