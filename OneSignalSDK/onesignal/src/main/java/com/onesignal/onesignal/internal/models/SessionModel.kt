package com.onesignal.onesignal.internal.models

import com.onesignal.onesignal.internal.modeling.Model
import java.util.*

class SessionModel : Model() {
    /**
     * When the session was created
     */
    var created: Date = Date()
}