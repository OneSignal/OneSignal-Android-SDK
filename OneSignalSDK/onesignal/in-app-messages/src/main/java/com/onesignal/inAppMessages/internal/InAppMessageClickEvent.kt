package com.onesignal.inAppMessages.internal

import com.onesignal.inAppMessages.IInAppMessage
import com.onesignal.inAppMessages.IInAppMessageClickEvent
import com.onesignal.inAppMessages.IInAppMessageClickResult
import org.json.JSONObject

internal class InAppMessageClickEvent(msg: InAppMessage, actn: InAppMessageClickResult) : IInAppMessageClickEvent {
    override val message: IInAppMessage
        get() = _message

    override val result: IInAppMessageClickResult
        get() = _result

    private val _message: InAppMessage = msg
    private val _result: InAppMessageClickResult = actn

    fun toJSONObject(): JSONObject {
        return JSONObject()
            .put("message", _message.toJSONObject())
            .put("action", _result.toJSONObject())
    }
}
