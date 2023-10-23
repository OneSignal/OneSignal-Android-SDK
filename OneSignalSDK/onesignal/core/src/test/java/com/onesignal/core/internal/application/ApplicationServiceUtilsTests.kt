package com.onesignal.core.internal.application

import android.app.Activity
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.onesignal.core.internal.application.impl.ApplicationService
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.extensions.RobolectricTest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.runner.junit4.KotestTestRunner
import org.junit.runner.RunWith
import com.onesignal.core.internal.application.impl.ApplicationServiceUtil

@RobolectricTest
@RunWith(KotestTestRunner::class)
class ApplicationServiceUtilsTests : FunSpec({

    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    test("returns true if application service is not null and app context is not null") {
        /* Given */
         val context = ApplicationProvider.getApplicationContext<Context>()
        val applicationService = ApplicationService()
        applicationService.start(context)

        /* When */
        val result = ApplicationServiceUtil.isValidInstance(applicationService)

        /* Then */
        result shouldBe true
    }

    test("returns false if application service is null") {
        /* Given */
        val applicationService: IApplicationService? = null


        /* When */
        val result = ApplicationServiceUtil.isValidInstance(applicationService)

        /* Then */
        result shouldBe false
    }

     test("returns false if app context is null and or exception is thrown") {
         /* Given */
         val applicationService = ApplicationService()

         /* When */
         val result = ApplicationServiceUtil.isValidInstance(applicationService)

         /* Then */
         result shouldBe false
     }

}) {
    companion object {
        fun pushActivity(applicationService: ApplicationService, currActivity: Activity, newActivity: Activity, destoryCurrent: Boolean = false) {
            applicationService.onActivityPaused(currActivity)
            applicationService.onActivityCreated(newActivity, null)
            applicationService.onActivityStarted(newActivity)
            applicationService.onActivityResumed(newActivity)
            applicationService.onActivityStopped(currActivity)

            if (destoryCurrent) {
                applicationService.onActivityDestroyed(currActivity)
            }
        }

        fun popActivity(applicationService: ApplicationService, currActivity: Activity, oldActivity: Activity) {
            applicationService.onActivityPaused(currActivity)
            applicationService.onActivityStarted(oldActivity)
            applicationService.onActivityResumed(oldActivity)
            applicationService.onActivityStopped(currActivity)
            applicationService.onActivityDestroyed(currActivity)
        }
    }
}
