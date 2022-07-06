package com.onesignal.onesignal.internal.backend.http

import org.json.JSONObject

class HttpClient : IHttpClient  {
    override suspend fun post(url: String, body: JSONObject): JSONObject {
        //TODO("Not yet implemented")
        return JSONObject()
    }

    override suspend fun get(url: String): JSONObject {
        //TODO("Not yet implemented")
        return JSONObject()
    }

    override suspend fun put(url: String, body: JSONObject): JSONObject {
        //TODO("Not yet implemented")
        return JSONObject()
    }

    override suspend fun patch(url: String, body: JSONObject): JSONObject {
        //TODO("Not yet implemented")
        return JSONObject()
    }

    override suspend fun delete(url: String) {
        //TODO("Not yet implemented")
    }
}