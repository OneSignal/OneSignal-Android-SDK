package com.onesignal.example;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class BlankActivity extends Activity {

   @Override
   protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      System.out.println("BlankActivity onCreate!!!!");
   }

   @Override
   protected void onNewIntent(Intent intent) {
      System.out.println("BlankActivity onNewIntent!!!!1");
      super.onNewIntent(intent);
      System.out.println("BlankActivity onNewIntent!!!!2");
   }
}