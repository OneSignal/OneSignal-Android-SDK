package com.onesignal.user

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldNotBeNull

import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldBeTypeOf

class UserManagerTest : DescribeSpec({
    context("New - user getter") {
        val userManager = UserManager()
        val user = userManager.user

        it("gives UserAnonymous") {
            user.shouldBeTypeOf<UserAnonymous>()
        }
        it("gives same UserAnonymous instance on 2nd call") {
            userManager.user shouldBeSameInstanceAs user
        }
    }

    data class UserMangerInstance(
        val stateDescription: String,
        val userManager: () -> UserManager
    )

    // List all possible User instance and Identity types of it's active user
    val activeUserStates = listOf(
        UserMangerInstance("New") { UserManager() },
        UserMangerInstance("user = UserAnonymous") {
            UserManager().also { it.user }
        },
        UserMangerInstance("user = UserKnown") {
            UserManager().also {
                it.switchUser(Identity.ExternalId("1"))
            }
        },
        UserMangerInstance("user = UserKnownWithAuthHash") {
            UserManager().also {
                it.switchUser(Identity.ExternalIdWithAuthHash("1", "2"))
            }
        },
    )

    activeUserStates.forEach { context("State(${it.stateDescription})") {
        context("user property getter") {
            val userManager = it.userManager()
            it("gives instance") {
                userManager.user.shouldNotBeNull()
            }
        }
        context("getUserBy") {
            context("Anonymous") {
                val userManager = it.userManager()
                val getAnonymousUser = {
                    userManager.getUserBy(Identity.Anonymous())
                }
                val firstUser = getAnonymousUser()

                it("gives UserAnonymous") {
                    firstUser.shouldBeTypeOf<UserAnonymous>()
                }
                it("gives same instance") {
                    getAnonymousUser() shouldBeSameInstanceAs firstUser
                }
            }
            context("Known") {
                val userManager = it.userManager()
                val mockId = "mockId"
                val identity = Identity.ExternalId(mockId)
                val userFromGetBy = userManager.getUserBy(identity)

                it("gives UserKnown") {
                    userFromGetBy.shouldBeTypeOf<UserKnown>()
                }
                it("gives a different User with than default") {
                    userFromGetBy shouldNotBe userManager.user
                }
                it("gives same instance with same id") {
                    userFromGetBy shouldBeSameInstanceAs
                        userManager.getUserBy(identity)
                }
            }
        }
    }}
})
