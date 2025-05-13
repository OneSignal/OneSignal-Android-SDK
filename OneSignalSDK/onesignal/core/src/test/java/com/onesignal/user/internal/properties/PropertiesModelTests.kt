package com.onesignal.user.internal.properties

import com.onesignal.common.putJSONObject
import com.onesignal.user.internal.identity.IdentityModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.json.JSONObject
import java.util.UUID

class PropertiesModelTests :
    FunSpec({

        test("successfully initializes varying tag names") {
            // Given
            val varyingTags =
                JSONObject()
                    .putJSONObject(PropertiesModel::tags.name) {
                        it
                            .put("value", "data1")
                            .put("isEmpty", "data2")
                            .put("object", "data3")
                            .put("1", "data4")
                            .put("false", "data5")
                            .put("15.7", "data6")
                    }
            val propertiesModel = PropertiesModel()

            // When
            propertiesModel.initializeFromJson(varyingTags)
            val tagsModel = propertiesModel.tags

            // Then
            tagsModel["value"] shouldBe "data1"
            tagsModel["isEmpty"] shouldBe "data2"
            tagsModel["object"] shouldBe "data3"
            tagsModel["1"] shouldBe "data4"
            tagsModel["false"] shouldBe "data5"
            tagsModel["15.7"] shouldBe "data6"
        }

        test("successfully initializes varying of identities") {
            // Given
            val onesignalId = UUID.randomUUID().toString()
            val varyingIdentities =
                JSONObject()
                    .put("onesignal_id", onesignalId)
                    .put("external_id", "myExtId")
                    .put("a", "test1")
                    .put("al", "test2")
                    .put("b", "test3")
                    .put("value", "test4")
                    .put("isEmpty", "test5")
                    .put("object", "test6")
                    .put("id", "test7")
                    .put("os", "test8")
                    .put("myid", "test9")
                    .put("facebookID", "test10")
            val identityModel = IdentityModel()

            // When
            identityModel.initializeFromJson(varyingIdentities)

            // Then
            identityModel.onesignalId shouldBe onesignalId
            identityModel.externalId shouldBe "myExtId"
            identityModel.getValue("a") shouldBe "test1"
            identityModel.getValue("al") shouldBe "test2"
            identityModel.getValue("b") shouldBe "test3"
            identityModel.getValue("value") shouldBe "test4"
            identityModel.getValue("isEmpty") shouldBe "test5"
            identityModel.getValue("object") shouldBe "test6"
            identityModel.getValue("id") shouldBe "test7"
            identityModel.getValue("os") shouldBe "test8"
            identityModel.getValue("myid") shouldBe "test9"
            identityModel.getValue("facebookID") shouldBe "test10"
        }
    })
