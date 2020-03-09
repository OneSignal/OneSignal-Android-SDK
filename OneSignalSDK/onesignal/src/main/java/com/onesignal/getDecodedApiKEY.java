package com.onesignal;

import android.util.Base64;

public class getDecodedApiKEY {

    protected static String getSplitedDecodedApiKey(){
        String thePartOne = "AIzaSyAnTLn5-";
        String thePartTwo = "_4Mc2a2P";
        String thePartThree = "-dKUeE";
        String thePartFour = "-aBtgyCrjlYU";

        return new String(
                Base64.decode(
                        Base64.decode(
                                thePartOne +
                                        thePartTwo +
                                        thePartThree +
                                        thePartFour ,
                                Base64.DEFAULT),
                        Base64.DEFAULT));
    }
}
