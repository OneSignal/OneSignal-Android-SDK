package com.onesignal.example;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

//import com.google.firebase.messaging.FirebaseMessagingService;
//import com.google.firebase.messaging.RemoteMessage;
//
//import java.util.Map;
//
//public class MyFcmListenerService extends FirebaseMessagingService {
//   @Override
//   public void onMessageReceived(RemoteMessage message) {
//      Log.e("OneSignalExample", "MyFcmListenerService:onMessageReceived!!!!");
//      String from = message.getFrom();
//      Map data = message.getData();
//   }
//}


public class MyFcmListenerService extends Service {

   @Nullable
   @Override
   public IBinder onBind(Intent intent) {
      return null;
   }
}