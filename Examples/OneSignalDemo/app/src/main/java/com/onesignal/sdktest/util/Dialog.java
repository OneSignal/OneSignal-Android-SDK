package com.onesignal.sdktest.util;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import androidx.annotation.Nullable;
import com.google.android.material.textfield.TextInputLayout;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.onesignal.OSOutcomeEvent;
import com.onesignal.OneSignal;
import com.onesignal.sdktest.R;
import com.onesignal.sdktest.adapter.EnumSelectionRecyclerViewAdapter;
import com.onesignal.sdktest.callback.AddPairAlertDialogCallback;
import com.onesignal.sdktest.callback.EmailUpdateCallback;
import com.onesignal.sdktest.callback.EnumSelectionCallback;
import com.onesignal.sdktest.callback.UpdateAlertDialogCallback;
import com.onesignal.sdktest.constant.Tag;
import com.onesignal.sdktest.constant.Text;
import com.onesignal.sdktest.type.OutcomeEvent;
import com.onesignal.sdktest.type.ToastType;
import com.onesignal.sdktest.ui.CustomAlertDialogBuilder;
import com.onesignal.sdktest.ui.RecyclerViewBuilder;
import com.onesignal.sdktest.user.CurrentUser;

import org.json.JSONException;
import org.json.JSONObject;

public class Dialog {

    private CurrentUser currentUser;
    private Font font;
    private LayoutInflater layoutInflater;
    private RecyclerViewBuilder recyclerViewBuilder;
    private Toaster toaster;

    private Context context;

    public Dialog(Context context) {
        this.context = context;

        currentUser = CurrentUser.getInstance();
        font = new Font(context);
        layoutInflater = LayoutInflater.from(context);
        recyclerViewBuilder = new RecyclerViewBuilder(context);
        toaster = new Toaster(context);
    }

    /**
     * Create an AlertDialog for when the user updates a single value field
     * Click OK to verify and update the field being updated
     */
    public void createUpdateAlertDialog(final String content, final ProfileUtil.FieldType field, final UpdateAlertDialogCallback callback) {
        View updateAlertDialogView = layoutInflater.inflate(R.layout.update_alert_dialog_layout, null, false);

        final TextInputLayout updateAlertDialogTextInputLayout = updateAlertDialogView.findViewById(R.id.update_alert_dialog_text_input_layout);
        final EditText updateAlertDialogEditText = updateAlertDialogView.findViewById(R.id.update_alert_dialog_edit_text);
        final ProgressBar updateAlertDialogProgressBar = updateAlertDialogView.findViewById(R.id.update_alert_dialog_progress_bar);

        String hintTitle = "New " + field.getTitle();
        updateAlertDialogTextInputLayout.setHint(hintTitle);
        updateAlertDialogEditText.setText(content);

        font.applyFont(updateAlertDialogTextInputLayout, font.saralaBold);
        font.applyFont(updateAlertDialogEditText, font.saralaBold);

        final CustomAlertDialogBuilder updateAlertDialog = new CustomAlertDialogBuilder(context, updateAlertDialogView);
        updateAlertDialog.setView(updateAlertDialogView);
        updateAlertDialog.setIsCancelable(true);
        updateAlertDialog.setCanceledOnTouchOutside(false);
        updateAlertDialog.setPositiveButton(Text.BUTTON_UPDATE, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog, int which) {
                toggleUpdateAlertDialogAttributes(true);

                final String newContent = updateAlertDialogEditText.getText().toString().trim();

                if (newContent.equals(content)) {
                    InterfaceUtil.hideKeyboardFrom(context, updateAlertDialogEditText);

                    toggleUpdateAlertDialogAttributes(false);
                    dialog.dismiss();
                } else if (ProfileUtil.isContentValid(field, updateAlertDialogTextInputLayout)) {
                    InterfaceUtil.hideKeyboardFrom(context, updateAlertDialogEditText);

                    switch (field) {
                        case APP_ID:
                            updateAppId(dialog, newContent);
                            break;

                        case EMAIL:
                            updateEmail(dialog, newContent);
                            break;

                        case EXTERNAL_USER_ID:
                            updateExternalUserId(dialog, newContent);
                            break;
                    }
                } else {
                    toggleUpdateAlertDialogAttributes(false);
                }
            }

            private void toggleUpdateAlertDialogAttributes(boolean disableAttributes) {
                int progressVisibility = disableAttributes ? View.VISIBLE : View.GONE;
                updateAlertDialogProgressBar.setVisibility(progressVisibility);

                int buttonVisibility = disableAttributes ? View.GONE : View.VISIBLE;
                updateAlertDialog.getPositiveButtonElement().setVisibility(buttonVisibility);
                updateAlertDialog.getNegativeButtonElement().setVisibility(buttonVisibility);

                updateAlertDialog.getPositiveButtonElement().setEnabled(!disableAttributes);
                updateAlertDialog.getNegativeButtonElement().setEnabled(!disableAttributes);
                updateAlertDialog.setIsCancelable(!disableAttributes);
            }

            /**
             * Handles changing the app id for the SDK by reinitializing and caching
             */
            private void updateAppId(DialogInterface dialog, String appId) {
                OneSignal.setAppId(appId);
                SharedPreferenceUtil.cacheOneSignalAppId(context, appId);

                toggleUpdateAlertDialogAttributes(false);
                dialog.dismiss();
                callback.onSuccess(appId);
            }

            /**
             * Updates the email attached to the device and caches
             */
            private void updateEmail(final DialogInterface dialog, final String email) {
                currentUser.setEmail(email, new EmailUpdateCallback() {
                    @Override
                    public void onSuccess() {
                        SharedPreferenceUtil.cacheUserEmail(context, email);
                        Log.d(Tag.DEBUG, Text.EMAIL_SET_SUCCESSFULLY);

                        ((Activity) context).runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                toggleUpdateAlertDialogAttributes(false);

                                dialog.dismiss();
                                callback.onSuccess(email);
                            }
                        });
                    }

                    @Override
                    public void onFailure() {
                        Log.d(Tag.ERROR, Text.EMAIL_SET_FAILURE);

                        ((Activity) context).runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                toggleUpdateAlertDialogAttributes(false);

                                dialog.dismiss();
                                callback.onFailure();
                            }
                        });
                    }
                });
            }

            /**
             * Set external id attached to the user/email of the device
             */
            private void updateExternalUserId(final DialogInterface dialog, final String externalUserId) {
                OneSignal.setExternalUserId(externalUserId, new OneSignal.OSExternalUserIdUpdateCompletionHandler() {
                    @Override
                    public void onSuccess(JSONObject results) {
                        // Default success to false until we know push came back successful
                        boolean successful = false;

                        // Check push exists with success status and success status is true
                        if (isExternalUserIdPushSuccessful(results)) {
                            OneSignal.onesignalLog(OneSignal.LOG_LEVEL.VERBOSE, "Push channel external user id set successfully");
                            SharedPreferenceUtil.cacheUserExternalUserId(context, externalUserId);
                            successful = true;
                        }

                        // Check email exists with success status and success status is true
                        if (isExternalUserIdEmailSuccessful(results)) {
                            OneSignal.onesignalLog(OneSignal.LOG_LEVEL.VERBOSE, "Email channel external user id set successfully");
                        }

                        // We base success on the push success existing and being true, call success callback for AlertDialog callback
                        // We could eventually check email also but not important for now
                        if (successful)
                            callback.onSuccess(externalUserId);
                        else
                            callback.onFailure();

                        toggleUpdateAlertDialogAttributes(false);
                        dialog.dismiss();
                    }

                    @Override
                    public void onFailure(OneSignal.ExternalIdError error) {
                        OneSignal.onesignalLog(OneSignal.LOG_LEVEL.VERBOSE, "External user id set failed with error: " + error);
                        callback.onFailure();
                        dialog.dismiss();
                    }

                    /**
                     * Parse the results of the external user id completion callback and make sure push.success = true
                     */
                    private boolean isExternalUserIdPushSuccessful(JSONObject status) {
                        boolean successful = false;
                        try {
                            if (!status.has("push"))
                                return false;

                            JSONObject pushStatus = status.getJSONObject("push");
                            if (!pushStatus.has("success"))
                                return false;

                            successful = pushStatus.getBoolean("success");
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        return successful;
                    }

                    /**
                     * Parse the results of the external user id completion callback and make sure email.success = true
                     */
                    private boolean isExternalUserIdEmailSuccessful(JSONObject status) {
                        boolean successful = false;
                        try {
                            if (!status.has("email"))
                                return false;

                            JSONObject emailStatus = status.getJSONObject("email");
                            if (!emailStatus.has("success"))
                                return false;

                            successful = emailStatus.getBoolean("success");
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        return successful;
                    }
                });
            }

        }).setNegativeButton(Text.BUTTON_CANCEL, null);
        updateAlertDialog.show();
        updateAlertDialogEditText.requestFocus();
    }

    /**
     * Create an AlertDialog for when the user updates a single value field
     * Click OK to verify and update the field being updated
     */
    public void createAddPairAlertDialog(String content, final ProfileUtil.FieldType field, final AddPairAlertDialogCallback callback) {
        final View addPairAlertDialogView = layoutInflater.inflate(R.layout.add_pair_alert_dialog_layout, null, false);

        final TextView addPairAlertDialogTitleTextView = addPairAlertDialogView.findViewById(R.id.add_pair_alert_dialog_title_text_view);
        final TextInputLayout addPairAlertDialogKeyTextInputLayout = addPairAlertDialogView.findViewById(R.id.add_pair_alert_dialog_key_text_input_layout);
        final EditText addPairAlertDialogKeyEditText = addPairAlertDialogView.findViewById(R.id.add_pair_alert_dialog_key_edit_text);
        final TextInputLayout addPairAlertDialogValueTextInputLayout = addPairAlertDialogView.findViewById(R.id.add_pair_alert_dialog_value_text_input_layout);
        final EditText addPairAlertDialogValueEditText = addPairAlertDialogView.findViewById(R.id.add_pair_alert_dialog_value_edit_text);
        final ProgressBar addPairAlertDialogProgressBar = addPairAlertDialogView.findViewById(R.id.add_pair_alert_dialog_progress_bar);

        addPairAlertDialogKeyTextInputLayout.setHint("Key");
        addPairAlertDialogValueTextInputLayout.setHint("Value");
        addPairAlertDialogTitleTextView.setText(content);

        font.applyFont(addPairAlertDialogTitleTextView, font.saralaBold);
        font.applyFont(addPairAlertDialogKeyTextInputLayout, font.saralaBold);
        font.applyFont(addPairAlertDialogKeyEditText, font.saralaBold);
        font.applyFont(addPairAlertDialogValueTextInputLayout, font.saralaBold);
        font.applyFont(addPairAlertDialogValueEditText, font.saralaBold);

        final CustomAlertDialogBuilder addPairAlertDialog = new CustomAlertDialogBuilder(context, addPairAlertDialogView);
        addPairAlertDialog.setView(addPairAlertDialogView);
        addPairAlertDialog.setIsCancelable(true);
        addPairAlertDialog.setCanceledOnTouchOutside(false);
        addPairAlertDialog.setPositiveButton(Text.BUTTON_ADD, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog, int which) {
                toggleUpdateAlertDialogAttributes(true);

                final String pairKey = addPairAlertDialogKeyEditText.getText().toString().trim();
                final String pairStringValue = addPairAlertDialogValueEditText.getText().toString().trim();

                Object pairValue = pairStringValue;
                if (Util.isBoolean(pairStringValue)) {
                    pairValue = Boolean.parseBoolean(pairStringValue.toLowerCase());
                } else if (Util.isNumeric(pairStringValue)) {
                    pairValue = Double.parseDouble(pairStringValue);
                }

                if (ProfileUtil.isContentValid(field, addPairAlertDialogKeyTextInputLayout)) {
                    InterfaceUtil.hideKeyboardFrom(context, addPairAlertDialogView);

                    switch (field) {
                        case TAG:
                            OneSignal.sendTag(pairKey, pairStringValue);
                            break;

                        case TRIGGER:
                            OneSignal.addTrigger(pairKey, pairValue);
                            break;
                    }

                    dialog.dismiss();
                    callback.onSuccess(new Pair<>(pairKey, pairValue));
                } else {
                    toggleUpdateAlertDialogAttributes(false);
                }
            }

            private void toggleUpdateAlertDialogAttributes(boolean disableAttributes) {
                int progressVisibility = disableAttributes ? View.VISIBLE : View.GONE;
                addPairAlertDialogProgressBar.setVisibility(progressVisibility);

                int buttonVisibility = disableAttributes ? View.GONE : View.VISIBLE;
                addPairAlertDialog.getPositiveButtonElement().setVisibility(buttonVisibility);
                addPairAlertDialog.getNegativeButtonElement().setVisibility(buttonVisibility);

                addPairAlertDialog.getPositiveButtonElement().setEnabled(!disableAttributes);
                addPairAlertDialog.getNegativeButtonElement().setEnabled(!disableAttributes);
                addPairAlertDialog.setIsCancelable(!disableAttributes);
            }

        }).setNegativeButton(Text.BUTTON_CANCEL, null);
        addPairAlertDialog.show();
        addPairAlertDialogKeyEditText.requestFocus();
    }

    public void createSendOutcomeAlertDialog(final String content) {
        final View sendOutcomeAlertDialogView = layoutInflater.inflate(R.layout.send_outcome_alert_dialog_layout, null, false);

        final CardView sendOutcomeDialogTitleCardView = sendOutcomeAlertDialogView.findViewById(R.id.send_outcome_alert_dialog_selection_card_view);
        final RelativeLayout sendOutcomeDialogTitleRelativeLayout = sendOutcomeAlertDialogView.findViewById(R.id.send_outcome_alert_dialog_selection_relative_layout);
        final TextView sendOutcomeDialogTitleTextView = sendOutcomeAlertDialogView.findViewById(R.id.send_outcome_alert_dialog_selection_text_view);
        final ImageView sendOutcomeDialogTitleArrowImageView = sendOutcomeAlertDialogView.findViewById(R.id.send_outcome_alert_dialog_selection_arrow_image_view);
        final RecyclerView sendOutcomeDialogSelectionRecyclerView = sendOutcomeAlertDialogView.findViewById(R.id.send_outcome_alert_dialog_selection_recycler_view);
        final LinearLayout sendOutcomeDialogSelectionContentLinearLayout = sendOutcomeAlertDialogView.findViewById(R.id.send_outcome_alert_dialog_content_linear_layout);
        final TextInputLayout sendOutcomeDialogNameTextInputLayout = sendOutcomeAlertDialogView.findViewById(R.id.send_outcome_alert_dialog_name_text_input_layout);
        final EditText sendOutcomeDialogNameEditText = sendOutcomeAlertDialogView.findViewById(R.id.send_outcome_alert_dialog_name_edit_text);
        final TextInputLayout sendOutcomeDialogValueTextInputLayout = sendOutcomeAlertDialogView.findViewById(R.id.send_outcome_alert_dialog_value_text_input_layout);
        final EditText sendOutcomeDialogValueEditText = sendOutcomeAlertDialogView.findViewById(R.id.send_outcome_alert_dialog_value_edit_text);
        final ProgressBar sendOutcomeDialogProgressBar = sendOutcomeAlertDialogView.findViewById(R.id.send_outcome_alert_dialog_progress_bar);

        sendOutcomeDialogNameTextInputLayout.setHint("Name");
        sendOutcomeDialogValueTextInputLayout.setHint("Value");
        sendOutcomeDialogTitleTextView.setText(content);

        font.applyFont(sendOutcomeDialogTitleTextView, font.saralaBold);
        font.applyFont(sendOutcomeDialogNameTextInputLayout, font.saralaBold);
        font.applyFont(sendOutcomeDialogValueTextInputLayout, font.saralaBold);

        sendOutcomeDialogTitleCardView.setCardElevation(8f);

        recyclerViewBuilder.setupRecyclerView(sendOutcomeDialogSelectionRecyclerView, 3, false, true);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(context);
        sendOutcomeDialogSelectionRecyclerView.setLayoutManager(linearLayoutManager);
        EnumSelectionRecyclerViewAdapter enumSelectionRecyclerViewAdapter = new EnumSelectionRecyclerViewAdapter(context, OutcomeEvent.values(), new EnumSelectionCallback() {
            @Override
            public void onSelection(String title) {
                int nameVisibility = View.GONE;
                int valueVisibility = View.GONE;

                OutcomeEvent outcomeEvent = OutcomeEvent.enumFromTitleString(title);
                if (outcomeEvent == null) {
                    Drawable arrow = context.getResources().getDrawable(R.drawable.ic_chevron_down_white_48dp);
                    sendOutcomeDialogTitleArrowImageView.setImageDrawable(arrow);
                    sendOutcomeDialogTitleCardView.setCardElevation(0f);
                    sendOutcomeDialogSelectionRecyclerView.setVisibility(View.GONE);
                    sendOutcomeDialogSelectionContentLinearLayout.setVisibility(View.GONE);

                    sendOutcomeDialogNameEditText.setVisibility(nameVisibility);
                    sendOutcomeDialogValueTextInputLayout.setVisibility(valueVisibility);
                    return;
                }

                switch(outcomeEvent) {
                    case OUTCOME:
                    case UNIQUE_OUTCOME:
                        nameVisibility = View.VISIBLE;
                        break;
                    case OUTCOME_WITH_VALUE:
                        nameVisibility = View.VISIBLE;
                        valueVisibility = View.VISIBLE;
                        break;
                }

                sendOutcomeDialogTitleTextView.setText(outcomeEvent.getTitle());

                Drawable arrow = context.getResources().getDrawable(R.drawable.ic_chevron_down_white_48dp);
                sendOutcomeDialogTitleArrowImageView.setImageDrawable(arrow);
                sendOutcomeDialogTitleCardView.setCardElevation(0f);
                sendOutcomeDialogSelectionRecyclerView.setVisibility(View.GONE);

                sendOutcomeDialogSelectionContentLinearLayout.setVisibility(View.VISIBLE);
                sendOutcomeDialogNameTextInputLayout.setVisibility(nameVisibility);
                sendOutcomeDialogNameEditText.setVisibility(nameVisibility);
                sendOutcomeDialogValueTextInputLayout.setVisibility(valueVisibility);
                sendOutcomeDialogValueEditText.setVisibility(valueVisibility);
            }
        });
        sendOutcomeDialogSelectionRecyclerView.setAdapter(enumSelectionRecyclerViewAdapter);

        sendOutcomeDialogTitleRelativeLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean showMenu = sendOutcomeDialogSelectionRecyclerView.getVisibility() == View.GONE;
                Drawable arrow = context.getResources().getDrawable(showMenu ? R.drawable.ic_chevron_up_white_48dp : R.drawable.ic_chevron_down_white_48dp);
                int menuVisibility = showMenu ? View.VISIBLE : View.GONE;
                int contentVisibility = showMenu ? View.GONE : View.VISIBLE;
                float shadow = showMenu ? 8f : 0f;

                sendOutcomeDialogTitleArrowImageView.setImageDrawable(arrow);
                sendOutcomeDialogTitleCardView.setCardElevation(shadow);
                sendOutcomeDialogSelectionRecyclerView.setVisibility(menuVisibility);
                sendOutcomeDialogSelectionContentLinearLayout.setVisibility(contentVisibility);


                int nameVisibility = View.GONE;
                int valueVisibility = View.GONE;

                String selectedTitle = sendOutcomeDialogTitleTextView.getText().toString();
                OutcomeEvent outcomeEvent = OutcomeEvent.enumFromTitleString(selectedTitle);

                if (outcomeEvent == null) {
                    sendOutcomeDialogSelectionContentLinearLayout.setVisibility(View.GONE);
                    return;
                }

                if (!showMenu) {
                    switch (outcomeEvent) {
                        case OUTCOME:
                        case UNIQUE_OUTCOME:
                            nameVisibility = View.VISIBLE;
                            break;
                        case OUTCOME_WITH_VALUE:
                            nameVisibility = View.VISIBLE;
                            valueVisibility = View.VISIBLE;
                            break;
                    }
                }

                sendOutcomeDialogSelectionContentLinearLayout.setVisibility(nameVisibility);
                sendOutcomeDialogNameEditText.setVisibility(nameVisibility);
                sendOutcomeDialogValueTextInputLayout.setVisibility(valueVisibility);
                sendOutcomeDialogValueEditText.setVisibility(valueVisibility);
            }
        });

        final CustomAlertDialogBuilder sendOutcomeAlertDialog = new CustomAlertDialogBuilder(context, sendOutcomeAlertDialogView);
        sendOutcomeAlertDialog.setView(sendOutcomeAlertDialogView);
        sendOutcomeAlertDialog.setIsCancelable(true);
        sendOutcomeAlertDialog.setCanceledOnTouchOutside(false);
        sendOutcomeAlertDialog.setPositiveButton(Text.BUTTON_SEND, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(final DialogInterface dialog, int which) {
                toggleUpdateAlertDialogAttributes(true);

                String selectedTitle = sendOutcomeDialogTitleTextView.getText().toString();
                OutcomeEvent outcomeEvent = OutcomeEvent.enumFromTitleString(selectedTitle);

                if (outcomeEvent == null) {
                    toaster.makeCustomViewToast("Please select an outcome type!", ToastType.ERROR);
                    toggleUpdateAlertDialogAttributes(false);
                    return;
                }

                String name = sendOutcomeDialogNameEditText.getText().toString().trim();
                String value = sendOutcomeDialogValueEditText.getText().toString().trim();

                if (name.isEmpty()) {
                    toaster.makeCustomViewToast("Please enter an outcome name!", ToastType.ERROR);
                    toggleUpdateAlertDialogAttributes(false);
                    return;
                }

                switch (outcomeEvent) {
                    case OUTCOME:
                        OneSignal.sendOutcome(name, new OneSignal.OutcomeCallback() {
                            @Override
                            public void onSuccess(@Nullable OSOutcomeEvent outcomeEvent) {
                                ((Activity) context).runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        toggleUpdateAlertDialogAttributes(false);
                                        dialog.dismiss();
                                    }
                                });
                            }
                        });
                        break;
                    case UNIQUE_OUTCOME:
                        OneSignal.sendUniqueOutcome(name, new OneSignal.OutcomeCallback() {
                            @Override
                            public void onSuccess(@Nullable OSOutcomeEvent outcomeEvent) {
                                ((Activity) context).runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        toggleUpdateAlertDialogAttributes(false);
                                        dialog.dismiss();
                                    }
                                });
                            }
                        });
                        break;
                    case OUTCOME_WITH_VALUE:
                        if (value.isEmpty()) {
                            toaster.makeCustomViewToast("Please enter an outcome value!", ToastType.ERROR);
                            toggleUpdateAlertDialogAttributes(false);
                            return;
                        }

                        OneSignal.sendOutcomeWithValue(name, Float.parseFloat(value), new OneSignal.OutcomeCallback() {
                            @Override
                            public void onSuccess(@Nullable OSOutcomeEvent outcomeEvent) {
                                ((Activity) context).runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        toggleUpdateAlertDialogAttributes(false);
                                        dialog.dismiss();
                                    }
                                });
                            }
                        });
                        break;
                }

                InterfaceUtil.hideKeyboardFrom(context, sendOutcomeAlertDialogView);
            }

            private void toggleUpdateAlertDialogAttributes(boolean disableAttributes) {
                int progressVisibility = disableAttributes ? View.VISIBLE : View.GONE;
                sendOutcomeDialogProgressBar.setVisibility(progressVisibility);

                int buttonVisibility = disableAttributes ? View.GONE : View.VISIBLE;
                sendOutcomeAlertDialog.getPositiveButtonElement().setVisibility(buttonVisibility);
                sendOutcomeAlertDialog.getNegativeButtonElement().setVisibility(buttonVisibility);

                sendOutcomeAlertDialog.getPositiveButtonElement().setEnabled(!disableAttributes);
                sendOutcomeAlertDialog.getNegativeButtonElement().setEnabled(!disableAttributes);
                sendOutcomeAlertDialog.setIsCancelable(!disableAttributes);
            }

        }).setNegativeButton(Text.BUTTON_CANCEL, null);
        sendOutcomeAlertDialog.show();
    }

}
