package com.onesignal.example;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.TextView;

public class GreenActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_green);

        String openURL = getIntent().getStringExtra("openURL");

        final TextView textView = (TextView)findViewById(R.id.debug_view);
        textView.setText("URL from additionalData: " + openURL);


        Button onBackButton = (Button)(findViewById(R.id.back_button));
        onBackButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                startActivity(intent);
            }
        });

        WebView webView = (WebView)(findViewById(R.id.webview));
        webView.getSettings().setJavaScriptEnabled(true);
        webView.setWebViewClient(new WebViewClient());
        if (openURL == null) {
            webView.loadUrl("https://google.com");
        } else {
            webView.loadUrl(openURL);
        }


    }
}
