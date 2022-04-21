package com.onesignal.user

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldNotBeSameInstanceAs

class UserIdentityTest : DescribeSpec({
    describe("An UserAnonymous") {
        val user = UserAnonymous()
        context("identity") {
            it("is equal to a different UserIdentity.Anonymous instance") {
                user.identity shouldNotBeSameInstanceAs UserIdentity.Anonymous()
            }
        }
    }

    describe("An UserIdentified") {
        val myId = "myId"

        context("identity with ExternalIdWithoutAuth") {
            val getNewIdentity = { UserIdentity.ExternalIdWithoutAuth(myId) }
            it("is equal to ExternalIdWithoutAuth") {
                val user = UserIdentified(getNewIdentity())
                user.identity shouldBe getNewIdentity()
            }
        }

        context("identity with ExternalIdWithAuthHash") {
            val mockHash = "mockHash"
            val getNewIdentity = { UserIdentity.ExternalIdWithAuthHash(myId, mockHash) }
            it("is equal to ExternalIdWithAuthHash") {
                val user = UserIdentified(getNewIdentity())
                user.identity shouldBe getNewIdentity()
            }
        }
    }
})
