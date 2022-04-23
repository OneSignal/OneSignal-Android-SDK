package com.onesignal.user

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldBeTypeOf
import io.kotest.matchers.types.shouldNotBeSameInstanceAs

class UserManagerTest : DescribeSpec({
    describe("default UserManager") {
        val userManager = UserManager()
        val user = userManager.user

        context("get user") {
            it("gives UserAnonymous") {
                user.shouldBeTypeOf<UserAnonymous>()
            }

            it("gives same UserAnonymous instance on 2nd call") {
                userManager.user shouldBeSameInstanceAs user
            }
        }

        context("getUserBy") {
            it("gives same UserAnonymous - Identity.Anonymous") {
                userManager.getUserBy(Identity.Anonymous()) shouldBeSameInstanceAs user
            }

            context("Identified") {
                val mockId = "mockId"
                val userWithId = userManager.getUserBy(Identity.ExternalId(mockId))
                it("gives UserIdentified with ExternalIdWithoutAuth") {
                    userWithId.shouldBeTypeOf<UserIdentified>()
                }

                it("gives differ User with ExternalIdWithoutAuth") {
                    userWithId shouldNotBeSameInstanceAs user
                }
            }
        }
    }
})
