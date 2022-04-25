package com.onesignal.user

import io.kotest.core.spec.style.FreeSpec
import io.kotest.core.spec.style.describeSpec
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldBeTypeOf

import io.kotest.datatest.withData

fun getUserByTests(userManager: UserManager) = describeSpec {
    include(getUserByAnonymousTests(userManager))
    include(getUserByKnownTests(userManager))
}

fun getUserByAnonymousTests(userManager: UserManager) = describeSpec {
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

fun getUserByKnownTests(userManager: UserManager) = describeSpec {
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

class UserManagerTest : FreeSpec({
//    context("user getter") {
//        val userManager = UserManager()
//        val user = userManager.user
//        it("gives UserAnonymous") {
//            user.shouldBeTypeOf<UserAnonymous>()
//        }
//        it("gives same UserAnonymous instance on 2nd call") {
//            userManager.user shouldBeSameInstanceAs user
//        }
//    }

    data class UserMangerInstance(
        val stateDescription: String,
        val userManager: UserManager
    )

    listOf(
        UserMangerInstance("Default", UserManager()),
        UserMangerInstance(
            "Accessed activeUser",
            UserManager().also { it.user }
        ),
        UserMangerInstance(
            "Assigned Known",
            UserManager().also { it.switchUser(Identity.ExternalId("1")) }
        ),
    ).forEach {
        include("State(${it.stateDescription}) - getUserBy - ", getUserByTests(it.userManager))
//        include(
//            "With state(${it.stateDescription}), getUserByAnonymousTests",
//            getUserByAnonymousTests(it.userManager)
//        )
//        include(
//            "With state(${it.stateDescription}), getUserByKnownTests",
//            getUserByKnownTests(it.userManager)
//        )
    }
})
