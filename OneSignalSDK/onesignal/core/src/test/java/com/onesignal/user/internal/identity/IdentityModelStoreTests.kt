package com.onesignal.user.internal.identity

import com.onesignal.common.IDManager
import com.onesignal.mocks.MockHelper
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

class IdentityModelStoreTests : FunSpec({
    test("backendOneSignalIdOrNull returns the id once the backend has assigned one") {
        // Given
        val store = MockHelper.identityModelStore { it.onesignalId = "onesignalId" }

        // When / Then
        store.backendOneSignalIdOrNull() shouldBe "onesignalId"
    }

    test("backendOneSignalIdOrNull is null while the user only has a local placeholder id") {
        // Given: first launch, login() and logout() all assign a local id that the create user
        // response later replaces. Requests that name the user by onesignal_id must not send it,
        // since the server would look up a user it has never seen.
        val store = MockHelper.identityModelStore { it.onesignalId = IDManager.createLocalId() }

        // When / Then
        store.backendOneSignalIdOrNull().shouldBeNull()
    }

    test("backendOneSignalIdOrNull is null when the id is empty") {
        // Given
        val store = MockHelper.identityModelStore { it.onesignalId = "" }

        // When / Then
        store.backendOneSignalIdOrNull().shouldBeNull()
    }

    test("backendOneSignalIdOrNull is null when the identity model has no id at all") {
        // Given: the singleton store hands out an empty model before the first user is created,
        // and reading onesignalId on it throws, so the helper has to check for the property first.
        val store = mockk<IdentityModelStore>(relaxed = true)
        every { store.model } returns IdentityModel()

        // When / Then
        store.backendOneSignalIdOrNull().shouldBeNull()
    }
})
