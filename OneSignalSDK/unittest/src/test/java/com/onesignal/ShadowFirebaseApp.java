package com.onesignal;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

import org.robolectric.annotation.Implements;

@Implements(com.google.firebase.FirebaseApp.class)
public class ShadowFirebaseApp {

    @NonNull
    public static FirebaseApp initializeApp(@NonNull Context context, @NonNull FirebaseOptions options, @NonNull String name) {
        // Throw simulates Firebase library not bundled with app
        throw new RuntimeException();
    }
}
