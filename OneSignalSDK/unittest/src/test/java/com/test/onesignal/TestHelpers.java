package com.test.onesignal;

import com.onesignal.OneSignalPackagePrivateHelper;

import java.util.Set;

class TestHelpers {
   
   static void threadAndTaskWait() throws Exception {
      boolean createdNewThread;
      do {
         createdNewThread = false;
         boolean joinedAThread;
         do {
            joinedAThread = false;
            Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
            
            for (Thread thread : threadSet) {
               if (thread.getName().startsWith("OS_")) {
                  thread.join();
                  joinedAThread = createdNewThread = true;
               }
            }
         } while (joinedAThread);
         
         boolean advancedRunnables = OneSignalPackagePrivateHelper.runAllNetworkRunnables();
         advancedRunnables = OneSignalPackagePrivateHelper.runFocusRunnables() || advancedRunnables;
         
         if (advancedRunnables)
            createdNewThread = true;
      } while (createdNewThread);
   }
}
