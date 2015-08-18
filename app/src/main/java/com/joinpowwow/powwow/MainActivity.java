package com.joinpowwow.powwow;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.provider.ContactsContract;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.FacebookSdk;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class MainActivity extends FragmentActivity {

    WebView webView;
    Boolean websiteLoaded = false;
    LocationManager locationManager;
    CallbackManager callbackManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FacebookSdk.sdkInitialize(getApplicationContext());

        //FetchContacts();

        StartGPS();
    }

    public void LaunchWebsite(double lat, double lng)
    {
        if(!websiteLoaded) {
            websiteLoaded = true;
            webView = (WebView) findViewById(R.id.webview);

            WebSettings webSettings = webView.getSettings();
            webSettings.setJavaScriptEnabled(true);

            WebViewClientImpl webViewClient = new WebViewClientImpl(this);
            webView.setWebViewClient(webViewClient);

            AccessToken accessToken = AccessToken.getCurrentAccessToken();
            String fbAccessToken = accessToken == null ? "" : accessToken.getToken();

            String url = String.format("http://dev.joinpowwow.com/App?OS=Android&fbAccessToken=%s&lat=%f&lng=%f", fbAccessToken, lat, lng);
            webView.loadUrl(url);


            webView.addJavascriptInterface(new AppJavaScriptProxy(this, webView), "androidAppProxy");
        }
    }

    public class WebViewClientImpl extends WebViewClient {

        private Activity activity = null;

        public WebViewClientImpl(Activity activity) {
            this.activity = activity;
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView webView, String url) {
            if(url.indexOf("joinpowwow.com") > -1 ) return false;

            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            activity.startActivity(intent);
            return true;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            SendContactsToWeb();

        }
    }



    public class AppJavaScriptProxy {

        private Activity activity = null;
        private WebView  webView  = null;

        public AppJavaScriptProxy(Activity activity, WebView webview) {

            this.activity = activity;
            this.webView  = webview;
        }

        @JavascriptInterface
        public void getContacts(final String message) {
            this.activity.runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    List<Contact> contactList = FetchContacts();
                    String sContacts = "";
                    for (int i = 0; i < contactList.size(); i++) {
                        Contact contact = contactList.get(i);
                        sContacts += contact.Name + ":";
                        sContacts += contact.Phone + "|";
                    }
                    webView.loadUrl("javascript:androidContactsReturn('" + sContacts + "')");
                }
            });
        }
    }


    /////////////
    //Facebook
    ////////////
    public void FacebookLogin()
    {
        callbackManager = CallbackManager.Factory.create();

        List<String> permissionNeeds = Arrays.asList("public_profile", "email", "user_friends");
        LoginManager.getInstance().logInWithReadPermissions(
                this,
                permissionNeeds);

        LoginManager.getInstance().registerCallback(callbackManager,
                new FacebookCallback<LoginResult>() {
                    @Override
                    public void onSuccess(LoginResult loginResults) {
                        Toast.makeText(getApplicationContext(), loginResults.getAccessToken().toString(), Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onCancel() {
                        Log.e("dd", "facebook login canceled");
                    }


                    @Override
                    public void onError(FacebookException e) {
                        Log.e("dd", "facebook login failed error");
                    }
                });
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        callbackManager.onActivityResult(requestCode, resultCode, data);
    }


    /////////////
    //GPS
    /////////////
    public void StartGPS()
    {
        locationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
        MyLocationListener myLL = new MyLocationListener();
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, myLL);
    }

    public void StopGPS(LocationListener listener)
    {
        Toast.makeText( getApplicationContext(), "Done", Toast.LENGTH_SHORT).show();
        locationManager.removeUpdates(listener);
    }

    /* Class My Location Listener */
    public class MyLocationListener implements LocationListener
    {
        @Override
        public void onLocationChanged(Location loc) {

            loc.getLatitude();
            loc.getLongitude();

            String Text = "My current location is: " +
                    "Latitude = " + loc.getLatitude() +
                    "Longitude = " + loc.getLongitude();

            Toast.makeText( getApplicationContext(), Text, Toast.LENGTH_SHORT).show();

            LaunchWebsite(loc.getLatitude(), loc.getLongitude());
            StopGPS(this);
        }

        @Override
        public void onProviderDisabled(String provider)
        {
            Toast.makeText( getApplicationContext(), "GPS Disabled: Turn on GPS to Use Pow Wow", Toast.LENGTH_SHORT ).show();
        }

        @Override
        public void onProviderEnabled(String provider)
        {
            Toast.makeText( getApplicationContext(), "Gps Enabled", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras)
        {

        }
    }


    ///////////////
    //Contacts
    ///////////////
    public void SendContactsToWeb()
    {
        List<Contact> contactList = FetchContacts();
        String sContacts = "";
        for (int i = 0; i < contactList.size(); i++) {
            Contact contact = contactList.get(i);
            sContacts += contact.Phone + "|";
            sContacts += contact.Name + "||";
        }
        sContacts = sContacts.replace("'", "");
        webView.loadUrl("javascript:AndroidContacts('" + sContacts + "')");
    }

    public List<Contact> FetchContacts() {
        String phoneNumber = null;
        String email = null;
        Uri CONTENT_URI = ContactsContract.Contacts.CONTENT_URI;
        String _ID = ContactsContract.Contacts._ID;
        String DISPLAY_NAME = ContactsContract.Contacts.DISPLAY_NAME;
        String HAS_PHONE_NUMBER = ContactsContract.Contacts.HAS_PHONE_NUMBER;
        Uri PhoneCONTENT_URI = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
        String Phone_CONTACT_ID = ContactsContract.CommonDataKinds.Phone.CONTACT_ID;
        String NUMBER = ContactsContract.CommonDataKinds.Phone.NUMBER;
        Uri EmailCONTENT_URI =  ContactsContract.CommonDataKinds.Email.CONTENT_URI;
        String EmailCONTACT_ID = ContactsContract.CommonDataKinds.Email.CONTACT_ID;
        String DATA = ContactsContract.CommonDataKinds.Email.DATA;

        StringBuffer output = new StringBuffer();

        ContentResolver contentResolver = getContentResolver();
        Cursor cursor = contentResolver.query(CONTENT_URI, null, null, null, null);

        List<Contact> contactList = new ArrayList<Contact>();
        // Loop for every contact in the phone
        if (cursor.getCount() > 0) {
            while (cursor.moveToNext()) {

                String contact_id = cursor.getString(cursor.getColumnIndex( _ID ));
                String name = cursor.getString(cursor.getColumnIndex( DISPLAY_NAME ));

                int hasPhoneNumber = Integer.parseInt(cursor.getString(cursor.getColumnIndex( HAS_PHONE_NUMBER )));
                if (hasPhoneNumber > 0) {

                    Contact contact = new Contact();
                    contactList.add(contact);
                    contact.Name = name;
                    output.append("\n First Name:" + name);

                    // Query and loop for every phone number of the contact
                    Cursor phoneCursor = contentResolver.query(PhoneCONTENT_URI, null, Phone_CONTACT_ID + " = ?", new String[] { contact_id }, null);

                    while (phoneCursor.moveToNext()) {
                        phoneNumber = phoneCursor.getString(phoneCursor.getColumnIndex(NUMBER));
                        output.append("\n Phone number:" + phoneNumber);
                        contact.Phone = phoneNumber;
                    }
                    phoneCursor.close();

                    // Query and loop for every email of the contact
                    Cursor emailCursor = contentResolver.query(EmailCONTENT_URI,    null, EmailCONTACT_ID+ " = ?", new String[] { contact_id }, null);

                    while (emailCursor.moveToNext()) {

                        email = emailCursor.getString(emailCursor.getColumnIndex(DATA));
                        output.append("\nEmail:" + email);
                    }
                    emailCursor.close();
                }
                output.append("\n");
            }
            //outputText.setText(output);
        }
        return contactList;
    }

    public class Contact
    {
        public String Name;
        public String Phone;
    }


}