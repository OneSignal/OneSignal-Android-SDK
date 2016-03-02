/**
 * This file is part of the RootTools Project: http://code.google.com/p/roottools/
 *
 * Copyright (c) 2012 Stephen Erickson, Chris Ravenscroft, Dominik Schuermann, Adam Shanks
 *
 * This code is dual-licensed under the terms of the Apache License Version 2.0 and
 * the terms of the General Public License (GPL) Version 2.
 * You may use this code according to either of these licenses as is most appropriate
 * for your project on a case-by-case basis.
 *
 * The terms of each license can be found at:
 *
 * * http://www.apache.org/licenses/LICENSE-2.0
 * * http://www.gnu.org/licenses/gpl-2.0.txt
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under these Licenses is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See each License for the specific language governing permissions and
 * limitations under that License.
 */

// Namespaced in com.onesignal to prevent class name conflicts if app developer includes the full RootTools library.
package com.onesignal;

class RootToolsInternalMethods {
   static boolean isRooted() {
      String[] places = {"/sbin/", "/system/bin/", "/system/xbin/",
                         "/data/local/xbin/", "/data/local/bin/",
                         "/system/sd/xbin/", "/system/bin/failsafe/",
                         "/data/local/"};

      for (String where : places) {
         if (new java.io.File(where + "su").exists())
            return true;
      }

      return false;
   }
}