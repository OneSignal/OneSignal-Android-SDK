/**
 * Modified MIT License
 *
 * Copyright 2019 OneSignal
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

package com.test.onesignal;

import com.onesignal.BuildConfig;
import com.onesignal.MockHttpURLConnection;
import com.onesignal.OneSignal;
import com.onesignal.OneSignalPackagePrivateHelper.OneSignalRestClient;
import com.onesignal.ShadowOneSignalRestClientWithMockConnection;
import com.onesignal.StaticResetHelper;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

@Config(packageName = "com.onesignal.example",
    constants = BuildConfig.class,
    instrumentedPackages = {"com.onesignal"},
    shadows = { ShadowOneSignalRestClientWithMockConnection.class },
    sdk = 26)
@RunWith(RobolectricTestRunner.class)
public class RESTClientRunner {
   
   @BeforeClass // Runs only once, before any tests
   public static void setUpClass() throws Exception {
      ShadowLog.stream = System.out;
      TestHelpers.beforeTestSuite();
      StaticResetHelper.saveStaticValues();
   }

   @Before // Before each test
   public void beforeEachTest() throws Exception {
      firstResponse = secondResponse = null;
      TestHelpers.beforeTestInitAndCleanup();
   }

   @AfterClass
   public static void afterEverything() throws Exception {
      StaticResetHelper.restSetStaticFields();
   }

   @Test
   public void testRESTClientFallbackTimeout() throws Exception {
      ShadowOneSignalRestClientWithMockConnection.mockResponse = new MockHttpURLConnection.MockResponse() {{
         mockThreadHang = true;
      }};

      OneSignalRestClient.getSync("URL", null,"");
      TestHelpers.threadAndTaskWait();

      assertTrue(ShadowOneSignalRestClientWithMockConnection.lastConnection.getDidInterruptMockHang());
   }

   private static final String SDK_VERSION_HTTP_HEADER = "onesignal/android/" + OneSignal.VERSION;

   @Test
   public void SDKHeaderIsIncludedInGetCalls() throws Exception {
      OneSignalRestClient.getSync("URL", null, null);
      TestHelpers.threadAndTaskWait();

      assertEquals(SDK_VERSION_HTTP_HEADER, getLastHTTPHeaderProp("SDK-Version"));
   }

   @Test
   public void SDKHeaderIsIncludedInPostCalls() throws Exception {
      OneSignalRestClient.postSync("URL", null,null);
      TestHelpers.threadAndTaskWait();

      assertEquals(SDK_VERSION_HTTP_HEADER, getLastHTTPHeaderProp("SDK-Version"));
   }

   @Test
   public void SDKHeaderIsIncludedInPutCalls() throws Exception {
      OneSignalRestClient.putSync("URL", null,null);
      TestHelpers.threadAndTaskWait();

      assertEquals(SDK_VERSION_HTTP_HEADER, getLastHTTPHeaderProp("SDK-Version"));
   }

   private final static String MOCK_CACHE_KEY = "MOCK_CACHE_KEY";
   private final static String MOCK_ETAG_VALUE = "MOCK_ETAG_VALUE";

   private String firstResponse, secondResponse;

   // Note Thread.sleep in the following two tests are used since we can't wait on threads
   //    created from callResponseHandlerOnSuccess due deadlock limitations with Scheduler
   //    in the Robolectric unit test library.
   // https://github.com/robolectric/robolectric/issues/3819
   @Test
   public void testReusesCache() throws Exception {
      // 1. Do first request to save response
      ShadowOneSignalRestClientWithMockConnection.mockResponse = new MockHttpURLConnection.MockResponse() {{
         status = 200;
         responseBody = "{\"key1\": \"value1\"}";
         mockProps.put("etag", MOCK_ETAG_VALUE);
      }};
      OneSignalRestClient.getSync("URL", new OneSignalRestClient.ResponseHandler() {
         @Override
         public void onSuccess(String response) {
            firstResponse = response;
         }
      }, MOCK_CACHE_KEY);
      TestHelpers.threadAndTaskWait();
      Thread.sleep(200);

      // 2. Make 2nd request and make sure we send the ETag and use the cached response
      ShadowOneSignalRestClientWithMockConnection.mockResponse = new MockHttpURLConnection.MockResponse() {{
         status = 304;
      }};

      OneSignalRestClient.getSync("URL", new OneSignalRestClient.ResponseHandler() {
         @Override
         public void onSuccess(String response) {
            secondResponse = response;
         }
      }, MOCK_CACHE_KEY);
      TestHelpers.threadAndTaskWait();
      Thread.sleep(200);

      assertNotNull(firstResponse);
      assertEquals(firstResponse, secondResponse);
      assertEquals(MOCK_ETAG_VALUE, getLastHTTPHeaderProp("if-none-match"));
   }

   @Test
   public void testReplacesCacheOn200() throws Exception {
      testReusesCache();
      firstResponse = secondResponse = null;
      final String newMockResponse = "{\"key2\": \"value2\"}";

      // 3. Make 3rd request and make sure we send the ETag and use the cached response
      ShadowOneSignalRestClientWithMockConnection.mockResponse = new MockHttpURLConnection.MockResponse() {{
         status = 200;
         responseBody = newMockResponse;
         mockProps.put("etag", "MOCK_ETAG_VALUE2");
      }};
      OneSignalRestClient.getSync("URL", null, MOCK_CACHE_KEY);
      TestHelpers.threadAndTaskWait();
      Thread.sleep(200);

      // 4. Make 4th request and make sure we get the new cached value
      ShadowOneSignalRestClientWithMockConnection.mockResponse = new MockHttpURLConnection.MockResponse() {{
         status = 304;
      }};
      OneSignalRestClient.getSync("URL", new OneSignalRestClient.ResponseHandler() {
         @Override
         public void onSuccess(String response) {
            secondResponse = response.replace("\u0000", "");
         }
      }, MOCK_CACHE_KEY);
      TestHelpers.threadAndTaskWait();
      Thread.sleep(200);

      assertEquals(newMockResponse, secondResponse);
   }

   private static String getLastHTTPHeaderProp(String prop) {
      return ShadowOneSignalRestClientWithMockConnection.lastConnection.getRequestProperty(prop);
   }
}
