package com.onesignal.user.internal.subscriptions

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SubscriptionStatusTests : FunSpec({
    test("uses the backend-allocated FID status values") {
        SubscriptionStatus.FIREBASE_FCM_FID_DEFAULT_APP_MISSING.value shouldBe -32
        SubscriptionStatus.FIREBASE_FCM_FID_REGISTER_API_UNAVAILABLE.value shouldBe -33
        SubscriptionStatus.FIREBASE_FCM_FID_REGISTRATION_FAILED.value shouldBe -34
    }

    test("hydrates known notification types unchanged") {
        SubscriptionStatus.values().forEach { status ->
            SubscriptionStatus.fromNotificationTypes(status.value) shouldBe status
        }
    }

    test("hydrates unknown disabled notification types as a channel-neutral error") {
        SubscriptionStatus.fromNotificationTypes(-999) shouldBe SubscriptionStatus.ERROR
    }

    test("hydrates unknown positive notification types as subscribed") {
        SubscriptionStatus.fromNotificationTypes(2) shouldBe SubscriptionStatus.SUBSCRIBED
    }
})
