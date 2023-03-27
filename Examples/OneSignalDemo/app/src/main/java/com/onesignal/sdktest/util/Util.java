package com.onesignal.sdktest.util;

public class Util {

    public static boolean isBoolean(String string) {
        string = string.toLowerCase();
        return (string.equals("true") || string.equals("false"));
    }

    public static boolean isInteger(String string) {
        try {
            Long.parseLong(string);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static boolean isFloat(String string) {
        try {
            Double.parseDouble(string);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

}
