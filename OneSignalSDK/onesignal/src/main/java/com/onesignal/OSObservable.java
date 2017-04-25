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
   
   OSObservable(String methodName) {
      this.methodName = methodName;
      observers = new ArrayList<>();
   }
   
   void addObserver(ObserverType observer) {
      observers.add(new WeakReference<>(observer));
   }
   
   void addObserverStrong(ObserverType observer){
      observers.add(observer);
   }
   
   boolean notifyChange(StateType state) {
      boolean notified = false;
      
      for(Object observer : observers) {
         Object strongRefObserver;
         if (observer instanceof WeakReference)
            strongRefObserver = ((WeakReference)observer).get();
         else
            strongRefObserver = observer;
         
         if (strongRefObserver != null) {
            try {
               Class<?> clazz = strongRefObserver.getClass();
               Method method = clazz.getMethod(methodName, state.getClass());
               method.setAccessible(true);
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
