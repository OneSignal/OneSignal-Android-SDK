/**
 * Modified MIT License
 * 
 * Copyright 2015 OneSignal
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * 1. The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * 2. All copies of substantial portions of the Software may only be used in connection
 * with services provided by OneSignal.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.onesignal.example;

import org.json.JSONObject;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

public class BackgroundDataBroadcastReceiver extends BroadcastReceiver {
	
	// This onReceive will be call when a OneSignal Background Data Notification is received(before clicking) by the device.
	// You can read the additionalData and do anything you need here with it.
	// You may consider adding a wake lock here if you need to make sure the devices doesn't go to sleep while processing.
	// The following must also be in your AndroidManifest.xml for this to fire:
	/*
	 <receiver
	    android:name="com.onesignal.example.BackgroundDataBroadcastReceiver"
	    android:exported="false">
		<intent-filter>
	    	<action android:name="com.onesignal.BackgroundBroadcast.RECEIVE" />
	 	</intent-filter>
	 </receiver>
	 
	 Make sure to keep android:exported="false" so other apps can't call can this.
	*/
	@Override
	public void onReceive(Context context, Intent intent) {
		Bundle dataBundle = intent.getBundleExtra("data");
		
		try {
			Log.i("OneSignalExample", "Notification content: " + dataBundle.getString("alert"));
			Log.i("OneSignalExample", "Notification title: " + dataBundle.getString("title"));
			Log.i("OneSignalExample", "Is Your App Active: " + dataBundle.getBoolean("isActive"));
			
			JSONObject customJSON = new JSONObject(dataBundle.getString("custom"));
			if (customJSON.has("a"))
				Log.i("OneSignalExample", "additionalData: " + customJSON.getJSONObject("a").toString());
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}
}