package com.onesignal.notifications.internal.registration.impl

import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.google.android.gms.tasks.Tasks
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@RobolectricTest
class PushRegistratorFCMTests : FunSpec({
    test("returns the legacy FCM token without FID registration") {
        val registerForFid = mockk<() -> com.google.android.gms.tasks.Task<Void>>()
        val installationIdTask = mockk<() -> com.google.android.gms.tasks.Task<String>>()

        val token =
            withContext(Dispatchers.IO) {
                FirebaseTokenProvider(
                    legacyTokenTask = Tasks.forResult("legacy-token"),
                    registerForFid = registerForFid,
                    installationIdTask = installationIdTask,
                ).getToken()
            }

        token shouldBe "legacy-token"
        verify(exactly = 0) { registerForFid() }
        verify(exactly = 0) { installationIdTask() }
    }

    test("registers with FID and returns the installation ID when the legacy API is disabled") {
        var registrationCompleted = false

        val token =
            withContext(Dispatchers.IO) {
                FirebaseTokenProvider(
                    legacyTokenTask =
                    Tasks.forException(
                        IllegalStateException("API disabled. Please use register() instead"),
                    ),
                    registerForFid = {
                        registrationCompleted = true
                        Tasks.forResult(null)
                    },
                    installationIdTask = {
                        registrationCompleted shouldBe true
                        Tasks.forResult("installation-id")
                    },
                ).getToken()
            }

        token shouldBe "installation-id"
    }

    test("does not mask unrelated legacy token failures") {
        val failure = IllegalStateException("Firebase app was deleted")

        val thrown =
            shouldThrow<IllegalStateException> {
                withContext(Dispatchers.IO) {
                    FirebaseTokenProvider(
                        legacyTokenTask = Tasks.forException(failure),
                        registerForFid = { Tasks.forResult(null) },
                        installationIdTask = { Tasks.forResult("installation-id") },
                    ).getToken()
                }
            }

        thrown shouldBe failure
    }
})
