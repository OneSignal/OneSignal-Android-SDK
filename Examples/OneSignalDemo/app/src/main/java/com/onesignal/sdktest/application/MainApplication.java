package com.onesignal.sdktest.application;

import android.annotation.SuppressLint;
import android.os.StrictMode;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.multidex.MultiDexApplication;

import com.onesignal.Continue;
import com.onesignal.OneSignal;
import com.onesignal.inAppMessages.IInAppMessageClickListener;
import com.onesignal.inAppMessages.IInAppMessageClickEvent;
import com.onesignal.inAppMessages.IInAppMessageDidDismissEvent;
import com.onesignal.inAppMessages.IInAppMessageDidDisplayEvent;
import com.onesignal.inAppMessages.IInAppMessageLifecycleListener;
import com.onesignal.debug.LogLevel;
import com.onesignal.inAppMessages.IInAppMessageWillDismissEvent;
import com.onesignal.inAppMessages.IInAppMessageWillDisplayEvent;
import com.onesignal.notifications.IDisplayableNotification;
import com.onesignal.notifications.INotificationLifecycleListener;
import com.onesignal.notifications.INotificationWillDisplayEvent;
import com.onesignal.sdktest.R;
import com.onesignal.sdktest.constant.Tag;
import com.onesignal.sdktest.constant.Text;
import com.onesignal.sdktest.notification.OneSignalNotificationSender;
import com.onesignal.sdktest.util.SharedPreferenceUtil;
import com.onesignal.user.state.IUserStateObserver;
import com.onesignal.user.state.UserChangedState;
import com.onesignal.user.state.UserState;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.honeycomb.opentelemetry.android.Honeycomb;
import io.honeycomb.opentelemetry.android.HoneycombOptions;
import io.opentelemetry.android.OpenTelemetryRum;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;

public class MainApplication extends MultiDexApplication {
    private static final int SLEEP_TIME_TO_MIMIC_ASYNC_OPERATION = 2000;

    public MainApplication() {
        // run strict mode to surface any potential issues easier
        StrictMode.enableDefaults();
    }

    @SuppressLint("NewApi")
    @Override
    public void onCreate() {
        super.onCreate();

        Map<String, String> attr = new HashMap<>();
        attr.put("test-setResourceAttributes", "12345");

        HoneycombOptions options = new HoneycombOptions.Builder(this)
                // Uncomment the line below to send to EU instance. Defaults to US.
                // .setApiEndpoint("https://api.eu1.honeycomb.io:443")
                .setApiKey("API_KEY_HERE")
                .setServiceName("OS-Android-SDK-Test")
                .setServiceVersion("0.0.1")
                .setDebug(true)
                .setResourceAttributes(attr)
                .setLogRecordProcessor((context, logRecord) ->
                        // NOTE: This gets call when a crash happens
                        // TODO: When moving logic to SDK, set app_id, package, org_id, subscription_id, and onesignal_id
                        logRecord.setAttribute(AttributeKey.stringKey("test-onEmit"), "test-onEmit-value"))
                .build();
        OpenTelemetryRum openTelRum = Honeycomb.Companion.configure(this, options);

        OneSignal.getDebug().setLogLevel(LogLevel.DEBUG);

        // OneSignal Initialization
        String appId = SharedPreferenceUtil.getOneSignalAppId(this);
        // If cached app id is null use the default, otherwise use cached.
        if (appId == null) {
            appId = getString(R.string.onesignal_app_id);
            SharedPreferenceUtil.cacheOneSignalAppId(this, appId);
        }

        /// NOTE: This doesn't add anything to the built in crash handler, even if the span isn't ended.
//        Tracer tracer = openTelRum.getOpenTelemetry().tracerBuilder("test-tracerBuilder").build();
//        Span span = tracer.spanBuilder("test-spanBuilder").setAttribute("test_app_id2", "1234").startSpan();
//        span.end();

        /// NOTE: Doesn't work on it's own, a span is not created by default.
        // Span.current().setAttribute("test_app_id", "123");

        OneSignalNotificationSender.setAppId(appId);

        OneSignal.initWithContext(this, appId);

        // Ensure calling requestPermission in a thread right after initWithContext does not crash
        // This will reproduce result similar to Kotlin CouroutineScope.launch{}, which may potentially crash the app
        ExecutorService executor = Executors.newSingleThreadExecutor();
        @SuppressLint({"NewApi", "LocalSuppress"}) CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            OneSignal.getNotifications().requestPermission(true, Continue.none());
        }, executor);
        future.join(); // Waits for the task to complete
        executor.shutdown();

        OneSignal.getInAppMessages().addLifecycleListener(new IInAppMessageLifecycleListener() {
            @Override
            public void onWillDisplay(@NonNull IInAppMessageWillDisplayEvent event) {
                Log.v(Tag.LOG_TAG, "onWillDisplayInAppMessage");
            }

            @Override
            public void onDidDisplay(@NonNull IInAppMessageDidDisplayEvent event) {
                Log.v(Tag.LOG_TAG, "onDidDisplayInAppMessage");
            }

            @Override
            public void onWillDismiss(@NonNull IInAppMessageWillDismissEvent event) {
                Log.v(Tag.LOG_TAG, "onWillDismissInAppMessage");
            }

            @Override
            public void onDidDismiss(@NonNull IInAppMessageDidDismissEvent event) {
                Log.v(Tag.LOG_TAG, "onDidDismissInAppMessage");
            }
        });

        OneSignal.getInAppMessages().addClickListener(new IInAppMessageClickListener() {
            @Override
            public void onClick(@Nullable IInAppMessageClickEvent event) {
                Log.v(Tag.LOG_TAG, "INotificationClickListener.inAppMessageClicked");
            }
        });

        OneSignal.getNotifications().addClickListener(event ->
        {
            Log.v(Tag.LOG_TAG, "INotificationClickListener.onClick fired" +
                    " with event: " + event);
        });

        OneSignal.getNotifications().addForegroundLifecycleListener(new INotificationLifecycleListener() {
            @Override
            public void onWillDisplay(@NonNull INotificationWillDisplayEvent event) {
                Log.v(Tag.LOG_TAG, "INotificationLifecycleListener.onWillDisplay fired" +
                        " with event: " + event);

                IDisplayableNotification notification = event.getNotification();
                JSONObject data = notification.getAdditionalData();

                //Prevent OneSignal from displaying the notification immediately on return. Spin
                //up a new thread to mimic some asynchronous behavior, when the async behavior (which
                //takes 2 seconds) completes, then the notification can be displayed.
                event.preventDefault();
                Runnable r = () -> {
                    try {
                        Thread.sleep(SLEEP_TIME_TO_MIMIC_ASYNC_OPERATION);
                    } catch (InterruptedException ignored) {
                    }

                    notification.display();
                };

                Thread t = new Thread(r);
                t.start();
            }
        });

        OneSignal.getUser().addObserver(new IUserStateObserver() {
            @Override
            public void onUserStateChange(@NonNull UserChangedState state) {
                UserState currentUserState = state.getCurrent();
                Log.v(Tag.LOG_TAG, "onUserStateChange fired " + currentUserState.toJSONObject());
            }
        });

        OneSignal.getInAppMessages().setPaused(true);
        OneSignal.getLocation().setShared(false);

        Log.d(Tag.LOG_TAG, Text.ONESIGNAL_SDK_INIT);
    }

}
