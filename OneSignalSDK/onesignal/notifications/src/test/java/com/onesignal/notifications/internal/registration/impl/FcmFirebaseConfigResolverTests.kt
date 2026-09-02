package com.onesignal.notifications.internal.registration.impl

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private const val DASHBOARD_SENDER_ID = "388536902528"
private const val SHARED_SENDER_ID = FcmSharedProject.SENDER_ID

private fun credentials(
    senderId: String = DASHBOARD_SENDER_ID,
    projectId: String = "customer-project",
    applicationId: String = "1:$senderId:android:abc",
    apiKey: String = "AIza-customer",
) = FcmProjectCredentials(senderId, projectId, applicationId, apiKey)

private val sharedDefault =
    credentials(
        senderId = SHARED_SENDER_ID,
        projectId = FcmSharedProject.PROJECT_ID,
        applicationId = FcmSharedProject.APPLICATION_ID,
        apiKey = "AIza-shared",
    )

class FcmFirebaseConfigResolverTests : FunSpec({
    test("prefers the host google-services.json FirebaseApp when its sender id matches the dashboard") {
        val host = credentials()
        val backend = credentials(projectId = "backend-project")

        val resolved =
            FcmFirebaseConfigResolver.resolve(
                dashboardSenderId = DASHBOARD_SENDER_ID,
                defaultApp = host,
                backend = backend,
                sharedDefault = sharedDefault,
            )

        resolved!!.source shouldBe FcmFirebaseConfig.Source.GOOGLE_SERVICES
        resolved.reuseDefaultApp shouldBe true
        resolved.credentials shouldBe host
    }

    test("uses host google-services.json when the dashboard sender id is missing") {
        val host = credentials()

        val resolved =
            FcmFirebaseConfigResolver.resolve(
                dashboardSenderId = null,
                defaultApp = host,
                backend = null,
                sharedDefault = sharedDefault,
            )

        resolved!!.source shouldBe FcmFirebaseConfig.Source.GOOGLE_SERVICES
        resolved.credentials shouldBe host
    }

    test("does not use the host FirebaseApp when its sender id does not match the dashboard") {
        val host = credentials(senderId = SHARED_SENDER_ID)
        val backend = credentials(projectId = "backend-project")

        val resolved =
            FcmFirebaseConfigResolver.resolve(
                dashboardSenderId = DASHBOARD_SENDER_ID,
                defaultApp = host,
                backend = backend,
                sharedDefault = sharedDefault,
            )

        resolved!!.source shouldBe FcmFirebaseConfig.Source.BACKEND
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

        resolved!!.source shouldBe FcmFirebaseConfig.Source.BACKEND
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

        resolved!!.source shouldBe FcmFirebaseConfig.Source.BACKEND
        resolved.reuseDefaultApp shouldBe false
        resolved.credentials shouldBe backend
    }

    test("uses backend FCM params when the dashboard sender id is missing") {
        val backend = credentials(projectId = "backend-project")

        val resolved =
            FcmFirebaseConfigResolver.resolve(
                dashboardSenderId = "",
                defaultApp = null,
                backend = backend,
                sharedDefault = sharedDefault,
            )

        resolved!!.source shouldBe FcmFirebaseConfig.Source.BACKEND
        resolved.credentials shouldBe backend
    }

    test("does not use backend credentials whose application id belongs to a different sender") {
        val backend =
            credentials(
                senderId = DASHBOARD_SENDER_ID,
                applicationId = "1:$SHARED_SENDER_ID:android:mixed",
            )

        val resolved =
            FcmFirebaseConfigResolver.resolve(
                dashboardSenderId = DASHBOARD_SENDER_ID,
                defaultApp = null,
                backend = backend,
                sharedDefault = sharedDefault,
            )

        resolved shouldBe null
    }

    test("does not treat the shared public project as backend credentials") {
        val backend = sharedDefault.copy(senderId = DASHBOARD_SENDER_ID)

        val resolved =
            FcmFirebaseConfigResolver.resolve(
                dashboardSenderId = DASHBOARD_SENDER_ID,
                defaultApp = null,
                backend = backend,
                sharedDefault = sharedDefault,
            )

        resolved shouldBe null
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

        resolved shouldBe null
    }

    test("does not mix a customer dashboard sender with OneSignal's shared Firebase project") {
        val resolved =
            FcmFirebaseConfigResolver.resolve(
                dashboardSenderId = DASHBOARD_SENDER_ID,
                defaultApp = null,
                backend = null,
                sharedDefault = sharedDefault,
            )

        resolved shouldBe null
    }

    test("uses the shared Firebase project only when the dashboard sender is the shared sender") {
        val resolved =
            FcmFirebaseConfigResolver.resolve(
                dashboardSenderId = SHARED_SENDER_ID,
                defaultApp = null,
                backend = null,
                sharedDefault = sharedDefault,
            )

        resolved!!.source shouldBe FcmFirebaseConfig.Source.SHARED_DEFAULT
        resolved.reuseDefaultApp shouldBe false
        resolved.credentials shouldBe sharedDefault
    }

    test("does not fall back to the shared project when the dashboard sender is missing") {
        val resolved =
            FcmFirebaseConfigResolver.resolve(
                dashboardSenderId = null,
                defaultApp = null,
                backend = null,
                sharedDefault = sharedDefault,
            )

        resolved shouldBe null
    }

    test("treats blank credential fields as incomplete") {
        credentials(senderId = " ").isComplete shouldBe false
        credentials(projectId = "").isComplete shouldBe false
        credentials(applicationId = " ").isComplete shouldBe false
        credentials(apiKey = "").isComplete shouldBe false
        credentials().isComplete shouldBe true
    }

    test("extracts the project number from a Firebase application id") {
        FcmProjectCredentials.projectNumberFromApplicationId("1:388536902528:android:abc") shouldBe "388536902528"
        FcmProjectCredentials.projectNumberFromApplicationId("not-an-app-id") shouldBe null
        FcmProjectCredentials.projectNumberFromApplicationId("") shouldBe null
        credentials().projectNumber shouldBe DASHBOARD_SENDER_ID
    }

    test("recognizes OneSignal's shared public project") {
        FcmSharedProject.isShared(sharedDefault) shouldBe true
        FcmSharedProject.isShared(credentials()) shouldBe false
        FcmSharedProject.isShared(
            credentials(applicationId = "1:$SHARED_SENDER_ID:android:other"),
        ) shouldBe true
    }

    test("compatibleWithDashboard allows any complete customer project when the dashboard sender is absent") {
        FcmFirebaseConfigResolver.compatibleWithDashboard(credentials(), null) shouldBe true
        FcmFirebaseConfigResolver.compatibleWithDashboard(credentials(), DASHBOARD_SENDER_ID) shouldBe true
        FcmFirebaseConfigResolver.compatibleWithDashboard(
            credentials(senderId = SHARED_SENDER_ID),
            DASHBOARD_SENDER_ID,
        ) shouldBe false
    }
})
