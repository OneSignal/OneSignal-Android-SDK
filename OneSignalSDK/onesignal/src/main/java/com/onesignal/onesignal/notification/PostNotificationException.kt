package com.onesignal.onesignal.notification

import org.json.JSONObject

class PostNotificationException(val json: JSONObject) : Exception()
