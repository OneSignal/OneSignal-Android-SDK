// Clears static properties on OneSignal to simulate an app cold start.

package com.onesignal;

import org.json.JSONArray;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class StaticResetHelper {

   static Collection<ClassState> classes = new ArrayList<>();

   static {
      classes.add(new StaticResetHelper().new ClassState(OneSignal.class, new OtherFieldHandler() {
         @Override
         public boolean onOtherField(Field field) throws Exception {
            if (field.getName() == "unprocessedOpenedNotifis") {
               field.set(null, new ArrayList<JSONArray>());
               return true;
            }
            return false;
         }
      }));

      classes.add(new StaticResetHelper().new ClassState(OneSignalStateSynchronizer.class, new OtherFieldHandler() {
         @Override
         public boolean onOtherField(Field field) throws Exception {
            if (field.getName() == "currentUserState" || field.getName() == "toSyncUserState") {
               field.set(null, null);
               return true;
            }
            return false;
         }
      }));
   }

   private interface OtherFieldHandler {
      boolean onOtherField(Field field) throws Exception;
   }

   private class ClassState {
      private OtherFieldHandler otherFieldHandler;
      private Class stateClass;
      private Map<Field, Object> orginalVals = new HashMap<Field, Object>();

      ClassState(Class inClass, OtherFieldHandler inOtherFieldHandler) {
         stateClass = inClass;
         otherFieldHandler = inOtherFieldHandler;
      }

      private Object tryClone(Object v) throws Exception {
         if (v instanceof Cloneable)
            return v.getClass().getMethod("clone").invoke(v);
         return v;
      }

      private void saveStaticValues() throws Exception {
         Field[] allFields = stateClass.getDeclaredFields();
         try {
            for (Field field : allFields) {
               int fieldModifiers = field.getModifiers();
               if (Modifier.isStatic(fieldModifiers)
                   && !Modifier.isFinal(fieldModifiers)) {
                  field.setAccessible(true);
                  Object value = tryClone(field.get(null));
                  orginalVals.put(field, value);
               }
            }
         } catch (IllegalAccessException e) {
            System.err.println(e);
         }
      }

      private void restSetStaticFields() throws Exception {
         for (Map.Entry<Field, Object> entry : orginalVals.entrySet()) {
            Field field = entry.getKey();
            Object value = entry.getValue();
            Class<?> type = field.getType();
            field.getName();
            field.setAccessible(true);

            if (!otherFieldHandler.onOtherField(field))
               field.set(null, tryClone(value));
         }
      }
   }

   public static void saveStaticValues() {
      for(ClassState aClass : classes) {
         try {
            aClass.saveStaticValues();
         } catch (Exception e) {
            e.printStackTrace();
         }
      }
   }

   public static void restSetStaticFields() {
      for(ClassState aClass : classes) {
         try {
            aClass.restSetStaticFields();
         } catch (Exception e) {
            e.printStackTrace();
         }
      }
   }
}
