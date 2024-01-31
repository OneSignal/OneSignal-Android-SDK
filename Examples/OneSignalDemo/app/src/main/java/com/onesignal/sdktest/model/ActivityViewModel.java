package com.onesignal.sdktest.model;

import android.app.Activity;
import android.content.Context;
import androidx.appcompat.app.AppCompatActivity;

import com.onesignal.notifications.IPermissionChangedHandler;

/**
 * This is the interface created with a few generic methods for setting a ViewModel
 * as the responsible guardian of an Activity
 */
public interface ActivityViewModel extends IPermissionChangedHandler { // TODO() extends OSPermissionObserver, OSSubscriptionObserver, OSEmailSubscriptionObserver  {

    /**
     * Casts Context of the given Activity to an Activity object
     * @return - Activity used to get to specific methods to the Activity
     */
    Activity getActivity();

    /**
     * Casts Context of the given Activity to an AppCompatActivity object
     * @return - AppCompatActivity used to get to specific methods to the Activity
     */
    AppCompatActivity getAppCompatActivity();

    /**
     * Context is passed in and used to define all of the ui elements across the activity
     * and initialize any other objects that may be used through out the activity
     * @param context - Context context of the given Activity being setup
     * @return - <input>ViewModel implementing the ActivityViewModel
     */
    ActivityViewModel onActivityCreated(Context context);

    /**
     * This method is for calling any setup methods and applying any fonts
     * Called at the end of onCreate strung after onActivityCreated is called
     * @return - <input>ViewModel implementing the ActivityViewModel
     */
    ActivityViewModel setupInterfaceElements();

    /**
     * Some Activities use a Toolbar and this is a generic method for setting it, design it, and controlling it
     * If it is not used, it will be left empty
     */
    void setupToolbar();

    /**
     * Methods for handling network connected and disconnected states
     */
    void networkConnected();
    void networkDisconnected();

}
