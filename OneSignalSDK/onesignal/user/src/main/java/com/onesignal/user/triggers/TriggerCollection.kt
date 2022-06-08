package com.onesignal.user.triggers

import com.onesignal.user.collections.OSMapCollection

class TriggerCollection(triggers: Collection<Trigger>) : OSMapCollection<String, Trigger>(triggers) {
}
