package com.onesignal.sdktest.activity;

import android.os.Bundle;
import com.google.android.material.appbar.AppBarLayout;
import androidx.core.widget.NestedScrollView;
import androidx.appcompat.app.AppCompatActivity;

import com.onesignal.sdktest.R;
import com.onesignal.sdktest.model.ActivityViewModel;
import com.onesignal.sdktest.model.MainActivityViewModel;
import com.onesignal.sdktest.util.OneSignalPrefs;

public class MainActivity extends AppCompatActivity {

    private ActivityViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity_layout);

        viewModel = new MainActivityViewModel()
                .onActivityCreated(this)
                .setupInterfaceElements();
    }

    @Override
    public void onBackPressed() {
        if (((MainActivityViewModel) viewModel).shouldScrollToTop()) {
            NestedScrollView nestedScrollView = viewModel.getActivity().findViewById(R.id.main_activity_nested_scroll_view);
            AppBarLayout appBarLayout = viewModel.getActivity().findViewById(R.id.main_activity_app_bar_layout);
            if (nestedScrollView != null) {
                nestedScrollView.smoothScrollTo(0, 0);
                appBarLayout.setExpanded(true);
            }
        } else {
            super.onBackPressed();
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        boolean hasConsent = OneSignalPrefs.getUserPrivacyConsent(MainActivity.this);
        if (hasConsent)
            ((MainActivityViewModel) viewModel).setupSettingsLayout();
    }
}
