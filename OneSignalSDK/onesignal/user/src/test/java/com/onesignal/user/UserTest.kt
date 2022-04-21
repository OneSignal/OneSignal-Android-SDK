package com.onesignal.user

import org.junit.jupiter.api.Assertions.assertEquals

import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class UserTest : Spek({
    describe("An UserAnonymous") {
        val user = UserAnonymous()
        context("identity") {
            it("is equal to UserIdentity.Anonymous") {
                assertEquals(UserIdentity.Anonymous(), user.identity)
            }
        }
    }
})
