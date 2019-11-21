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

package com.onesignal;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class MockHttpURLConnection extends HttpURLConnection {
   private boolean didInterruptMockHang;

   public boolean getDidInterruptMockHang() {
      return didInterruptMockHang;
   }

   public static class MockResponse {
      public String responseBody;
      public String errorResponseBody;
      public boolean mockThreadHang;
      public int status;
      public Map<String, String> mockProps = new HashMap<>();
   }

   private MockResponse mockResponse;

   MockHttpURLConnection(URL url, MockResponse response) {
      super(url);
      mockResponse = response;
   }

   @Override
   public void disconnect() {

   }

   @Override
   public boolean usingProxy() {
      return false;
   }

   @Override
   public void connect() throws IOException {

   }

   @Override
   public String getHeaderField(String name) {
      return mockResponse.mockProps.get(name);
   }

   @Override
   public int getResponseCode() throws IOException {
      if (mockResponse.mockThreadHang) {
         try {
            Thread.sleep(120_000);
         } catch (InterruptedException e) {
            didInterruptMockHang = true;
            throw new IOException("Successfully interrupted stuck thread!");
         }
      }

      return mockResponse.status;
   }

   @Override
   public InputStream getInputStream() throws IOException {
      return new ByteArrayInputStream(StandardCharsets.UTF_8.encode(mockResponse.responseBody).array());
   }

   @Override
   public InputStream getErrorStream() {
      if (mockResponse.errorResponseBody == null)
         return null;

      byte[] bytes = mockResponse.errorResponseBody.getBytes();
      return new ByteArrayInputStream(bytes);
   }
}
