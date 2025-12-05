package com.onesignal

import android.content.Context
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.inAppMessages.IInAppMessagesManager
import com.onesignal.location.ILocationManager
import com.onesignal.notifications.INotificationsManager
import com.onesignal.session.ISessionManager
import com.onesignal.user.IUserManager
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking

@RobolectricTest
class OneSignalAccessorsTests : FunSpec({

    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    afterAny {
        val context = getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("OneSignal", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val otherPrefs = context.getSharedPreferences("com.onesignal", Context.MODE_PRIVATE)
        otherPrefs.edit().clear().commit()
        Thread.sleep(50)
    }

    context("suspend accessor methods exist and return correct types") {
        test("getUserSuspend returns IUserManager after initialization") {
            runBlocking {
                // Given
                val context = getApplicationContext<Context>()
                OneSignal.initWithContextSuspend(context, "testAppId")

                // When
                val user = OneSignal.getUserSuspend()

                // Then
                user.shouldBeInstanceOf<IUserManager>()
            }
        }

        test("getSessionSuspend returns ISessionManager after initialization") {
            runBlocking {
                // Given
                val context = getApplicationContext<Context>()
                OneSignal.initWithContextSuspend(context, "testAppId")

                // When
                val session = OneSignal.getSessionSuspend()

                // Then
                session.shouldBeInstanceOf<ISessionManager>()
            }
        }

        test("getNotificationsSuspend returns INotificationsManager after initialization") {
            runBlocking {
                // Given
                val context = getApplicationContext<Context>()
                OneSignal.initWithContextSuspend(context, "testAppId")

                // When
                val notifications = OneSignal.getNotificationsSuspend()

                // Then
                notifications.shouldBeInstanceOf<INotificationsManager>()
            }
        }

        test("getLocationSuspend returns ILocationManager after initialization") {
            runBlocking {
                // Given
                val context = getApplicationContext<Context>()
                OneSignal.initWithContextSuspend(context, "testAppId")

                // When
                val location = OneSignal.getLocationSuspend()

                // Then
                location.shouldBeInstanceOf<ILocationManager>()
            }
        }

        test("getInAppMessagesSuspend returns IInAppMessagesManager after initialization") {
            runBlocking {
                // Given
                val context = getApplicationContext<Context>()
                OneSignal.initWithContextSuspend(context, "testAppId")

                // When
                val inAppMessages = OneSignal.getInAppMessagesSuspend()

                // Then
                inAppMessages.shouldBeInstanceOf<IInAppMessagesManager>()
            }
        }
    }

    context("suspend configuration property accessors") {
        test("getConsentRequiredSuspend and setConsentRequiredSuspend work correctly") {
            runBlocking {
                // Given
                val context = getApplicationContext<Context>()
                OneSignal.initWithContextSuspend(context, "testAppId")

                // When - set to true
                OneSignal.setConsentRequiredSuspend(true)

                // Then
                OneSignal.getConsentRequiredSuspend() shouldBe true

                // When - set to false
                OneSignal.setConsentRequiredSuspend(false)

                // Then
                OneSignal.getConsentRequiredSuspend() shouldBe false
            }
        }

        test("getConsentGivenSuspend and setConsentGivenSuspend work correctly") {
            runBlocking {
                // Given
                val context = getApplicationContext<Context>()
                OneSignal.initWithContextSuspend(context, "testAppId")

                // When - set to true
                OneSignal.setConsentGivenSuspend(true)

                // Then
                OneSignal.getConsentGivenSuspend() shouldBe true

                // When - set to false
                OneSignal.setConsentGivenSuspend(false)

                // Then
                OneSignal.getConsentGivenSuspend() shouldBe false
            }
        }

        test("getDisableGMSMissingPromptSuspend and setDisableGMSMissingPromptSuspend work correctly") {
            runBlocking {
                // Given
                val context = getApplicationContext<Context>()
                OneSignal.initWithContextSuspend(context, "testAppId")

                // When - set to true
                OneSignal.setDisableGMSMissingPromptSuspend(true)

                // Then
                OneSignal.getDisableGMSMissingPromptSuspend() shouldBe true

                // When - set to false
                OneSignal.setDisableGMSMissingPromptSuspend(false)

                // Then
                OneSignal.getDisableGMSMissingPromptSuspend() shouldBe false
            }
        }
    }

    context("suspend accessors match non-suspend properties") {
        test("getUserSuspend returns same instance as User property") {
            runBlocking {
                // Given
                val context = getApplicationContext<Context>()
                OneSignal.initWithContextSuspend(context, "testAppId")

                // When
                val userSuspend = OneSignal.getUserSuspend()
                val userProperty = OneSignal.User

                // Then
                userSuspend shouldBe userProperty
            }
        }

        test("getSessionSuspend returns same instance as Session property") {
            runBlocking {
                // Given
                val context = getApplicationContext<Context>()
                OneSignal.initWithContextSuspend(context, "testAppId")

                // When
                val sessionSuspend = OneSignal.getSessionSuspend()
                val sessionProperty = OneSignal.Session

                // Then
                sessionSuspend shouldBe sessionProperty
            }
        }

        test("getNotificationsSuspend returns same instance as Notifications property") {
            runBlocking {
                // Given
                val context = getApplicationContext<Context>()
                OneSignal.initWithContextSuspend(context, "testAppId")

                // When
                val notificationsSuspend = OneSignal.getNotificationsSuspend()
                val notificationsProperty = OneSignal.Notifications

                // Then
                notificationsSuspend shouldBe notificationsProperty
            }
        }

        test("getLocationSuspend returns same instance as Location property") {
            runBlocking {
                // Given
                val context = getApplicationContext<Context>()
                OneSignal.initWithContextSuspend(context, "testAppId")

                // When
                val locationSuspend = OneSignal.getLocationSuspend()
                val locationProperty = OneSignal.Location

                // Then
                locationSuspend shouldBe locationProperty
            }
        }

        test("getInAppMessagesSuspend returns same instance as InAppMessages property") {
            runBlocking {
                // Given
                val context = getApplicationContext<Context>()
                OneSignal.initWithContextSuspend(context, "testAppId")

                // When
                val inAppMessagesSuspend = OneSignal.getInAppMessagesSuspend()
                val inAppMessagesProperty = OneSignal.InAppMessages

                // Then
                inAppMessagesSuspend shouldBe inAppMessagesProperty
            }
        }

        test("suspend configuration accessors match non-suspend properties") {
            runBlocking {
                // Given
                val context = getApplicationContext<Context>()
                OneSignal.initWithContextSuspend(context, "testAppId")

                // When - set via suspend methods
                OneSignal.setConsentRequiredSuspend(true)
                OneSignal.setConsentGivenSuspend(true)
                OneSignal.setDisableGMSMissingPromptSuspend(true)

                // Then - verify via both suspend and non-suspend accessors
                OneSignal.getConsentRequiredSuspend() shouldBe OneSignal.consentRequired
                OneSignal.getConsentGivenSuspend() shouldBe OneSignal.consentGiven
                OneSignal.getDisableGMSMissingPromptSuspend() shouldBe OneSignal.disableGMSMissingPrompt

                // When - set via non-suspend properties
                OneSignal.consentRequired = false
                OneSignal.consentGiven = false
                OneSignal.disableGMSMissingPrompt = false

                // Then - verify via suspend accessors
                OneSignal.getConsentRequiredSuspend() shouldBe false
                OneSignal.getConsentGivenSuspend() shouldBe false
                OneSignal.getDisableGMSMissingPromptSuspend() shouldBe false
            }
        }
    }

    context("loginSuspend and logoutSuspend methods") {
        test("loginSuspend can be called after initialization") {
            runBlocking {
                // Given
                val context = getApplicationContext<Context>()
                OneSignal.initWithContextSuspend(context, "testAppId")

                // When/Then - should not throw
                OneSignal.loginSuspend("testExternalId")
            }
        }

        test("logoutSuspend can be called after initialization") {
            runBlocking {
                // Given
                val context = getApplicationContext<Context>()
                OneSignal.initWithContextSuspend(context, "testAppId")

                // When/Then - should not throw
                OneSignal.logoutSuspend()
            }
        }

        test("loginSuspend with JWT token can be called after initialization") {
            runBlocking {
                // Given
                val context = getApplicationContext<Context>()
                OneSignal.initWithContextSuspend(context, "testAppId")

                // When/Then - should not throw
                OneSignal.loginSuspend("testExternalId", "testJwtToken")
            }
        }
    }

    context("non-suspend accessor properties exist and return correct types") {
        test("User property returns IUserManager after initialization") {
            runBlocking {
                // Given
                val context = getApplicationContext<Context>()
                OneSignal.initWithContextSuspend(context, "testAppId")

                // When
                val user = OneSignal.User

                // Then
                user.shouldBeInstanceOf<IUserManager>()
            }
        }

        test("Session property returns ISessionManager after initialization") {
            runBlocking {
                // Given
                val context = getApplicationContext<Context>()
                OneSignal.initWithContextSuspend(context, "testAppId")

                // When
                val session = OneSignal.Session

                // Then
                session.shouldBeInstanceOf<ISessionManager>()
            }
        }

        test("Notifications property returns INotificationsManager after initialization") {
            runBlocking {
                // Given
                val context = getApplicationContext<Context>()
                OneSignal.initWithContextSuspend(context, "testAppId")

                // When
                val notifications = OneSignal.Notifications

                // Then
                notifications.shouldBeInstanceOf<INotificationsManager>()
            }
        }

        test("Location property returns ILocationManager after initialization") {
            runBlocking {
                // Given
                val context = getApplicationContext<Context>()
                OneSignal.initWithContextSuspend(context, "testAppId")

                // When
                val location = OneSignal.Location

                // Then
                location.shouldBeInstanceOf<ILocationManager>()
            }
        }

        test("InAppMessages property returns IInAppMessagesManager after initialization") {
            runBlocking {
                // Given
                val context = getApplicationContext<Context>()
                OneSignal.initWithContextSuspend(context, "testAppId")

                // When
                val inAppMessages = OneSignal.InAppMessages

                // Then
                inAppMessages.shouldBeInstanceOf<IInAppMessagesManager>()
            }
        }
    }

    context("non-suspend configuration properties") {
        test("consentRequired property can be get and set") {
            runBlocking {
                // Given
                val context = getApplicationContext<Context>()
                OneSignal.initWithContextSuspend(context, "testAppId")

                // When - set to true
                OneSignal.consentRequired = true

                // Then
                OneSignal.consentRequired shouldBe true

                // When - set to false
                OneSignal.consentRequired = false

                // Then
                OneSignal.consentRequired shouldBe false
            }
        }

        test("consentGiven property can be get and set") {
            runBlocking {
                // Given
                val context = getApplicationContext<Context>()
                OneSignal.initWithContextSuspend(context, "testAppId")

                // When - set to true
                OneSignal.consentGiven = true

                // Then
                OneSignal.consentGiven shouldBe true

                // When - set to false
                OneSignal.consentGiven = false

                // Then
                OneSignal.consentGiven shouldBe false
            }
        }

        test("disableGMSMissingPrompt property can be get and set") {
            runBlocking {
                // Given
                val context = getApplicationContext<Context>()
                OneSignal.initWithContextSuspend(context, "testAppId")

                // When - set to true
                OneSignal.disableGMSMissingPrompt = true

                // Then
                OneSignal.disableGMSMissingPrompt shouldBe true

                // When - set to false
                OneSignal.disableGMSMissingPrompt = false

                // Then
                OneSignal.disableGMSMissingPrompt shouldBe false
            }
        }
    }

    context("non-suspend methods") {
        test("initWithContext method exists and can be called") {
            // Given
            val context = getApplicationContext<Context>()

            // When/Then - should not throw
            OneSignal.initWithContext(context, "testAppId")
        }

        test("login method exists and can be called after initialization") {
            runBlocking {
                // Given
                val context = getApplicationContext<Context>()
                OneSignal.initWithContextSuspend(context, "testAppId")

                // When/Then - should not throw
                OneSignal.login("testExternalId")
            }
        }

        test("login method with JWT token exists and can be called after initialization") {
            runBlocking {
                // Given
                val context = getApplicationContext<Context>()
                OneSignal.initWithContextSuspend(context, "testAppId")

                // When/Then - should not throw
                OneSignal.login("testExternalId", "testJwtToken")
            }
        }

        test("logout method exists and can be called after initialization") {
            runBlocking {
                // Given
                val context = getApplicationContext<Context>()
                OneSignal.initWithContextSuspend(context, "testAppId")

                // When/Then - should not throw
                OneSignal.logout()
            }
        }
    }

    context("non-suspend properties match suspend accessors") {
        test("User property returns same instance as getUserSuspend") {
            runBlocking {
                // Given
                val context = getApplicationContext<Context>()
                OneSignal.initWithContextSuspend(context, "testAppId")

                // When
                val userProperty = OneSignal.User
                val userSuspend = OneSignal.getUserSuspend()

                // Then
                userProperty shouldBe userSuspend
            }
        }

        test("Session property returns same instance as getSessionSuspend") {
            runBlocking {
                // Given
                val context = getApplicationContext<Context>()
                OneSignal.initWithContextSuspend(context, "testAppId")

                // When
                val sessionProperty = OneSignal.Session
                val sessionSuspend = OneSignal.getSessionSuspend()

                // Then
                sessionProperty shouldBe sessionSuspend
            }
        }

        test("Notifications property returns same instance as getNotificationsSuspend") {
            runBlocking {
                // Given
                val context = getApplicationContext<Context>()
                OneSignal.initWithContextSuspend(context, "testAppId")

                // When
                val notificationsProperty = OneSignal.Notifications
                val notificationsSuspend = OneSignal.getNotificationsSuspend()

                // Then
                notificationsProperty shouldBe notificationsSuspend
            }
        }

        test("Location property returns same instance as getLocationSuspend") {
            runBlocking {
                // Given
                val context = getApplicationContext<Context>()
                OneSignal.initWithContextSuspend(context, "testAppId")

                // When
                val locationProperty = OneSignal.Location
                val locationSuspend = OneSignal.getLocationSuspend()

                // Then
                locationProperty shouldBe locationSuspend
            }
        }

        test("InAppMessages property returns same instance as getInAppMessagesSuspend") {
            runBlocking {
                // Given
                val context = getApplicationContext<Context>()
                OneSignal.initWithContextSuspend(context, "testAppId")

                // When
                val inAppMessagesProperty = OneSignal.InAppMessages
                val inAppMessagesSuspend = OneSignal.getInAppMessagesSuspend()

                // Then
                inAppMessagesProperty shouldBe inAppMessagesSuspend
            }
        }
    }
})
