package com.onesignal.user

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class UserTest : DescribeSpec({
    describe("UserAnonymous") {
        val user = UserAnonymous()

        context("identity") {
            it("is Anonymous") {
                user.identity shouldBe Identity.Anonymous()
            }
        }
    }

    describe("UserKnown") {
        val mockId = "mockId"

        context("ExternalId") {
            val mockIdentity = { Identity.ExternalId(mockId) }
            val user = UserKnown(mockIdentity())

            it("identity equals ExternalId") {
                user.identity shouldBe mockIdentity()
            }
        }

        context("ExternalIdWithAuthHash") {
            val mockAuthHash = "mockAuthHash"
            val mockIdentity = {
                Identity.ExternalIdWithAuthHash(mockId, mockAuthHash)
            }
            val user = UserKnown(mockIdentity())

            it("identity equals ExternalIdWithAuthHash") {
                user.identity shouldBe mockIdentity()
            }
        }
    }
})
