package com.onesignal.usersdktest.activity;

import android.os.Build;
import android.os.Bundle;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import com.onesignal.onesignal.OneSignal;
import com.onesignal.usersdktest.R;
import com.onesignal.usersdktest.model.MainActivityViewModel;

@RequiresApi(api = Build.VERSION_CODES.N)
public class MainActivity extends AppCompatActivity {

    private MainActivityViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity_layout);

        viewModel = new MainActivityViewModel();
        OneSignal.getNotifications().addPushPermissionHandler(viewModel);
// TODO("STILL SUPPORT?")
//        OneSignal.addSubscriptionObserver(viewModel);
//        OneSignal.addEmailSubscriptionObserver(viewModel);
        viewModel.onActivityCreated(this)
                .setupInterfaceElements();
    }

    @Override
    public void onBackPressed() {
        if (!viewModel.scrollToTopIfAvailable()) {
            super.onBackPressed();
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        boolean hasConsent = OneSignal.getRequiresPrivacyConsent();
        if (hasConsent)
            viewModel.setupSettingsLayout();
    }
}
