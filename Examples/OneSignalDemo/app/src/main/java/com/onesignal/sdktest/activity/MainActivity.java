package com.onesignal.test.android.activity;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.onesignal.OneSignal;
import com.onesignal.test.android.R;
import com.onesignal.test.android.model.MainActivityViewModel;

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
