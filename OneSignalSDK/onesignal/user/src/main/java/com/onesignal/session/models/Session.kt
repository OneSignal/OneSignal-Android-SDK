package com.onesignal.session.models

import com.onesignal.models.modeling.Model
import java.util.*

class Session : Model() {
    /**
     * When the session was created
     */
    var created: Date = Date()
}