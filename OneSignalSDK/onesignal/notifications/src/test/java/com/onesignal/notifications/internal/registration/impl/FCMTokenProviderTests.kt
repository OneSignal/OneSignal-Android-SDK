package com.onesignal.notifications.internal.registration.impl

import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val SENDER_ID = "388536902528"

private fun completedTask(): Task<Void> = TaskCompletionSource<Void>().apply { setResult(null) }.task

private fun failedTask(exception: Exception): Task<Void> =
    TaskCompletionSource<Void>().apply { setException(exception) }.task

private fun registration(
    senderId: String? = SENDER_ID,
    register: () -> Task<*> = { completedTask() },
    installationId: () -> Task<String> = { Tasks.forResult("installation-id") },
) = FCMTokenProvider.InstallationIdRegistration(senderId, register, installationId)

@RobolectricTest
class FCMTokenProviderTests : FunSpec({
    val disabledLegacyApi = IllegalStateException("API disabled. Please use {@link #register()} instead.")

    test("returns the legacy FCM token when the API is enabled") {
        val token =
            withContext(Dispatchers.IO) {
                FCMTokenProvider.getToken(SENDER_ID, { Tasks.forResult("fcm-token") }) {
                    throw AssertionError("should not fall back to installation id registration")
                }
            }

        token shouldBe "fcm-token"
    }

    test("registers the installation id when the legacy API is disabled") {
        var registered = false
        val token =
            withContext(Dispatchers.IO) {
                FCMTokenProvider.getToken(SENDER_ID, { Tasks.forException(disabledLegacyApi) }) {
                    registration(register = {
                        registered = true
                        completedTask()
                    })
                }
            }

        token shouldBe "installation-id"
        registered shouldBe true
    }

    test("does not register for unrelated IllegalStateExceptions") {
        val unrelated = IllegalStateException("Firebase is not initialized")

        val thrown =
            withContext(Dispatchers.IO) {
                shouldThrow<IllegalStateException> {
                    FCMTokenProvider.getToken(SENDER_ID, { Tasks.forException(unrelated) }) {
                        throw AssertionError("should not fall back to installation id registration")
                    }
                }
            }

        thrown shouldBe unrelated
    }

    test("explains the problem when there is no default FirebaseApp to register with") {
        val thrown =
            withContext(Dispatchers.IO) {
                shouldThrow<IllegalStateException> {
                    FCMTokenProvider.getToken(SENDER_ID, { Tasks.forException(disabledLegacyApi) }) { null }
                }
            }

        thrown.message!! shouldContain "no default FirebaseApp"
    }

    test("explains the problem when the default FirebaseApp uses a different sender id") {
        val thrown =
            withContext(Dispatchers.IO) {
                shouldThrow<IllegalStateException> {
                    FCMTokenProvider.getToken(SENDER_ID, { Tasks.forException(disabledLegacyApi) }) {
                        registration(
                            senderId = "999999999999",
                            register = { throw AssertionError("should not register on a sender id mismatch") },
                        )
                    }
                }
            }

        thrown.message!! shouldContain "sender id 999999999999"
    }

    test("propagates registration failures") {
        val registrationFailure = IllegalStateException("Registration failed")

        val thrown =
            withContext(Dispatchers.IO) {
                shouldThrow<IllegalStateException> {
                    FCMTokenProvider.getToken(SENDER_ID, { Tasks.forException(disabledLegacyApi) }) {
                        registration(
                            register = { failedTask(registrationFailure) },
                            installationId = { throw AssertionError("should not run after registration fails") },
                        )
                    }
                }
            }

        thrown shouldBe registrationFailure
    }
})
