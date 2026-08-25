package com.onesignal.notifications.internal.registration.impl

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private const val DASHBOARD_SENDER_ID = "388536902528"
private const val OTHER_SENDER_ID = "754795614042"

private fun credentials(
    senderId: String = DASHBOARD_SENDER_ID,
    projectId: String = "customer-project",
    applicationId: String = "1:$senderId:android:abc",
    apiKey: String = "AIza-customer",
) = FcmProjectCredentials(senderId, projectId, applicationId, apiKey)

private val sharedDefault =
    credentials(
        senderId = DASHBOARD_SENDER_ID,
        projectId = "onesignal-shared-public",
        applicationId = "1:754795614042:android:c682b8144a8dd52bc1ad63",
        apiKey = "AIza-shared",
    )

class FcmFirebaseConfigResolverTests : FunSpec({
    test("prefers the host google-services.json FirebaseApp when its sender id matches the dashboard") {
        val host = credentials()

        val resolved =
            FcmFirebaseConfigResolver.resolve(
                dashboardSenderId = DASHBOARD_SENDER_ID,
                defaultApp = host,
                backend = credentials(projectId = "backend-project"),
                sharedDefault = sharedDefault,
            )

        resolved.source shouldBe FcmFirebaseConfig.Source.GOOGLE_SERVICES
        resolved.reuseDefaultApp shouldBe true
        resolved.credentials shouldBe host
    }

    test("does not use the host FirebaseApp when its sender id does not match the dashboard") {
        val host = credentials(senderId = OTHER_SENDER_ID)
        val backend = credentials(projectId = "backend-project")

        val resolved =
            FcmFirebaseConfigResolver.resolve(
                dashboardSenderId = DASHBOARD_SENDER_ID,
                defaultApp = host,
                backend = backend,
                sharedDefault = sharedDefault,
            )

        resolved.source shouldBe FcmFirebaseConfig.Source.BACKEND
        resolved.reuseDefaultApp shouldBe false
        resolved.credentials shouldBe backend
    }

    test("does not use incomplete host Firebase options") {
        val host = credentials(projectId = " ")
        val backend = credentials(projectId = "backend-project")

        val resolved =
            FcmFirebaseConfigResolver.resolve(
                dashboardSenderId = DASHBOARD_SENDER_ID,
                defaultApp = host,
                backend = backend,
                sharedDefault = sharedDefault,
            )

        resolved.source shouldBe FcmFirebaseConfig.Source.BACKEND
        resolved.credentials shouldBe backend
    }

    test("uses complete backend FCM params when google-services.json is absent") {
        val backend = credentials(projectId = "backend-project")

        val resolved =
            FcmFirebaseConfigResolver.resolve(
                dashboardSenderId = DASHBOARD_SENDER_ID,
                defaultApp = null,
                backend = backend,
                sharedDefault = sharedDefault,
            )

        resolved.source shouldBe FcmFirebaseConfig.Source.BACKEND
        resolved.reuseDefaultApp shouldBe false
        resolved.credentials shouldBe backend
    }

    test("does not use incomplete backend FCM params") {
        val backend = credentials(apiKey = "")

        val resolved =
            FcmFirebaseConfigResolver.resolve(
                dashboardSenderId = DASHBOARD_SENDER_ID,
                defaultApp = null,
                backend = backend,
                sharedDefault = sharedDefault,
            )

        resolved.source shouldBe FcmFirebaseConfig.Source.SHARED_DEFAULT
        resolved.credentials shouldBe sharedDefault
    }

    test("falls back to OneSignal's shared Firebase project when nothing else is available") {
        val resolved =
            FcmFirebaseConfigResolver.resolve(
                dashboardSenderId = DASHBOARD_SENDER_ID,
                defaultApp = null,
                backend = null,
                sharedDefault = sharedDefault,
            )

        resolved.source shouldBe FcmFirebaseConfig.Source.SHARED_DEFAULT
        resolved.reuseDefaultApp shouldBe false
        resolved.credentials shouldBe sharedDefault
    }

    test("treats blank credential fields as incomplete") {
        credentials(senderId = " ").isComplete shouldBe false
        credentials(projectId = "").isComplete shouldBe false
        credentials(applicationId = " ").isComplete shouldBe false
        credentials(apiKey = "").isComplete shouldBe false
        credentials().isComplete shouldBe true
    }
})
