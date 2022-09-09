package com.onesignal.sdktest.model;

import android.app.Activity;
import android.content.Context;
import com.google.android.material.appbar.AppBarLayout;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.widget.NestedScrollView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.Toolbar;

import android.content.Intent;
import android.os.Build;
import android.util.Pair;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;

import com.onesignal.core.Continue;
import com.onesignal.core.OneSignal;
import com.onesignal.notification.IPermissionStateChanges;
import com.onesignal.core.user.subscriptions.IEmailSubscription;
import com.onesignal.core.user.subscriptions.IPushSubscription;
import com.onesignal.core.user.subscriptions.ISmsSubscription;
import com.onesignal.sdktest.R;
import com.onesignal.sdktest.activity.SecondaryActivity;
import com.onesignal.sdktest.adapter.InAppMessageRecyclerViewAdapter;
import com.onesignal.sdktest.adapter.NotificationRecyclerViewAdapter;
import com.onesignal.sdktest.adapter.PairRecyclerViewAdapter;
import com.onesignal.sdktest.adapter.SingleRecyclerViewAdapter;
import com.onesignal.sdktest.callback.AddPairAlertDialogCallback;
import com.onesignal.sdktest.callback.PairItemActionCallback;
import com.onesignal.sdktest.callback.SendOutcomeAlertDialogCallback;
import com.onesignal.sdktest.callback.UpdateAlertDialogCallback;
import com.onesignal.sdktest.constant.Text;
import com.onesignal.sdktest.type.InAppMessage;
import com.onesignal.sdktest.type.Notification;
import com.onesignal.sdktest.type.OutcomeEvent;
import com.onesignal.sdktest.type.ToastType;
import com.onesignal.sdktest.ui.RecyclerViewBuilder;
import com.onesignal.sdktest.util.Animate;
import com.onesignal.sdktest.util.Dialog;
import com.onesignal.sdktest.util.Font;
import com.onesignal.sdktest.util.IntentTo;
import com.onesignal.sdktest.util.SharedPreferenceUtil;
import com.onesignal.sdktest.util.ProfileUtil;
import com.onesignal.sdktest.util.Toaster;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiresApi(api = Build.VERSION_CODES.N)
public class MainActivityViewModel implements ActivityViewModel {

    private Animate animate;
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
    private Button switchUserButton;

    // Alias
    private TextView aliasTitleTextView;
    private RelativeLayout externalUserIdRelativeLayout;
    private TextView externalUserIdTitleTextView;
    private TextView userExternalUserIdTextView;
    private RecyclerView aliasesRecyclerView;
    private PairRecyclerViewAdapter aliasesRecyclerViewAdapter;
    private TextView noAliasesTextView;
    private Button addAliasButton;

    // Email
    private TextView emailHeaderTextView;
    private TextView noEmailsTextView;
    private Button addEmailButton;

    // SMS
    private TextView smsHeaderTextView;
    private TextView noSmssTextView;
    private Button addSMSButton;

    // Tags
    private TextView tagsTitleTextView;
    private TextView noTagsTextView;
    private RecyclerView tagsRecyclerView;
    private PairRecyclerViewAdapter tagPairRecyclerViewAdapter;

    private RecyclerView emailsRecyclerView;
    private SingleRecyclerViewAdapter emailsRecyclerViewAdapter;
    private RecyclerView smssRecyclerView;
    private SingleRecyclerViewAdapter smssRecyclerViewAdapter;

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

    // Push
    private RelativeLayout subscriptionRelativeLayout;
    private TextView subscriptionTextView;
    private TextView subscriptionDescriptionTextView;
    private Switch subscriptionSwitch;
    private Button promptPushButton;
    private RelativeLayout pauseInAppMessagesRelativeLayout;
    private TextView pauseInAppMessagesTextView;
    private TextView pauseInAppMessagesDescriptionTextView;
    private Switch pauseInAppMessagesSwitch;
    private Button revokeConsentButton;

    private boolean shouldScrollTop = false;

    private Context context;

    private HashMap<String, Object> aliasSet;
    private ArrayList<Map.Entry> aliasArrayList;
    private ArrayList<Object> emailArrayList;
    private ArrayList<Object> smsArrayList;

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
        revokeConsentButton = getActivity().findViewById(R.id.main_activity_app_revoke_consent_button);
        switchUserButton = getActivity().findViewById(R.id.main_activity_switch_user_button);

        aliasTitleTextView = getActivity().findViewById(R.id.main_activity_aliases_title_text_view);
        externalUserIdRelativeLayout = getActivity().findViewById(R.id.main_activity_account_details_external_user_id_relative_layout);
        externalUserIdTitleTextView = getActivity().findViewById(R.id.main_activity_account_details_external_user_id_text_view);
        userExternalUserIdTextView = getActivity().findViewById(R.id.main_activity_account_details_user_external_user_id_text_view);
        noAliasesTextView = getActivity().findViewById(R.id.main_activity_aliases_no_aliases_text_view);
        addAliasButton = getActivity().findViewById(R.id.main_activity_add_alias_button);
        aliasesRecyclerView = getActivity().findViewById(R.id.main_activity_aliases_recycler_view);

        emailHeaderTextView = getActivity().findViewById(R.id.main_activity_email_title_text_view);
        noEmailsTextView = getActivity().findViewById(R.id.main_activity_emails_no_emails_text_view);
        addEmailButton = getActivity().findViewById(R.id.main_activity_add_email_button);
        emailsRecyclerView = getActivity().findViewById(R.id.main_activity_emails_recycler_view);

        smsHeaderTextView = getActivity().findViewById(R.id.main_activity_sms_title_text_view);
        noSmssTextView = getActivity().findViewById(R.id.main_activity_smss_no_smss_text_view);
        addSMSButton = getActivity().findViewById(R.id.main_activity_add_sms_button);
        smssRecyclerView = getActivity().findViewById(R.id.main_activity_smss_recycler_view);

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

        subscriptionRelativeLayout = getActivity().findViewById(R.id.main_activity_push_subscription_relative_layout);
        subscriptionTextView = getActivity().findViewById(R.id.main_activity_push_subscription_text_view);
        subscriptionDescriptionTextView = getActivity().findViewById(R.id.main_activity_push_subscription_info_text_view);
        subscriptionSwitch = getActivity().findViewById(R.id.main_activity_push_subscription_switch);
        promptPushButton = getActivity().findViewById(R.id.main_activity_push_prompt_push_button);

        pauseInAppMessagesRelativeLayout = getActivity().findViewById(R.id.main_activity_iam_pause_in_app_messages_relative_layout);
        pauseInAppMessagesTextView = getActivity().findViewById(R.id.main_activity_iam_pause_in_app_messages_text_view);
        pauseInAppMessagesDescriptionTextView = getActivity().findViewById(R.id.main_activity_iam_pause_in_app_messages_info_text_view);
        pauseInAppMessagesSwitch = getActivity().findViewById(R.id.main_activity_iam_pause_in_app_messages_switch);

        Button navigateNextActivity = getActivity().findViewById(R.id.main_activity_navigate_button);
        navigateNextActivity.setOnClickListener(v -> {
            getActivity().startActivity(new Intent(getActivity(), SecondaryActivity.class));
        });

        aliasSet = new HashMap<>();
        aliasArrayList = new ArrayList<>();

        emailArrayList = new ArrayList<>();
        smsArrayList = new ArrayList<>();

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
        font.applyFont(switchUserButton, font.saralaBold);
        font.applyFont(aliasTitleTextView, font.saralaBold);
        font.applyFont(noAliasesTextView, font.saralaBold);
        font.applyFont(emailHeaderTextView, font.saralaBold);
        font.applyFont(noEmailsTextView, font.saralaBold);
        font.applyFont(smsHeaderTextView, font.saralaBold);
        font.applyFont(noSmssTextView, font.saralaBold);
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
        font.applyFont(promptPushButton, font.saralaBold);
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
    public void onPermissionChanged(@Nullable IPermissionStateChanges stateChanges) {
        refreshSubscriptionState();
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
        setupLayout();
        refreshState();
    }

    public void setupLayout() {
        setupScrollView();
        setupAppLayout();
        setupUserLayout();
        setupLocationLayout();
        setupPushNotificationLayout();
        setupInAppMessagingLayout();
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
        revokeConsentButton.setOnClickListener(v -> togglePrivacyConsent(false));
        switchUserButton.setOnClickListener(v -> dialog.createUpdateAlertDialog(OneSignal.getUser().getExternalId(), Dialog.DialogAction.LOGIN, ProfileUtil.FieldType.EXTERNAL_USER_ID, new UpdateAlertDialogCallback() {
            @Override
            public void onSuccess(String update) {
                if(update == null || update.isEmpty()) {
                    OneSignal.logout();
                    switchUserButton.setText(R.string.login_user);
                    refreshState();
                }
                else {
                    OneSignal.login(update, Continue.with(r -> {
                        switchUserButton.setText(R.string.logout_user);
                        refreshState();
                    }));
                }
            }

            @Override
            public void onFailure() {

            }
        }));
    }

    private void setupUserLayout() {
        setupAliasLayout();
        setupEmailLayout();
        setupSMSLayout();
        setupTagsLayout();
        setupOutcomeLayout();
        setupTriggersLayout();
    }

    private void setupAliasLayout() {
        setupAliasesRecyclerView();
        addAliasButton.setOnClickListener(v -> dialog.createAddPairAlertDialog("Add Alias", ProfileUtil.FieldType.ALIAS, new AddPairAlertDialogCallback() {
            @Override
            public void onSuccess(Pair<String, Object> pair) {
                if (pair.second == null || pair.second.toString().isEmpty()) {
                    OneSignal.getUser().removeAlias(pair.first);
                    aliasSet.remove(pair.first);
                    toaster.makeCustomViewToast("Deleted alias " + pair.first, ToastType.SUCCESS);
                } else {
                    OneSignal.getUser().addAlias(pair.first, pair.second.toString());
                    aliasSet.put(pair.first, pair.second);
                    toaster.makeCustomViewToast("Added alias " + pair.first, ToastType.SUCCESS);
                }

                refreshAliasRecyclerView();
            }

            @Override
            public void onFailure() {
                refreshAliasRecyclerView();
            }
        }));
    }

    private void setupAliasesRecyclerView() {
        recyclerViewBuilder.setupRecyclerView(aliasesRecyclerView, 20, false, true);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getActivity());
        aliasesRecyclerView.setLayoutManager(linearLayoutManager);
        aliasesRecyclerViewAdapter = new PairRecyclerViewAdapter(context, aliasArrayList, new PairItemActionCallback() {
            @Override
            public void onLongClick(String key) {
                OneSignal.getUser().removeAlias(key);
                aliasSet.remove(key);
                refreshAliasRecyclerView();
                toaster.makeCustomViewToast("Deleted alias " + key, ToastType.SUCCESS);
            }
        });
        aliasesRecyclerView.setAdapter(aliasesRecyclerViewAdapter);
    }

    private void refreshAliasRecyclerView() {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                aliasArrayList.clear();
                aliasArrayList.addAll(aliasSet.entrySet());

                if (aliasArrayList.size() > 0) {
                    animate.toggleAnimationView(false, View.GONE, aliasesRecyclerView, noAliasesTextView);
                } else {
                    animate.toggleAnimationView(true, View.GONE, aliasesRecyclerView, noAliasesTextView);
                }

                aliasesRecyclerViewAdapter.notifyDataSetChanged();
            }
        });
    }

    private void setupEmailLayout() {
        setupEmailRecyclerView();

        addEmailButton.setOnClickListener(v -> dialog.createUpdateAlertDialog("", Dialog.DialogAction.ADD, ProfileUtil.FieldType.EMAIL, new UpdateAlertDialogCallback() {
            @Override
            public void onSuccess(String value) {
                if (value != null && !value.isEmpty()) {
                    OneSignal.getUser().addEmailSubscription(value.toString());
                    emailArrayList.add(value);
                    toaster.makeCustomViewToast("Added email " + value, ToastType.SUCCESS);
                }

                refreshEmailRecyclerView();
            }

            @Override
            public void onFailure() {
                refreshEmailRecyclerView();
            }
        }));
    }

    private void setupSMSLayout() {
        setupSMSRecyclerView();

        addSMSButton.setOnClickListener(v -> dialog.createUpdateAlertDialog("", Dialog.DialogAction.ADD, ProfileUtil.FieldType.SMS, new UpdateAlertDialogCallback() {
            @Override
            public void onSuccess(String value) {
                if (value != null && !value.isEmpty()) {
                    OneSignal.getUser().addSmsSubscription(value);
                    smsArrayList.add(value);
                    toaster.makeCustomViewToast("Added SMS " + value, ToastType.SUCCESS);
                }

                refreshSMSRecyclerView();
            }

            @Override
            public void onFailure() {
                refreshSMSRecyclerView();
            }
        }));
    }

    private void setupTagsLayout() {
        animate.toggleAnimationView(true, View.GONE, tagsRecyclerView, noTagsTextView);

        setupTagRecyclerView();

        addTagButton.setOnClickListener(v -> dialog.createAddPairAlertDialog("Add Tag", ProfileUtil.FieldType.TAG, new AddPairAlertDialogCallback() {
            @Override
            public void onSuccess(Pair<String, Object> pair) {
                if (pair.second == null || pair.second.toString().isEmpty()) {
                    OneSignal.getUser().removeTag(pair.first);
                    tagSet.remove(pair.first);
                    toaster.makeCustomViewToast("Deleted tag " + pair.first, ToastType.SUCCESS);
                } else {
                    OneSignal.getUser().setTag(pair.first, pair.second.toString());
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
                OneSignal.getUser().removeTag(key);
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

    private void setupEmailRecyclerView() {
        recyclerViewBuilder.setupRecyclerView(emailsRecyclerView, 20, false, true);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getActivity());
        emailsRecyclerView.setLayoutManager(linearLayoutManager);
        emailsRecyclerViewAdapter = new SingleRecyclerViewAdapter(context, emailArrayList, value -> {
            OneSignal.getUser().removeEmailSubscription(value);
            emailArrayList.remove(value);
            refreshEmailRecyclerView();
            toaster.makeCustomViewToast("Deleted email " + value, ToastType.SUCCESS);
        });
        emailsRecyclerView.setAdapter(emailsRecyclerViewAdapter);
    }

    private void refreshEmailRecyclerView() {
        getActivity().runOnUiThread(() -> {
            if (emailArrayList.size() > 0) {
                animate.toggleAnimationView(false, View.GONE, emailsRecyclerView, noEmailsTextView);
            } else {
                animate.toggleAnimationView(true, View.GONE, emailsRecyclerView, noEmailsTextView);
            }

            emailsRecyclerViewAdapter.notifyDataSetChanged();
        });
    }

    private void setupSMSRecyclerView() {
        recyclerViewBuilder.setupRecyclerView(smssRecyclerView, 20, false, true);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getActivity());
        smssRecyclerView.setLayoutManager(linearLayoutManager);
        smssRecyclerViewAdapter = new SingleRecyclerViewAdapter(context, smsArrayList, value -> {
            OneSignal.getUser().removeTrigger(value);
            smsArrayList.remove(value);
            refreshSMSRecyclerView();
            toaster.makeCustomViewToast("Deleted SMS " + value, ToastType.SUCCESS);
        });
        smssRecyclerView.setAdapter(smssRecyclerViewAdapter);
    }

    private void refreshSMSRecyclerView() {
        getActivity().runOnUiThread(() -> {
            if (smsArrayList.size() > 0) {
                animate.toggleAnimationView(false, View.GONE, smssRecyclerView, noSmssTextView);
            } else {
                animate.toggleAnimationView(true, View.GONE, smssRecyclerView, noSmssTextView);
            }

            smssRecyclerViewAdapter.notifyDataSetChanged();
        });
    }

    private void setupOutcomeLayout() {
        sendOutcomeButton.setOnClickListener(v -> dialog.createSendOutcomeAlertDialog("Select an Outcome Type...", new SendOutcomeAlertDialogCallback() {
            @Override
            public boolean onSuccess(OutcomeEvent outcomeEvent, String name, String value) {
                switch (outcomeEvent) {
                    case OUTCOME:
                        OneSignal.getUser().sendOutcome(name);
                        break;
                    case UNIQUE_OUTCOME:
                        OneSignal.getUser().sendUniqueOutcome(name);
                        break;
                    case OUTCOME_WITH_VALUE:
                        if (value.isEmpty()) {
                            toaster.makeCustomViewToast("Please enter an outcome value!", ToastType.ERROR);
                            return false;
                        }

                        OneSignal.getUser().sendOutcomeWithValue(name, Float.parseFloat(value));
                        break;
                }

                return true;
            }

            @Override
            public void onFailure() {

            }
        }));
    }

    private void setupTriggersLayout() {
        animate.toggleAnimationView(true, View.GONE, triggersRecyclerView, noTriggersTextView);

        setupTriggerRecyclerView();
        addTriggerButton.setOnClickListener(v -> dialog.createAddPairAlertDialog("Add Trigger", ProfileUtil.FieldType.TRIGGER, new AddPairAlertDialogCallback() {
            @Override
            public void onSuccess(Pair<String, Object> pair) {
                if (pair.second == null || pair.second.toString().isEmpty()) {
                    OneSignal.getUser().removeTrigger(pair.first);
                    triggerSet.remove(pair.first);
                    toaster.makeCustomViewToast("Deleted trigger " + pair.first, ToastType.SUCCESS);
                } else {
                    OneSignal.getUser().setTrigger(pair.first, pair.second);
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
            OneSignal.getUser().removeTrigger(key);
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

    private void setupLocationLayout() {
        setupLocationSharedSwitch();
        setupPromptLocationButton();
    }

    private void setupLocationSharedSwitch() {
        locationSharedRelativeLayout.setOnClickListener(v -> {
            boolean isLocationShared = !locationSharedSwitch.isChecked();
            locationSharedSwitch.setChecked(isLocationShared);
            SharedPreferenceUtil.cacheLocationSharedStatus(context, isLocationShared);
        });

        boolean isLocationShared = SharedPreferenceUtil.getCachedLocationSharedStatus(context);
        locationSharedSwitch.setChecked(isLocationShared);
        locationSharedSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SharedPreferenceUtil.cacheLocationSharedStatus(context, isChecked);
            OneSignal.getLocation().setLocationShared(isChecked);
        });
    }

    private void setupPromptLocationButton() {
        promptLocationButton.setOnClickListener(v -> {
            OneSignal.getLocation().requestPermission(Continue.none());
        });
    }

    private void setupPushNotificationLayout() {
        setupSubscriptionSwitch();
        setupPromptPushButton();
        setupSendNotificationsLayout();
    }

    private void setupSubscriptionSwitch() {
        refreshSubscriptionState();

        subscriptionRelativeLayout.setOnClickListener(v -> {
            boolean isSubscriptionEnabled = !subscriptionSwitch.isChecked();
            subscriptionSwitch.setChecked(isSubscriptionEnabled);
        });

        // Add a listener to toggle the push notification enablement for the push subscription.
        subscriptionSwitch.setOnClickListener(v -> {
            IPushSubscription subscription = OneSignal.getUser().getSubscriptions().getPush();
            if (subscription != null) {
                subscription.setEnabled(subscriptionSwitch.isChecked());
            }
        });
    }

    private void setupPromptPushButton() {
        promptPushButton.setOnClickListener(v -> {
            OneSignal.getNotifications().requestPermission(Continue.with(r -> refreshSubscriptionState()));
        });
    }

    private void refreshSubscriptionState() {
        boolean isPermissionEnabled = OneSignal.getNotifications().getPermissionStatus().getNotificationsEnabled();
        IPushSubscription pushSubscription = OneSignal.getUser().getSubscriptions().getPush();
        boolean isSubscribed = pushSubscription != null && pushSubscription.getEnabled();

        subscriptionRelativeLayout.setEnabled(isPermissionEnabled);
        subscriptionSwitch.setEnabled(isPermissionEnabled);
        subscriptionSwitch.setChecked(isSubscribed);
    }

    private void setupSendNotificationsLayout() {
        recyclerViewBuilder.setupRecyclerView(pushNotificationRecyclerView, 16, false, true);
        GridLayoutManager gridLayoutManager = new GridLayoutManager(getActivity(), 2);
        pushNotificationRecyclerView.setLayoutManager(gridLayoutManager);

        pushNotificationRecyclerViewAdapter = new NotificationRecyclerViewAdapter(context, Notification.values());
        pushNotificationRecyclerView.setAdapter(pushNotificationRecyclerViewAdapter);
    }

    private void setupInAppMessagingLayout() {
        setupPauseInAppMessagesSwitch();
        setupSendIAMsLayout();
    }

    private void setupPauseInAppMessagesSwitch() {
        pauseInAppMessagesRelativeLayout.setOnClickListener(v -> {
            boolean isInAppMessagesPaused = pauseInAppMessagesSwitch.isChecked();
            pauseInAppMessagesSwitch.setChecked(!isInAppMessagesPaused);
        });

        pauseInAppMessagesSwitch.setChecked(SharedPreferenceUtil.getCachedInAppMessagingPausedStatus(context));
        pauseInAppMessagesSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            OneSignal.getIam().setPaused(isChecked);
            SharedPreferenceUtil.cacheInAppMessagingPausedStatus(context, isChecked);
        });
    }

    private void setupSendIAMsLayout() {
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
        OneSignal.setPrivacyConsent(hasConsent);
        SharedPreferenceUtil.cacheUserPrivacyConsent(context, hasConsent);

        shouldScrollTop = hasConsent;

        int consentVisibility = hasConsent ? View.GONE : View.VISIBLE;
        int scrollVisibility = hasConsent ? View.VISIBLE : View.GONE;
        privacyConsentLinearLayout.setVisibility(consentVisibility);
        nestedScrollView.setVisibility(scrollVisibility);

        appBarLayout.setExpanded(true);
    }



    private  void refreshState() {
        // appId
        appIdTextView.setText(getOneSignalAppId());

        // externalId
        String externalUserId = OneSignal.getUser().getExternalId();
        userExternalUserIdTextView.setText(externalUserId == null ? "" : externalUserId);

        // aliases
        aliasSet.clear();
        for (Map.Entry<String, String> aliasEntry :OneSignal.getUser().getAliases().entrySet()) {
            aliasSet.put(aliasEntry.getKey(), aliasEntry.getValue());
        }
        refreshAliasRecyclerView();

        // email subscriptions
        emailArrayList.clear();
        List<IEmailSubscription> emailSubs = OneSignal.getUser().getSubscriptions().getEmails();
        for (IEmailSubscription emailSub: emailSubs) {
            emailArrayList.add(emailSub.getEmail());
        }
        refreshEmailRecyclerView();

        // sms subscriptions
        smsArrayList.clear();
        List<ISmsSubscription> smsSubs = OneSignal.getUser().getSubscriptions().getSmss();
        for (ISmsSubscription smsSub: smsSubs) {
            smsArrayList.add(smsSub.getNumber());
        }
        refreshSMSRecyclerView();

        // tags
        tagSet.clear();
        for (Map.Entry<String, String> tagEntry :OneSignal.getUser().getTags().entrySet()) {
            tagSet.put(tagEntry.getKey(), tagEntry.getValue());
        }
        refreshTagRecyclerView();

        // triggers
        triggerSet.clear();
        // triggers are not persisted, they are always "starting from scratch"
        refreshTriggerRecyclerView();
    }
}
