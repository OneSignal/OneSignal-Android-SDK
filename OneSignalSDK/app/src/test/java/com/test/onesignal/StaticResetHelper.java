package com.test.onesignal;

import com.onesignal.OneSignal;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

public class StaticResetHelper {

    static Map<Field, Object> orginalVals = new HashMap<Field, Object>();

    static Object tryClone(Object v) throws Exception {
        if (v instanceof Cloneable) {
            return v.getClass().getMethod("clone").invoke(v);
        }
        return v;
    }

    public static void saveStaticValues() throws Exception {
        Field[] allFields = OneSignal.class.getDeclaredFields();
        try {
            for (Field field : allFields) {
                int fieldModifiers = field.getModifiers();
                if ( Modifier.isStatic(fieldModifiers)
                  && !Modifier.isFinal(fieldModifiers)) {
                    field.setAccessible(true);
                    Object value = tryClone(field.get(null));
                    orginalVals.put(field, value);
                }
            }
        }
        catch (IllegalAccessException e) {
            System.err.println(e);
        }
    }

    public static void restSetStaticFields() throws Exception {
        for (Map.Entry<Field, Object> entry : orginalVals.entrySet()) {
            Field field = entry.getKey();
            Object value = entry.getValue();
            Class<?> type = field.getType();

            field.setAccessible(true);

            if (type == Integer.TYPE)
                field.setInt(null, (Integer) value);
            else
                field.set(null, tryClone(value));
        }
    }
}
