package com.onesignal.user

import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.Suite
import org.spekframework.spek2.style.specification.describe
import kotlin.test.assertNotSame

import kotlin.test.assertSame
import kotlin.test.assertTrue

class UserManagerTest : Spek({
    describe("default UserManager") {
        val userManager = UserManager()
        val user = userManager.user

        context("get user") {
            it("gives UserAnonymous") {
                assertTrue(user is UserAnonymous)
            }

            it("gives same UserAnonymous instance on 2nd call") {
                assertSame(user, userManager.user)
            }
        }

        context("getUserBy") {
            it("gives same UserAnonymous - UserIdentity.Anonymous") {
                assertSame(user, userManager.getUserBy(UserIdentity.Anonymous()))
            }

            context("Identified") {
                val mockId = "mockId"
                it("gives UserIdentified with ExternalIdWithoutAuth") {
                    val user = userManager.getUserBy(UserIdentity.ExternalIdWithoutAuth(mockId))
                    assertTrue(user is UserIdentified)
                }

                it("gives differ User with ExternalIdWithoutAuth") {
                    assertNotSame(user, userManager.getUserBy(UserIdentity.ExternalIdWithoutAuth(mockId)))
                }
            }
        }
    }
})
