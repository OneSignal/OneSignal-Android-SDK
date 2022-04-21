package com.onesignal.user

import org.junit.jupiter.api.Assertions.*

import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class UserIdentityTest : Spek({
    describe("An UserAnonymous") {
        val user = UserAnonymous()
        context("identity") {
            it("is equal a different UserIdentity.Anonymous instance") {
                assertEquals(UserIdentity.Anonymous(), user.identity)
            }
        }
    }

    describe("An UserIdentified") {
        val myId = "myId"

        context("identity with ExternalIdWithoutAuth") {
            val getNewIdentity = { UserIdentity.ExternalIdWithoutAuth(myId) }
            it("is equal to ExternalIdWithoutAuth") {
                val user = UserIdentified(getNewIdentity())
                assertEquals(getNewIdentity(), user.identity)
            }
        }

        context("identity with ExternalIdWithAuthHash") {
            val mockHash = "mockHash"
            val getNewIdentity = { UserIdentity.ExternalIdWithAuthHash(myId, mockHash) }
            it("is equal to ExternalIdWithAuthHash") {
                val user = UserIdentified(getNewIdentity())
                assertEquals(getNewIdentity(), user.identity)
            }
        }
    }
})
