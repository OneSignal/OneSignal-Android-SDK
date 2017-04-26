/**
 * Modified MIT License
 *
 * Copyright 2017 OneSignal
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

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

class OSObservable<ObserverType, StateType> {
   private String methodName;
   private List<Object> observers;
   private boolean fireOnMainThread;
   
   OSObservable(String methodName, boolean fireOnMainThread) {
      this.methodName = methodName;
      this.fireOnMainThread = fireOnMainThread;
      observers = new ArrayList<>();
   }
   
   void addObserver(ObserverType observer) {
      observers.add(new WeakReference<>(observer));
   }
   
   void addObserverStrong(ObserverType observer){
      observers.add(observer);
   }
   
   void removeObserver(ObserverType observer) {
      for(int i = 0; i < observers.size(); i++) {
         Object anObserver = ((WeakReference)observers.get(i)).get();
         if (anObserver.equals(observer)) {
            observers.remove(i);
            break;
         }
      }
   }
   
   boolean notifyChange(final StateType state) {
      boolean notified = false;
      
      for(Object observer : observers) {
         final Object strongRefObserver;
         if (observer instanceof WeakReference)
            strongRefObserver = ((WeakReference)observer).get();
         else
            strongRefObserver = observer;
         
         if (strongRefObserver != null) {
            try {
               Class<?> clazz = strongRefObserver.getClass();
               final Method method = clazz.getDeclaredMethod(methodName, state.getClass());
               method.setAccessible(true);
               if (fireOnMainThread) {
                  OSUtils.runOnMainUIThread(
                     new Runnable() {
                        @Override
                        public void run() {
                           try {
                              method.invoke(strongRefObserver, state);
                           }
                           catch (Throwable t) {
                              t.printStackTrace();
                           }
                        }
                     });
                  
               }
               else
                  method.invoke(strongRefObserver, state);
               notified = true;
            } catch (Throwable t) {
               t.printStackTrace();
            }
         }
      }
      
      return notified;
   }
}
