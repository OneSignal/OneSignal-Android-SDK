package com.onesignal.sdktest.activity;

import android.os.Build;
import android.os.Bundle;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import com.onesignal.OneSignal;
import com.onesignal.sdktest.R;
import com.onesignal.sdktest.model.MainActivityViewModel;

@RequiresApi(api = Build.VERSION_CODES.N)
public class MainActivity extends AppCompatActivity {

    private MainActivityViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity_layout);

        viewModel = new MainActivityViewModel();
        OneSignal.getNotifications().addPermissionObserver(viewModel);
// TODO("STILL SUPPORT?")
//        OneSignal.addSubscriptionObserver(viewModel);
//        OneSignal.addEmailSubscriptionObserver(viewModel);
        viewModel.onActivityCreated(this)
                .setupInterfaceElements();
// TODO: Login and add trigger here
//        OneSignal.login("nan");
//        OneSignal.getInAppMessages().addTrigger("fruit", "apple");
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

        boolean hasConsent = OneSignal.getConsentGiven();
        if (hasConsent)
            viewModel.setupLayout();
    }
}
