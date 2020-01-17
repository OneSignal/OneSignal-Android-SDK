package com.onesignal.sdktest.activity;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.onesignal.sdktest.R;
import com.onesignal.sdktest.model.ActivityViewModel;
import com.onesignal.sdktest.model.SplashActivityViewModel;
import com.onesignal.sdktest.util.IntentTo;

public class SplashActivity extends AppCompatActivity {

    private IntentTo intentTo;

    private ActivityViewModel viewModel;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.splash_activity_layout);

        intentTo = new IntentTo(this);

        viewModel = new SplashActivityViewModel()
                .onActivityCreated(this)
                .setupInterfaceElements();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }

}
