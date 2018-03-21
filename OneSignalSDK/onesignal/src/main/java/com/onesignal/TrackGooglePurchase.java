/**
 * Modified MIT License
 * 
 * Copyright 2016 OneSignal
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

package com.onesignal;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;

class TrackGooglePurchase {

   static private int iapEnabled = -99;
   private ServiceConnection mServiceConn;
   private static Class<?> IInAppBillingServiceClass;
   private Object mIInAppBillingService;
   private Method getPurchasesMethod, getSkuDetailsMethod;
   private Context appContext;

   private ArrayList<String> purchaseTokens;

   // Any new purchases found count as pre-existing.
   // The constructor sets it to false if we already saved any purchases or already found out there isn't any.
   private boolean newAsExisting = true;
   private boolean isWaitingForPurchasesRequest = false;

   TrackGooglePurchase(Context activity) {
      appContext = activity;

      purchaseTokens = new ArrayList<>();
      try {
         String purchaseTokensString = OneSignalPrefs.getString(OneSignalPrefs.PREFS_PLAYER_PURCHASES,
                 OneSignalPrefs.PREFS_PURCHASE_TOKENS,"[]");

         JSONArray jsonPurchaseTokens = new JSONArray(purchaseTokensString);
         for (int i = 0; i < jsonPurchaseTokens.length(); i++)
            purchaseTokens.add(jsonPurchaseTokens.get(i).toString());
         newAsExisting = (jsonPurchaseTokens.length() == 0);
         if (newAsExisting)
            newAsExisting = OneSignalPrefs.getBool(OneSignalPrefs.PREFS_PLAYER_PURCHASES,
                    OneSignalPrefs.PREFS_EXISTING_PURCHASES, true);
      } catch (JSONException e) {
         e.printStackTrace();
      }

      trackIAP();
   }

   static boolean CanTrack(Context context) {
      if (iapEnabled == -99)
         iapEnabled = context.checkCallingOrSelfPermission("com.android.vending.BILLING");
      try {
         if (iapEnabled == PackageManager.PERMISSION_GRANTED)
            IInAppBillingServiceClass = Class.forName("com.android.vending.billing.IInAppBillingService");
      } catch (Throwable t) {
         iapEnabled = 0;
         return false;
      }

      return (iapEnabled == PackageManager.PERMISSION_GRANTED);
   }

   void trackIAP() {
      if (mServiceConn == null) {
         mServiceConn = new ServiceConnection() {
            @Override
            public void onServiceDisconnected(ComponentName name) {
               iapEnabled = -99;
               mIInAppBillingService = null;
            }

            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
               try {
                  Class<?> stubClass = Class.forName("com.android.vending.billing.IInAppBillingService$Stub");
                  Method asInterfaceMethod = getAsInterfaceMethod(stubClass);

                  asInterfaceMethod.setAccessible(true);
                  mIInAppBillingService = asInterfaceMethod.invoke(null, service);

                  QueryBoughtItems();
               } catch (Throwable t) {
                  t.printStackTrace();
               }
            }
         };

         Intent serviceIntent = new Intent("com.android.vending.billing.InAppBillingService.BIND");
         serviceIntent.setPackage("com.android.vending");

         appContext.bindService(serviceIntent, mServiceConn, Context.BIND_AUTO_CREATE);
      } else if (mIInAppBillingService != null)
         QueryBoughtItems();
   }

   private void QueryBoughtItems() {
      if (isWaitingForPurchasesRequest)
         return;

      new Thread(new Runnable() {
         public void run() {
            isWaitingForPurchasesRequest = true;
            try {
               if (getPurchasesMethod == null) {
                  getPurchasesMethod = getGetPurchasesMethod(IInAppBillingServiceClass);
                  getPurchasesMethod.setAccessible(true);
               }

               Bundle ownedItems = (Bundle) getPurchasesMethod.invoke(mIInAppBillingService, 3, appContext.getPackageName(), "inapp", null);
               if (ownedItems.getInt("RESPONSE_CODE") == 0) {
                  ArrayList<String> skusToAdd = new ArrayList<String>();
                  ArrayList<String> newPurchaseTokens = new ArrayList<String>();

                  ArrayList<String> ownedSkus = ownedItems.getStringArrayList("INAPP_PURCHASE_ITEM_LIST");
                  ArrayList<String> purchaseDataList = ownedItems.getStringArrayList("INAPP_PURCHASE_DATA_LIST");

                  for (int i = 0; i < purchaseDataList.size(); i++) {
                     String purchaseData = purchaseDataList.get(i);
                     String sku = ownedSkus.get(i);
                     JSONObject itemPurchased = new JSONObject(purchaseData);
                     String purchaseToken = itemPurchased.getString("purchaseToken");

                     if (!purchaseTokens.contains(purchaseToken) && !newPurchaseTokens.contains(purchaseToken)) {
                        newPurchaseTokens.add(purchaseToken);
                        skusToAdd.add(sku);
                     }
                  }

                  if (skusToAdd.size() > 0)
                     sendPurchases(skusToAdd, newPurchaseTokens);
                  else if (purchaseDataList.size() == 0) {
                     newAsExisting = false;

                     OneSignalPrefs.saveBool(OneSignalPrefs.PREFS_PLAYER_PURCHASES,
                             OneSignalPrefs.PREFS_EXISTING_PURCHASES,false);
                  }

                  // TODO: Handle very large list. Test for continuationToken != null then call getPurchases again
               }
            } catch (Throwable e) {
               e.printStackTrace();
            }
            isWaitingForPurchasesRequest = false;
         }
      }).start();
   }

   private void sendPurchases(final ArrayList<String> skusToAdd, final ArrayList<String> newPurchaseTokens) {
      try {
         if (getSkuDetailsMethod == null) {
            getSkuDetailsMethod = getGetSkuDetailsMethod(IInAppBillingServiceClass);
            getSkuDetailsMethod.setAccessible(true);
         }

         Bundle querySkus = new Bundle();
         querySkus.putStringArrayList("ITEM_ID_LIST", skusToAdd);
         Bundle skuDetails = (Bundle)getSkuDetailsMethod.invoke(mIInAppBillingService, 3, appContext.getPackageName(), "inapp", querySkus);

         int response = skuDetails.getInt("RESPONSE_CODE");
         if (response == 0) {
            ArrayList<String> responseList = skuDetails.getStringArrayList("DETAILS_LIST");
            Map<String, JSONObject> currentSkus = new HashMap<>();
            JSONObject jsonItem;
            for (String thisResponse : responseList) {
               JSONObject object = new JSONObject(thisResponse);
               String sku = object.getString("productId");
               BigDecimal price = new BigDecimal(object.getString("price_amount_micros"));
               price = price.divide(new BigDecimal(1000000));

               jsonItem = new JSONObject();
               jsonItem.put("sku", sku);
               jsonItem.put("iso", object.getString("price_currency_code"));
               jsonItem.put("amount", price.toString());
               currentSkus.put(sku, jsonItem);
            }

            JSONArray purchasesToReport = new JSONArray();
            for (String sku : skusToAdd) {
               if (!currentSkus.containsKey(sku))
                  continue;
               purchasesToReport.put(currentSkus.get(sku));
            }

            // New purchases to report. If successful then mark them as tracked.
            if (purchasesToReport.length() > 0) {
               OneSignalRestClient.ResponseHandler restResponseHandler = new OneSignalRestClient.ResponseHandler() {
                  public void onFailure(int statusCode, JSONObject response, Throwable throwable) {
                     OneSignal.Log(OneSignal.LOG_LEVEL.WARN, "HTTP sendPurchases failed to send.", throwable);
                     isWaitingForPurchasesRequest = false;
                  }
   
                  public void onSuccess(String response) {
                     purchaseTokens.addAll(newPurchaseTokens);

                     OneSignalPrefs.saveString(OneSignalPrefs.PREFS_PLAYER_PURCHASES,
                             OneSignalPrefs.PREFS_PURCHASE_TOKENS, purchaseTokens.toString());
                     OneSignalPrefs.saveBool(OneSignalPrefs.PREFS_PLAYER_PURCHASES,
                              OneSignalPrefs.PREFS_EXISTING_PURCHASES, true);

                     newAsExisting = false;
                     isWaitingForPurchasesRequest = false;
                  }
               };
               
               OneSignal.sendPurchases(purchasesToReport, newAsExisting, restResponseHandler);
            }
         }
      } catch (Throwable t) {
         OneSignal.Log(OneSignal.LOG_LEVEL.WARN, "Failed to track IAP purchases", t);
      }
   }

   private static Method getAsInterfaceMethod(Class clazz) {
      for(Method method : clazz.getMethods()) {
         Class<?>[] args = method.getParameterTypes();
         if (args.length == 1 && args[0] == android.os.IBinder.class)
            return method;
      }

      return  null;
   }

   private static Method getGetPurchasesMethod(Class clazz) {
      for(Method method : clazz.getMethods()) {
         Class<?>[] args = method.getParameterTypes();
         if (args.length == 4
             && args[0] == int.class && args[1] == String.class && args[2] == String.class && args[3] == String.class)
            return method;
      }

      return  null;
   }

   private static Method getGetSkuDetailsMethod(Class clazz) {
      for(Method method : clazz.getMethods()) {
         Class<?>[] args = method.getParameterTypes();
         Class<?> returnType = method.getReturnType();

         if (args.length == 4
             && args[0] == int.class && args[1] == String.class && args[2] == String.class && args[3] == Bundle.class
             && returnType == Bundle.class)
            return method;
      }

      return  null;
   }
}


/*
  // IInAppBillingService

  public abstract int isBillingSupported(int paramInt, String paramString1, String paramString2)
  public abstract Bundle getSkuDetails(int paramInt, String paramString1, String paramString2, Bundle paramBundle)
  public abstract Bundle getBuyIntent(int paramInt, String paramString1, String paramString2, String paramString3, String paramString4)
  public abstract Bundle getPurchases(int paramInt, String paramString1, String paramString2, String paramString3)
  public abstract int consumePurchase(int paramInt, String paramString1, String paramString2)
  public abstract int isPromoEligible(int paramInt, String paramString1, String paramString2)
  public abstract Bundle getBuyIntentToReplaceSkus(int paramInt, String paramString1, List<String> paramList, String paramString2, String paramString3, String paramString4)
  public abstract Bundle getBuyIntentExtraParams(int paramInt, String paramString1, String paramString2, String paramString3, String paramString4, Bundle paramBundle)
  public abstract Bundle getPurchaseHistory(int paramInt, String paramString1, String paramString2, String paramString3, Bundle paramBundle)
  public abstract int isBillingSupportedExtraParams(int paramInt, String paramString1, String paramString2, Bundle paramBundle)
  public abstract Bundle getPurchasesExtraParams(int paramInt, String paramString1, String paramString2, String paramString3, Bundle paramBundle)
  public abstract int consumePurchaseExtraParams(int paramInt, String paramString1, String paramString2, Bundle paramBundle)
 */