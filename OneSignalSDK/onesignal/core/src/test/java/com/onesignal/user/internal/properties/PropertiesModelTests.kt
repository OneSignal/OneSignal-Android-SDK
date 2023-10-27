package com.onesignal.user.internal.properties

import com.onesignal.common.putJSONObject
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.runner.junit4.KotestTestRunner
import org.json.JSONObject
import org.junit.runner.RunWith

@RunWith(KotestTestRunner::class)
class PropertiesModelTests : FunSpec({

    test("successfully initializes varying tag names") {
        /* Given */
        val varyingTags = JSONObject()
            .putJSONObject(PropertiesModel::tags.name) {
                it.put("value", "data1")
                    .put("isEmpty", "data2")
                    .put("object", "data3")
                    .put("1", "data4")
                    .put("false", "data5")
                    .put("15.7", "data6")
            }
        val propertiesModel = PropertiesModel()

        /* When */
        propertiesModel.initializeFromJson(varyingTags)
        val tagsModel = propertiesModel.tags

        /* Then */
        tagsModel["value"] shouldBe "data1"
        tagsModel["isEmpty"] shouldBe "data2"
        tagsModel["object"] shouldBe "data3"
        tagsModel["1"] shouldBe "data4"
        tagsModel["false"] shouldBe "data5"
        tagsModel["15.7"] shouldBe "data6"
    }
})
