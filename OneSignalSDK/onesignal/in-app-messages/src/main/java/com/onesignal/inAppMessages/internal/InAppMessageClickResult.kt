package com.onesignal.inAppMessages.internal

import com.onesignal.inAppMessages.IInAppMessage
import com.onesignal.inAppMessages.IInAppMessageAction
import com.onesignal.inAppMessages.IInAppMessageClickResult
import org.json.JSONObject

internal class InAppMessageClickResult(msg: InAppMessage, actn: InAppMessageAction) : IInAppMessageClickResult {
    override val message: IInAppMessage
        get() = _message

    override val action: IInAppMessageAction
        get() = _action

    private val _message: InAppMessage = msg
    private val _action: InAppMessageAction = actn

    fun toJSONObject(): JSONObject {
        return JSONObject()
            .put("message", _message.toJSONObject())
            .put("action", _action.toJSONObject())
    }
}
