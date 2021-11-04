package com.onesignal.sdktest.activity;

import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;

import com.onesignal.OneSignal;
import com.onesignal.sdktest.R;
import com.onesignal.sdktest.model.MainActivityViewModel;

public class MainActivity extends AppCompatActivity {

    private MainActivityViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity_layout);

        viewModel = new MainActivityViewModel();
        OneSignal.addPermissionObserver(viewModel);
        OneSignal.addSubscriptionObserver(viewModel);
        OneSignal.addEmailSubscriptionObserver(viewModel);
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

        boolean hasConsent = OneSignal.userProvidedPrivacyConsent();
        if (hasConsent)
            viewModel.setupSettingsLayout();
    }
}
