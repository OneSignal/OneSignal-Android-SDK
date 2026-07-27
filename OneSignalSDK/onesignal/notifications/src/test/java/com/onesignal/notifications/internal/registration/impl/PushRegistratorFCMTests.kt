package com.onesignal.notifications.internal.registration.impl

import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.google.android.gms.tasks.Tasks
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@RobolectricTest
class PushRegistratorFCMTests : FunSpec({
    test("returns the legacy FCM token without FID registration") {
        val legacyTokenTask = mockk<() -> com.google.android.gms.tasks.Task<String>>()
        val registerForFid = mockk<() -> com.google.android.gms.tasks.Task<Void>>()
        val installationIdTask = mockk<() -> com.google.android.gms.tasks.Task<String>>()
        every { legacyTokenTask() } returns Tasks.forResult("legacy-token")

        val token =
            withContext(Dispatchers.IO) {
                FirebaseTokenProvider(
                    fidRegistrationEnabled = false,
                    legacyTokenTask = legacyTokenTask,
                    registerForFid = registerForFid,
                    installationIdTask = installationIdTask,
                ).getToken()
            }

        token shouldBe "legacy-token"
        verify(exactly = 1) { legacyTokenTask() }
        verify(exactly = 0) { registerForFid() }
        verify(exactly = 0) { installationIdTask() }
    }

    test("registers with FID without calling the disabled legacy API") {
        var registrationCompleted = false
        val legacyTokenTask = mockk<() -> com.google.android.gms.tasks.Task<String>>()

        val token =
            withContext(Dispatchers.IO) {
                FirebaseTokenProvider(
                    fidRegistrationEnabled = true,
                    legacyTokenTask = legacyTokenTask,
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
        verify(exactly = 0) { legacyTokenTask() }
    }

    test("does not mask unrelated legacy token failures") {
        val failure = IllegalStateException("Firebase app was deleted")

        val thrown =
            shouldThrow<IllegalStateException> {
                withContext(Dispatchers.IO) {
                    FirebaseTokenProvider(
                        fidRegistrationEnabled = false,
                        legacyTokenTask = { Tasks.forException(failure) },
                        registerForFid = { Tasks.forResult(null) },
                        installationIdTask = { Tasks.forResult("installation-id") },
                    ).getToken()
                }
            }

        thrown shouldBe failure
    }
})
