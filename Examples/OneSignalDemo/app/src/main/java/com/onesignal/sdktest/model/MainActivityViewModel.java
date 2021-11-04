package com.onesignal.sdktest.model;

import android.app.Activity;
import android.content.Context;
import com.google.android.material.appbar.AppBarLayout;
import androidx.core.widget.NestedScrollView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.Toolbar;
import android.util.Pair;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;

import com.onesignal.OSDeviceState;
import com.onesignal.OSEmailSubscriptionStateChanges;
import com.onesignal.OSPermissionStateChanges;
import com.onesignal.OSSubscriptionStateChanges;
import com.onesignal.OneSignal;
import com.onesignal.sdktest.R;
import com.onesignal.sdktest.adapter.InAppMessageRecyclerViewAdapter;
import com.onesignal.sdktest.adapter.NotificationRecyclerViewAdapter;
import com.onesignal.sdktest.adapter.PairRecyclerViewAdapter;
import com.onesignal.sdktest.callback.AddPairAlertDialogCallback;
import com.onesignal.sdktest.callback.PairItemActionCallback;
import com.onesignal.sdktest.callback.UpdateAlertDialogCallback;
import com.onesignal.sdktest.constant.Text;
import com.onesignal.sdktest.type.InAppMessage;
import com.onesignal.sdktest.type.Notification;
import com.onesignal.sdktest.type.ToastType;
import com.onesignal.sdktest.ui.RecyclerViewBuilder;
import com.onesignal.sdktest.user.CurrentUser;
import com.onesignal.sdktest.util.Animate;
import com.onesignal.sdktest.util.Dialog;
import com.onesignal.sdktest.util.Font;
import com.onesignal.sdktest.util.IntentTo;
import com.onesignal.sdktest.util.SharedPreferenceUtil;
import com.onesignal.sdktest.util.ProfileUtil;
import com.onesignal.sdktest.util.Toaster;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class MainActivityViewModel implements ActivityViewModel {

    private Animate animate;
    private CurrentUser currentUser;
    private Dialog dialog;
    private Font font;
    private IntentTo intentTo;
    private RecyclerViewBuilder recyclerViewBuilder;
    private Toaster toaster;

    private AppBarLayout appBarLayout;
    private Toolbar toolbar;
    private LinearLayout privacyConsentLinearLayout;
    private NestedScrollView nestedScrollView;

    // Privacy Consent
    private TextView privacyConsentTitleTextView;
    private TextView privacyConsentDescriptionTextView;
    private Button privacyConsentAllowButton;

    // App
    private TextView appTitleTextView;
    private RelativeLayout appIdRelativeLayout;
    private TextView appIdTitleTextView;
    private TextView appIdTextView;
    // Email
    private RelativeLayout emailRelativeLayout;
    private TextView emailHeaderTextView;
    private TextView emailTitleTextView;
    private TextView userEmailTextView;
    private Button logoutEmailButton;

    // SMS
    private RelativeLayout smsRelativeLayout;
    private TextView smsHeaderTextView;
    private TextView smsTitleTextView;
    private TextView userSMSTextView;
    private Button logoutSMSButton;

    // External User Id
    private RelativeLayout externalUserIdRelativeLayout;
    private TextView externalUserIdTitleTextView;
    private TextView userExternalUserIdTextView;

    // Tags
    private TextView tagsTitleTextView;
    private TextView noTagsTextView;
    private RecyclerView tagsRecyclerView;
    private PairRecyclerViewAdapter tagPairRecyclerViewAdapter;
    private Button addTagButton;

    // Notification Demo
    private TextView pushNotificationTitleTextView;
    private RecyclerView pushNotificationRecyclerView;
    private NotificationRecyclerViewAdapter pushNotificationRecyclerViewAdapter;

    // Outcomes
    private TextView outcomeTitleTextView;
    private Button sendOutcomeButton;

    // Triggers
    private TextView triggersTitleTextView;
    private TextView noTriggersTextView;
    private RecyclerView triggersRecyclerView;
    private PairRecyclerViewAdapter triggerPairRecyclerViewAdapter;
    private Button addTriggerButton;

    // In App Messaging Demo
    private TextView inAppMessagingTitleTextView;
    private RecyclerView inAppMessagingRecyclerView;
    private InAppMessageRecyclerViewAdapter inAppMessagingRecyclerViewAdapter;

    // Location
    private TextView locationTitleTextView;
    private RelativeLayout locationSharedRelativeLayout;
    private TextView locationSharedTextView;
    private TextView locationSharedDescriptionTextView;
    private Switch locationSharedSwitch;
    private Button promptLocationButton;

    // Settings
    private TextView settingTitleTextView;
    private RelativeLayout subscriptionRelativeLayout;
    private TextView subscriptionTextView;
    private TextView subscriptionDescriptionTextView;
    private Switch subscriptionSwitch;
    private RelativeLayout pauseInAppMessagesRelativeLayout;
    private TextView pauseInAppMessagesTextView;
    private TextView pauseInAppMessagesDescriptionTextView;
    private Switch pauseInAppMessagesSwitch;
    private Button revokeConsentButton;

    private boolean shouldScrollTop = false;

    private Context context;

    private HashMap<String, Object> tagSet;
    private ArrayList<Map.Entry> tagArrayList;

    private HashMap<String, Object> triggerSet;
    private ArrayList<Map.Entry> triggerArrayList;

    @Override
    public Activity getActivity() {
        return (Activity) context;
    }

    @Override
    public AppCompatActivity getAppCompatActivity() {
        return (AppCompatActivity) context;
    }

    @Override
    public ActivityViewModel onActivityCreated(Context context) {
        this.context = context;

        animate = new Animate();
        currentUser = CurrentUser.getInstance();
        dialog = new Dialog(context);
        font = new Font(context);
        intentTo = new IntentTo(context);
        recyclerViewBuilder = new RecyclerViewBuilder(context);
        toaster = new Toaster(context);

        appBarLayout = getActivity().findViewById(R.id.main_activity_app_bar_layout);
        toolbar = getActivity().findViewById(R.id.main_activity_toolbar);
        privacyConsentLinearLayout = getActivity().findViewById(R.id.main_activity_privacy_consent_linear_layout);
        nestedScrollView = getActivity().findViewById(R.id.main_activity_nested_scroll_view);

        privacyConsentTitleTextView = getActivity().findViewById(R.id.main_activity_privacy_consent_title_text_view);
        privacyConsentDescriptionTextView = getActivity().findViewById(R.id.main_activity_privacy_consent_description_text_view);
        privacyConsentAllowButton = getActivity().findViewById(R.id.main_activity_privacy_consent_allow_button);

        appTitleTextView = getActivity().findViewById(R.id.main_activity_account_title_text_view);
        appIdRelativeLayout = getActivity().findViewById(R.id.main_activity_account_details_app_id_relative_layout);
        appIdTitleTextView = getActivity().findViewById(R.id.main_activity_account_details_app_id_title_text_view);
        appIdTextView = getActivity().findViewById(R.id.main_activity_account_details_app_id_text_view);

        emailHeaderTextView = getActivity().findViewById(R.id.main_activity_email_title_text_view);
        emailRelativeLayout = getActivity().findViewById(R.id.main_activity_account_details_email_relative_layout);
        emailTitleTextView = getActivity().findViewById(R.id.main_activity_account_details_email_text_view);
        userEmailTextView = getActivity().findViewById(R.id.main_activity_account_details_user_email_text_view);
        logoutEmailButton = getActivity().findViewById(R.id.main_activity_email_logout_email_button);

        smsHeaderTextView = getActivity().findViewById(R.id.main_activity_sms_title_text_view);
        smsRelativeLayout = getActivity().findViewById(R.id.main_activity_account_details_sms_relative_layout);
        smsTitleTextView = getActivity().findViewById(R.id.main_activity_account_details_sms_text_view);
        userSMSTextView = getActivity().findViewById(R.id.main_activity_account_details_user_sms_text_view);
        logoutSMSButton = getActivity().findViewById(R.id.main_activity_sms_logout_sms_button);

        externalUserIdRelativeLayout = getActivity().findViewById(R.id.main_activity_account_details_external_user_id_relative_layout);
        externalUserIdTitleTextView = getActivity().findViewById(R.id.main_activity_account_details_external_user_id_text_view);
        userExternalUserIdTextView = getActivity().findViewById(R.id.main_activity_account_details_user_external_user_id_text_view);

        tagsTitleTextView = getActivity().findViewById(R.id.main_activity_tags_title_text_view);
        noTagsTextView = getActivity().findViewById(R.id.main_activity_tags_no_tags_text_view);
        tagsRecyclerView = getActivity().findViewById(R.id.main_activity_tags_recycler_view);
        addTagButton = getActivity().findViewById(R.id.main_activity_add_tags_button);

        pushNotificationTitleTextView = getActivity().findViewById(R.id.main_activity_push_notification_title_text_view);
        pushNotificationRecyclerView = getActivity().findViewById(R.id.main_activity_push_notification_recycler_view);

        outcomeTitleTextView = getActivity().findViewById(R.id.main_activity_outcomes_title_text_view);
        sendOutcomeButton = getActivity().findViewById(R.id.main_activity_outcomes_send_outcome_button);

        triggersTitleTextView = getActivity().findViewById(R.id.main_activity_in_app_messages_triggers_title_text_view);
        noTriggersTextView = getActivity().findViewById(R.id.main_activity_in_app_messages_triggers_no_triggers_text_view);
        triggersRecyclerView = getActivity().findViewById(R.id.main_activity_in_app_messages_triggers_recycler_view);
        addTriggerButton = getActivity().findViewById(R.id.main_activity_add_triggers_button);

        inAppMessagingTitleTextView = getActivity().findViewById(R.id.main_activity_in_app_messaging_title_text_view);
        inAppMessagingRecyclerView = getActivity().findViewById(R.id.main_activity_in_app_messaging_recycler_view);

        locationTitleTextView = getActivity().findViewById(R.id.main_activity_location_title_text_view);
        locationSharedRelativeLayout = getActivity().findViewById(R.id.main_activity_location_shared_relative_layout);
        locationSharedTextView = getActivity().findViewById(R.id.main_activity_location_shared_text_view);
        locationSharedDescriptionTextView = getActivity().findViewById(R.id.main_activity_location_shared_info_text_view);
        locationSharedSwitch = getActivity().findViewById(R.id.main_activity_location_shared_switch);
        promptLocationButton = getActivity().findViewById(R.id.main_activity_location_prompt_location_button);

        settingTitleTextView = getActivity().findViewById(R.id.main_activity_settings_title_text_view);
        subscriptionRelativeLayout = getActivity().findViewById(R.id.main_activity_settings_subscription_relative_layout);
        subscriptionTextView = getActivity().findViewById(R.id.main_activity_settings_subscription_text_view);
        subscriptionDescriptionTextView = getActivity().findViewById(R.id.main_activity_settings_subscription_info_text_view);
        subscriptionSwitch = getActivity().findViewById(R.id.main_activity_settings_subscription_switch);
        pauseInAppMessagesRelativeLayout = getActivity().findViewById(R.id.main_activity_settings_pause_in_app_messages_relative_layout);
        pauseInAppMessagesTextView = getActivity().findViewById(R.id.main_activity_settings_pause_in_app_messages_text_view);
        pauseInAppMessagesDescriptionTextView = getActivity().findViewById(R.id.main_activity_settings_pause_in_app_messages_info_text_view);
        pauseInAppMessagesSwitch = getActivity().findViewById(R.id.main_activity_settings_pause_in_app_messages_switch);
        revokeConsentButton = getActivity().findViewById(R.id.main_activity_settings_revoke_consent_button);

        tagSet = new HashMap<>();
        tagArrayList = new ArrayList<>();

        triggerSet = new HashMap<>();
        triggerArrayList = new ArrayList<>();

        return this;
    }

    @Override
    public ActivityViewModel setupInterfaceElements() {
        font.applyFont(appTitleTextView, font.saralaBold);
        font.applyFont(privacyConsentTitleTextView, font.saralaBold);
        font.applyFont(privacyConsentDescriptionTextView, font.saralaRegular);
        font.applyFont(privacyConsentAllowButton, font.saralaBold);
        font.applyFont(appIdTitleTextView, font.saralaBold);
        font.applyFont(appIdTextView, font.saralaRegular);
        font.applyFont(emailHeaderTextView, font.saralaBold);
        font.applyFont(emailTitleTextView, font.saralaBold);
        font.applyFont(userEmailTextView, font.saralaRegular);
        font.applyFont(smsHeaderTextView, font.saralaBold);
        font.applyFont(smsTitleTextView, font.saralaBold);
        font.applyFont(userSMSTextView, font.saralaRegular);
        font.applyFont(externalUserIdTitleTextView, font.saralaBold);
        font.applyFont(userExternalUserIdTextView, font.saralaRegular);
        font.applyFont(tagsTitleTextView, font.saralaBold);
        font.applyFont(noTagsTextView, font.saralaBold);
        font.applyFont(addTagButton, font.saralaBold);
        font.applyFont(pushNotificationTitleTextView, font.saralaBold);
        font.applyFont(outcomeTitleTextView, font.saralaBold);
        font.applyFont(sendOutcomeButton, font.saralaBold);
        font.applyFont(triggersTitleTextView, font.saralaBold);
        font.applyFont(noTriggersTextView, font.saralaBold);
        font.applyFont(addTriggerButton, font.saralaBold);
        font.applyFont(inAppMessagingTitleTextView, font.saralaBold);
        font.applyFont(locationTitleTextView, font.saralaBold);
        font.applyFont(locationSharedTextView, font.saralaBold);
        font.applyFont(locationSharedDescriptionTextView, font.saralaRegular);
        font.applyFont(promptLocationButton, font.saralaBold);
        font.applyFont(settingTitleTextView, font.saralaBold);
        font.applyFont(subscriptionTextView, font.saralaBold);
        font.applyFont(subscriptionDescriptionTextView, font.saralaRegular);
        font.applyFont(pauseInAppMessagesTextView, font.saralaBold);
        font.applyFont(pauseInAppMessagesDescriptionTextView, font.saralaRegular);
        font.applyFont(revokeConsentButton, font.saralaBold);

        boolean hasConsent = SharedPreferenceUtil.getUserPrivacyConsent(context);
        setupConsentLayout(hasConsent);

        if (hasConsent)
            postPrivacyConsentSetup();

        return this;
    }

    @Override
    public void setupToolbar() {
        toolbar.setTitle(Text.EMPTY);
        getAppCompatActivity().setSupportActionBar(toolbar);
    }

    @Override
    public void networkConnected() {

    }

    @Override
    public void networkDisconnected() {

    }

    @Override
    public void onOSEmailSubscriptionChanged(OSEmailSubscriptionStateChanges stateChanges) {

    }

    @Override
    public void onOSPermissionChanged(OSPermissionStateChanges stateChanges) {
        OSDeviceState deviceState = OneSignal.getDeviceState();
        final boolean isSubscribed = deviceState != null && deviceState.isSubscribed();

        boolean isPermissionEnabled = stateChanges.getTo().areNotificationsEnabled();
        subscriptionSwitch.setEnabled(isPermissionEnabled);
        subscriptionSwitch.setChecked(isSubscribed);
    }

    @Override
    public void onOSSubscriptionChanged(OSSubscriptionStateChanges stateChanges) {
        boolean isSubscribed = stateChanges.getTo().isSubscribed();
        subscriptionSwitch.setChecked(isSubscribed);
        SharedPreferenceUtil.cacheLocationSharedStatus(context, isSubscribed);
    }

    private void setupConsentLayout(boolean hasConsent) {
        int consentVisibility = hasConsent ? View.GONE : View.VISIBLE;
        int scrollVisibility = hasConsent ? View.VISIBLE : View.GONE;
        privacyConsentLinearLayout.setVisibility(consentVisibility);
        nestedScrollView.setVisibility(scrollVisibility);
        appBarLayout.setExpanded(true);

        privacyConsentAllowButton.setOnClickListener(v -> {
            togglePrivacyConsent(true);
            postPrivacyConsentSetup();
        });
    }

    private void postPrivacyConsentSetup() {
        setupScrollView();
        setupAppLayout();
        setupTagsLayout();
        setupPushNotificationLayout();
        setupOutcomeLayout();
        setupTriggersLayout();
        setupInAppMessagingLayout();
        setupLocationLayout();
        setupSettingsLayout();
    }

    private void setupScrollView() {
        nestedScrollView.getViewTreeObserver().addOnScrollChangedListener(new ViewTreeObserver.OnScrollChangedListener() {
            @Override
            public void onScrollChanged() {
                int scrollY = nestedScrollView.getScrollY();
                shouldScrollTop = scrollY != 0;
            }
        });
    }

    private void setupAppLayout() {
        appIdTextView.setText(getOneSignalAppId());
        appIdRelativeLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.createUpdateAlertDialog(getOneSignalAppId(), ProfileUtil.FieldType.APP_ID, new UpdateAlertDialogCallback() {
                    @Override
                    public void onSuccess(String update) {
                        appIdTextView.setText(update);
                        SharedPreferenceUtil.cacheOneSignalAppId(getActivity(), update);
                        intentTo.resetApplication();
                    }

                    @Override
                    public void onFailure() {

                    }
                });
            }
        });

        setupEmailButton();
        setupSMSButton();
        setupExternalUserIdButton();
    }

    private void setupEmailButton() {
        boolean isEmailSet = currentUser.isEmailSet();
        String email = isEmailSet ? currentUser.getEmail() : Text.EMAIL_NOT_SET;
        userEmailTextView.setText(email);

        emailRelativeLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.createUpdateAlertDialog(currentUser.getEmail(), ProfileUtil.FieldType.EMAIL, new UpdateAlertDialogCallback() {
                    @Override
                    public void onSuccess(String update) {
                        userEmailTextView.setText(update);
                    }

                    @Override
                    public void onFailure() {

                    }
                });
            }
        });

        logoutEmailButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                OneSignal.logoutEmail(new OneSignal.EmailUpdateHandler() {
                    @Override
                    public void onSuccess() {
                        OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "Logout email ended successfully");
                        MainActivityViewModel.this.getActivity().runOnUiThread(() -> userEmailTextView.setText(""));
                    }

                    @Override
                    public void onFailure(OneSignal.EmailUpdateError error) {
                        OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "Logout email failed with error: " + error);
                    }
                });
            }
        });
    }

    private void setupSMSButton() {
        boolean isSMSNumberSet = currentUser.isSMSNumberSet();
        String smsNumber = isSMSNumberSet ? currentUser.getSMSNumber() : Text.SMS_NOT_SET;
        userSMSTextView.setText(smsNumber);

        smsRelativeLayout.setOnClickListener(v -> dialog.createUpdateAlertDialog(currentUser.getSMSNumber(), ProfileUtil.FieldType.SMS, new UpdateAlertDialogCallback() {
            @Override
            public void onSuccess(String update) {
                userSMSTextView.setText(update);
            }

            @Override
            public void onFailure() {

            }
        }));

        logoutSMSButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                OneSignal.logoutSMSNumber(new OneSignal.OSSMSUpdateHandler() {
                    @Override
                    public void onSuccess(JSONObject result) {
                        OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "Logout SMS ended successfully, result: " + result);
                        MainActivityViewModel.this.getActivity().runOnUiThread(() -> userSMSTextView.setText(""));
                    }

                    @Override
                    public void onFailure(OneSignal.OSSMSUpdateError error) {
                        OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "Logout SMS failed with error: " + error);
                    }
                });
            }
        });
    }

    private void setupExternalUserIdButton() {
        boolean isExternalUserSet = currentUser.isExternalUserIdSet(context);
        String externalUserId = isExternalUserSet ? currentUser.getExternalUserId(context) : Text.EXTERNAL_USER_ID_NOT_SET;
        userExternalUserIdTextView.setText(externalUserId);

        externalUserIdRelativeLayout.setOnClickListener(v -> dialog.createUpdateAlertDialog(currentUser.getExternalUserId(context), ProfileUtil.FieldType.EXTERNAL_USER_ID, new UpdateAlertDialogCallback() {
            @Override
            public void onSuccess(String update) {
                userExternalUserIdTextView.setText(update);
            }

            @Override
            public void onFailure() {

            }
        }));
    }

    private void setupLocationLayout() {
        setupLocationSharedSwitch();
        setupPromptLocationButton();
    }

    private void setupLocationSharedSwitch() {
        locationSharedRelativeLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean isLocationShared = !locationSharedSwitch.isChecked();
                locationSharedSwitch.setChecked(isLocationShared);
                SharedPreferenceUtil.cacheLocationSharedStatus(context, isLocationShared);
            }
        });

        boolean isLocationShared = SharedPreferenceUtil.getCachedLocationSharedStatus(context);
        locationSharedSwitch.setChecked(isLocationShared);
        locationSharedSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                SharedPreferenceUtil.cacheLocationSharedStatus(context, isChecked);
                OneSignal.setLocationShared(isChecked);
            }
        });
    }

    private void setupPromptLocationButton() {
        promptLocationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                OneSignal.promptLocation();
            }
        });
    }

    public void setupSettingsLayout() {
        setupSubscriptionSwitch();
        setupPauseInAppMessagesSwitch();
        setupRevokeConsentButton();
    }

    private void setupSubscriptionSwitch() {
        OSDeviceState deviceState = OneSignal.getDeviceState();
        boolean isPermissionEnabled = deviceState != null && deviceState.areNotificationsEnabled();
        final boolean isSubscribed = deviceState != null && deviceState.isSubscribed();

        subscriptionSwitch.setEnabled(isPermissionEnabled);
        subscriptionSwitch.setChecked(isSubscribed);

        if (isPermissionEnabled) {
            subscriptionRelativeLayout.setOnClickListener(v -> {
                boolean isSubscribed1 = subscriptionSwitch.isChecked();
                subscriptionSwitch.setChecked(!isSubscribed1);
                OneSignal.disablePush(isSubscribed1);
            });
        } else {
            subscriptionRelativeLayout.setOnClickListener(v -> intentTo.notificationPermissions());
        }

        subscriptionSwitch.setOnClickListener(v -> {
            OneSignal.disablePush(!subscriptionSwitch.isChecked());
        });
    }

    private void setupPauseInAppMessagesSwitch() {
        pauseInAppMessagesRelativeLayout.setOnClickListener(v -> {
            boolean isInAppMessagesPaused = pauseInAppMessagesSwitch.isChecked();
            pauseInAppMessagesSwitch.setChecked(!isInAppMessagesPaused);
        });

        pauseInAppMessagesSwitch.setChecked(SharedPreferenceUtil.getCachedInAppMessagingPausedStatus(context));
        pauseInAppMessagesSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            OneSignal.pauseInAppMessages(isChecked);
            SharedPreferenceUtil.cacheInAppMessagingPausedStatus(context, isChecked);
        });
    }

    private void setupRevokeConsentButton() {
        revokeConsentButton.setOnClickListener(v -> togglePrivacyConsent(false));
    }

    private void setupTagsLayout() {
        animate.toggleAnimationView(true, View.GONE, tagsRecyclerView, noTagsTextView);

        setupTagRecyclerView();

        OneSignal.getTags(tags -> {
            if (tags == null || tags.toString().isEmpty())
                return;

            try {
                for (Iterator<String> it = tags.keys(); it.hasNext();) {
                    String key = it.next();
                    tagSet.put(key, tags.get(key));
                }

                refreshTagRecyclerView();

            } catch (JSONException e) {
                e.printStackTrace();
            }
        });

        addTagButton.setOnClickListener(v -> dialog.createAddPairAlertDialog("Add Tag", ProfileUtil.FieldType.TAG, new AddPairAlertDialogCallback() {
            @Override
            public void onSuccess(Pair<String, Object> pair) {
                if (pair.second == null || pair.second.toString().isEmpty()) {
                    OneSignal.deleteTag(pair.first);
                    tagSet.remove(pair.first);
                    toaster.makeCustomViewToast("Deleted tag " + pair.first, ToastType.SUCCESS);
                } else {
                    tagSet.put(pair.first, pair.second);
                    toaster.makeCustomViewToast("Added tag " + pair.first, ToastType.SUCCESS);
                }

                refreshTagRecyclerView();
            }

            @Override
            public void onFailure() {
                refreshTagRecyclerView();
            }
        }));
    }

    private void setupTagRecyclerView() {
        recyclerViewBuilder.setupRecyclerView(tagsRecyclerView, 20, false, true);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getActivity());
        tagsRecyclerView.setLayoutManager(linearLayoutManager);
        tagPairRecyclerViewAdapter = new PairRecyclerViewAdapter(context, tagArrayList, new PairItemActionCallback() {
            @Override
            public void onLongClick(String key) {
                OneSignal.deleteTag(key);
                tagSet.remove(key);

                refreshTagRecyclerView();

                toaster.makeCustomViewToast("Deleted tag " + key, ToastType.SUCCESS);
            }
        });
        tagsRecyclerView.setAdapter(tagPairRecyclerViewAdapter);
    }

    private void refreshTagRecyclerView() {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tagArrayList.clear();
                tagArrayList.addAll(tagSet.entrySet());

                if (tagArrayList.size() > 0) {
                    animate.toggleAnimationView(false, View.GONE, tagsRecyclerView, noTagsTextView);
                } else {
                    animate.toggleAnimationView(true, View.GONE, tagsRecyclerView, noTagsTextView);
                }

                tagPairRecyclerViewAdapter.notifyDataSetChanged();
            }
        });
    }

    private void setupPushNotificationLayout() {
        recyclerViewBuilder.setupRecyclerView(pushNotificationRecyclerView, 16, false, true);
        GridLayoutManager gridLayoutManager = new GridLayoutManager(getActivity(), 2);
        pushNotificationRecyclerView.setLayoutManager(gridLayoutManager);

        pushNotificationRecyclerViewAdapter = new NotificationRecyclerViewAdapter(context, Notification.values());
        pushNotificationRecyclerView.setAdapter(pushNotificationRecyclerViewAdapter);
    }
    private void setupOutcomeLayout() {
        sendOutcomeButton.setOnClickListener(v -> dialog.createSendOutcomeAlertDialog("Select an Outcome Type..."));
    }

    private void setupTriggersLayout() {
        animate.toggleAnimationView(true, View.GONE, triggersRecyclerView, noTriggersTextView);

        setupTriggerRecyclerView();
        addTriggerButton.setOnClickListener(v -> dialog.createAddPairAlertDialog("Add Trigger", ProfileUtil.FieldType.TRIGGER, new AddPairAlertDialogCallback() {
            @Override
            public void onSuccess(Pair<String, Object> pair) {
                if (pair.second == null || pair.second.toString().isEmpty()) {
                    OneSignal.removeTriggerForKey(pair.first);
                    triggerSet.remove(pair.first);
                    toaster.makeCustomViewToast("Deleted trigger " + pair.first, ToastType.SUCCESS);
                } else {
                    triggerSet.put(pair.first, pair.second);
                    toaster.makeCustomViewToast("Added trigger " + pair.first, ToastType.SUCCESS);
                }

                refreshTriggerRecyclerView();
            }

            @Override
            public void onFailure() {
                refreshTriggerRecyclerView();
            }
        }));
    }

    private void setupTriggerRecyclerView() {
        recyclerViewBuilder.setupRecyclerView(triggersRecyclerView, 20, false, true);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getActivity());
        triggersRecyclerView.setLayoutManager(linearLayoutManager);
        triggerPairRecyclerViewAdapter = new PairRecyclerViewAdapter(context, triggerArrayList, key -> {
            OneSignal.removeTriggerForKey(key);
            triggerSet.remove(key);

            refreshTriggerRecyclerView();

            toaster.makeCustomViewToast("Deleted trigger " + key, ToastType.SUCCESS);
        });
        triggersRecyclerView.setAdapter(triggerPairRecyclerViewAdapter);
    }

    private void refreshTriggerRecyclerView() {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                triggerArrayList.clear();
                triggerArrayList.addAll(triggerSet.entrySet());

                if (triggerArrayList.size() > 0) {
                    animate.toggleAnimationView(false, View.GONE, triggersRecyclerView, noTriggersTextView);
                } else {
                    animate.toggleAnimationView(true, View.GONE, triggersRecyclerView, noTriggersTextView);
                }

                triggerPairRecyclerViewAdapter.notifyDataSetChanged();
            }
        });
    }

    private void setupInAppMessagingLayout() {
        recyclerViewBuilder.setupRecyclerView(inAppMessagingRecyclerView, 4, false, true);
        GridLayoutManager gridLayoutManager = new GridLayoutManager(getActivity(), 2);
        inAppMessagingRecyclerView.setLayoutManager(gridLayoutManager);

        inAppMessagingRecyclerViewAdapter = new InAppMessageRecyclerViewAdapter(context, InAppMessage.values());
        inAppMessagingRecyclerView.setAdapter(inAppMessagingRecyclerViewAdapter);
    }

    public boolean scrollToTopIfAvailable() {
        if (shouldScrollTop) {
            if (nestedScrollView != null) {
                nestedScrollView.smoothScrollTo(0, 0);
                appBarLayout.setExpanded(true);
            }
        }
        return shouldScrollTop;
    }

    private String getOneSignalAppId() {
        return SharedPreferenceUtil.getOneSignalAppId(context);
    }

    private void togglePrivacyConsent(boolean hasConsent) {
        OneSignal.provideUserConsent(hasConsent);
        SharedPreferenceUtil.cacheUserPrivacyConsent(context, hasConsent);

        shouldScrollTop = hasConsent;

        int consentVisibility = hasConsent ? View.GONE : View.VISIBLE;
        int scrollVisibility = hasConsent ? View.VISIBLE : View.GONE;
        privacyConsentLinearLayout.setVisibility(consentVisibility);
        nestedScrollView.setVisibility(scrollVisibility);

        appBarLayout.setExpanded(true);
    }

}
