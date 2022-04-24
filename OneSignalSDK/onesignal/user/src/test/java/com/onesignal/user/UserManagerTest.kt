package com.onesignal.user

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldBeTypeOf

class UserManagerTest : DescribeSpec({
    describe("UserManager") {
        val userManager = UserManager()

        context("user getter") {
            val user = userManager.user

            it("gives UserAnonymous") {
                user.shouldBeTypeOf<UserAnonymous>()
            }
            it("gives same UserAnonymous instance on 2nd call") {
                userManager.user shouldBeSameInstanceAs user
            }
        }

        context("getUserBy") {
            val defaultUser = userManager.user

            context("Identity.Anonymous") {
                val userFromGetBy = userManager.getUserBy(Identity.Anonymous())

                it("gives same UserAnonymous instance") {
                    userFromGetBy shouldBeSameInstanceAs defaultUser
                }
            }

            context("Identity.Known") {
                val mockId = "mockId"
                val userFromGetBy =
                    userManager.getUserBy(Identity.ExternalId(mockId))

                it("gives UserKnown") {
                    userFromGetBy.shouldBeTypeOf<UserKnown>()
                }
                it("gives a different User with than default") {
                    userFromGetBy shouldNotBe defaultUser
                }
            }
        }
    }
})
