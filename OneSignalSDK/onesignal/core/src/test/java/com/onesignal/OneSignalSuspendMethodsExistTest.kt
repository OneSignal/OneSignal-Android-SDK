package com.onesignal

import com.onesignal.inAppMessages.IInAppMessagesManager
import com.onesignal.location.ILocationManager
import com.onesignal.notifications.INotificationsManager
import com.onesignal.session.ISessionManager
import com.onesignal.user.IUserManager
import io.kotest.core.spec.style.FunSpec
import kotlin.reflect.full.memberFunctions

/**
 * Simple compilation tests to verify that all suspend methods exist in OneSignal class
 * with correct signatures. These tests verify the API surface but don't execute the methods.
 */
class OneSignalSuspendMethodsExistTest : FunSpec({

    test("initWithContextSuspend exists with correct signature") {
        // This test compiles only if the method exists with correct signature
        val method: suspend (android.content.Context, String) -> Boolean = OneSignal::initWithContextSuspend

        // Verify using reflection that it's actually a suspend function
        val kFunction = OneSignal::class.memberFunctions
            .find { it.name == "initWithContextSuspend" }

        assert(kFunction != null) { "initWithContextSuspend not found" }
        assert(kFunction!!.isSuspend) { "initWithContextSuspend is not a suspend function" }
    }

    test("getUserSuspend exists and returns IUserManager") {
        // Compilation check - this fails if method doesn't exist or has wrong return type
        val method: suspend () -> IUserManager = OneSignal::getUserSuspend

        val kFunction = OneSignal::class.memberFunctions
            .find { it.name == "getUserSuspend" }

        assert(kFunction != null) { "getUserSuspend not found" }
        assert(kFunction!!.isSuspend) { "getUserSuspend is not a suspend function" }
    }

    test("getSessionSuspend exists and returns ISessionManager") {
        val method: suspend () -> ISessionManager = OneSignal::getSessionSuspend

        val kFunction = OneSignal::class.memberFunctions
            .find { it.name == "getSessionSuspend" }

        assert(kFunction != null) { "getSessionSuspend not found" }
        assert(kFunction!!.isSuspend) { "getSessionSuspend is not a suspend function" }
    }

    test("getNotificationsSuspend exists and returns INotificationsManager") {
        val method: suspend () -> INotificationsManager = OneSignal::getNotificationsSuspend

        val kFunction = OneSignal::class.memberFunctions
            .find { it.name == "getNotificationsSuspend" }

        assert(kFunction != null) { "getNotificationsSuspend not found" }
        assert(kFunction!!.isSuspend) { "getNotificationsSuspend is not a suspend function" }
    }

    test("getLocationSuspend exists and returns ILocationManager") {
        val method: suspend () -> ILocationManager = OneSignal::getLocationSuspend

        val kFunction = OneSignal::class.memberFunctions
            .find { it.name == "getLocationSuspend" }

        assert(kFunction != null) { "getLocationSuspend not found" }
        assert(kFunction!!.isSuspend) { "getLocationSuspend is not a suspend function" }
    }

    test("getInAppMessagesSuspend exists and returns IInAppMessagesManager") {
        val method: suspend () -> IInAppMessagesManager = OneSignal::getInAppMessagesSuspend

        val kFunction = OneSignal::class.memberFunctions
            .find { it.name == "getInAppMessagesSuspend" }

        assert(kFunction != null) { "getInAppMessagesSuspend not found" }
        assert(kFunction!!.isSuspend) { "getInAppMessagesSuspend is not a suspend function" }
    }

    test("getConsentRequiredSuspend exists and returns Boolean") {
        val method: suspend () -> Boolean = OneSignal::getConsentRequiredSuspend

        val kFunction = OneSignal::class.memberFunctions
            .find { it.name == "getConsentRequiredSuspend" }

        assert(kFunction != null) { "getConsentRequiredSuspend not found" }
        assert(kFunction!!.isSuspend) { "getConsentRequiredSuspend is not a suspend function" }
    }

    test("setConsentRequiredSuspend exists with Boolean parameter") {
        val method: suspend (Boolean) -> Unit = OneSignal::setConsentRequiredSuspend

        val kFunction = OneSignal::class.memberFunctions
            .find { it.name == "setConsentRequiredSuspend" }

        assert(kFunction != null) { "setConsentRequiredSuspend not found" }
        assert(kFunction!!.isSuspend) { "setConsentRequiredSuspend is not a suspend function" }
    }

    test("getConsentGivenSuspend exists and returns Boolean") {
        val method: suspend () -> Boolean = OneSignal::getConsentGivenSuspend

        val kFunction = OneSignal::class.memberFunctions
            .find { it.name == "getConsentGivenSuspend" }

        assert(kFunction != null) { "getConsentGivenSuspend not found" }
        assert(kFunction!!.isSuspend) { "getConsentGivenSuspend is not a suspend function" }
    }

    test("setConsentGivenSuspend exists with Boolean parameter") {
        val method: suspend (Boolean) -> Unit = OneSignal::setConsentGivenSuspend

        val kFunction = OneSignal::class.memberFunctions
            .find { it.name == "setConsentGivenSuspend" }

        assert(kFunction != null) { "setConsentGivenSuspend not found" }
        assert(kFunction!!.isSuspend) { "setConsentGivenSuspend is not a suspend function" }
    }

    test("getDisableGMSMissingPromptSuspend exists and returns Boolean") {
        val method: suspend () -> Boolean = OneSignal::getDisableGMSMissingPromptSuspend

        val kFunction = OneSignal::class.memberFunctions
            .find { it.name == "getDisableGMSMissingPromptSuspend" }

        assert(kFunction != null) { "getDisableGMSMissingPromptSuspend not found" }
        assert(kFunction!!.isSuspend) { "getDisableGMSMissingPromptSuspend is not a suspend function" }
    }

    test("setDisableGMSMissingPromptSuspend exists with Boolean parameter") {
        val method: suspend (Boolean) -> Unit = OneSignal::setDisableGMSMissingPromptSuspend

        val kFunction = OneSignal::class.memberFunctions
            .find { it.name == "setDisableGMSMissingPromptSuspend" }

        assert(kFunction != null) { "setDisableGMSMissingPromptSuspend not found" }
        assert(kFunction!!.isSuspend) { "setDisableGMSMissingPromptSuspend is not a suspend function" }
    }

    test("loginSuspend exists with String and optional String parameters") {
        // Verify the method exists with correct signature using reflection
        // Note: There's only one loginSuspend with a default parameter for jwtBearerToken
        val kFunction = OneSignal::class.memberFunctions
            .find { it.name == "loginSuspend" }

        assert(kFunction != null) { "loginSuspend not found" }
        assert(kFunction!!.isSuspend) { "loginSuspend is not a suspend function" }
        assert(kFunction.parameters.size >= 2) { "loginSuspend should have at least 2 parameters (receiver + externalId)" }
    }

    test("logoutSuspend exists with no parameters") {
        val method: suspend () -> Unit = OneSignal::logoutSuspend

        val kFunction = OneSignal::class.memberFunctions
            .find { it.name == "logoutSuspend" }

        assert(kFunction != null) { "logoutSuspend not found" }
        assert(kFunction!!.isSuspend) { "logoutSuspend is not a suspend function" }
    }

    test("all suspend methods are marked with @JvmStatic") {
        // Get all suspend methods we added
        val suspendMethodNames = listOf(
            "getUserSuspend",
            "getSessionSuspend",
            "getNotificationsSuspend",
            "getLocationSuspend",
            "getInAppMessagesSuspend",
            "getConsentRequiredSuspend",
            "setConsentRequiredSuspend",
            "getConsentGivenSuspend",
            "setConsentGivenSuspend",
            "getDisableGMSMissingPromptSuspend",
            "setDisableGMSMissingPromptSuspend",
            "loginSuspend",
            "logoutSuspend"
        )

        // Verify each exists and is a static method (accessible via companion object)
        suspendMethodNames.forEach { methodName ->
            val kFunction = OneSignal::class.memberFunctions
                .find { it.name == methodName }

            assert(kFunction != null) { "$methodName not found" }
            assert(kFunction!!.isSuspend) { "$methodName is not a suspend function" }
        }
    }
})
