package com.onesignal.onesignal.internal.common;

import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class OSObservable<ObserverType, StateType> {
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
         if (anObserver == null) continue;
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
            Class<?> clazz = strongRefObserver.getClass();
            try {
               final Method method = clazz.getDeclaredMethod(methodName, state.getClass());
               method.setAccessible(true);
               if (fireOnMainThread) {
                  OSUtils.runOnMainUIThread(
                      new Runnable() {
                          @Override
                          public void run() {
                              try {
                                  method.invoke(strongRefObserver, state);
                              } catch (IllegalAccessException e) {
                                  e.printStackTrace();
                              } catch (InvocationTargetException e) {
                                  e.printStackTrace();
                              }
                          }
                      }
                 );
               }
               else {
                  try {
                     method.invoke(strongRefObserver, state);
                  } catch (IllegalAccessException e) {
                     e.printStackTrace();
                  } catch (InvocationTargetException e) {
                     e.printStackTrace();
                  }
               }

               notified = true;
            } catch (NoSuchMethodException e) {
               e.printStackTrace();
            }
         }
      }
      
      return notified;
   }
}
