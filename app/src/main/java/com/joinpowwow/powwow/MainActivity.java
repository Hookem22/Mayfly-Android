package com.joinpowwow.powwow;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.util.Log;

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

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.microsoft.windowsazure.mobileservices.MobileServiceClient;
import com.microsoft.windowsazure.notifications.NotificationsManager;

import org.json.JSONObject;

import io.branch.referral.Branch;
import io.branch.referral.BranchError;


public class MainActivity extends FragmentActivity {

    WebView webView;
    Boolean websiteLoaded = false;
    LocationManager locationManager;
    CallbackManager callbackManager;
    public static final String SENDER_ID = "1014214755425";
    public static MobileServiceClient mClient;
    private String branchEvent;
    private double latitude;
    private double longitude;
    private boolean latLngSent = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        FacebookSdk.sdkInitialize(getApplicationContext());

        LaunchWebsite();

        try {
            mClient = new MobileServiceClient(
                    "https://mayflyapp.azure-mobile.net/",
                    "NzEvHltvEcuInDmQsnXqReEIitsWIa99",
                    this);

            NotificationsManager.handleNotifications(this, SENDER_ID, MyHandler.class);

        } catch (MalformedURLException e) {
            e.printStackTrace();
        }

        StartGPS();

        GetBranchEvent();

    }

    public void LaunchWebsite()
    {
        webView = (WebView) findViewById(R.id.webview);

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);

        WebViewClientImpl webViewClient = new WebViewClientImpl(this);
        webView.setWebViewClient(webViewClient);

        //Facebook
        AccessToken accessToken = AccessToken.getCurrentAccessToken();
        String fbAccessToken = accessToken == null ? "" : accessToken.getToken();

        String deviceId = GetUuid(getApplicationContext());

        String url = String.format("http://joinpowwow.azurewebsites.net/App?OS=Android&fbAccessToken=%s&pushDeviceToken=%s&lat=%f&lng=%f", fbAccessToken, deviceId, latitude, longitude);
        //if(branchEvent != null)
        //    url = String.format("%s&goToEvent=%s", url, branchEvent);
        webView.loadUrl(url);

        webView.addJavascriptInterface(new AppJavaScriptProxy(this, webView), "androidAppProxy");
    }

    private static String uniqueID = null;
    private static final String PUSH_TOKEN_ID = "PUSH_TOKEN_ID";

    public synchronized static String GetUuid(Context context) {
        if (uniqueID == null) {
            SharedPreferences sharedPrefs = context.getSharedPreferences(
                    PUSH_TOKEN_ID, Context.MODE_PRIVATE);
            uniqueID = sharedPrefs.getString(PUSH_TOKEN_ID, null);
            if (uniqueID == null) {
                uniqueID = UUID.randomUUID().toString();
                SharedPreferences.Editor editor = sharedPrefs.edit();
                editor.putString(PUSH_TOKEN_ID, uniqueID);
                editor.commit();
            }
        }
        return uniqueID;
    }

    public class WebViewClientImpl extends WebViewClient {

        private Activity activity = null;

        public WebViewClientImpl(Activity activity) {
            this.activity = activity;
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView webView, String url) {
            if(url.indexOf("joinpowwow.azurewebsites.net") > -1 ) return false;

            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            activity.startActivity(intent);
            return true;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            //No longer inviting Friends
            //SendContactsToWeb();

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
        public void sendSMS(final String phones, final String message) {
            this.activity.runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) // At least KitKat
                    {
                        String defaultSmsPackageName = Telephony.Sms.getDefaultSmsPackage(getApplicationContext()); // Need to change the build to API 19

                        Intent sendIntent = new Intent(Intent.ACTION_SEND);
                        sendIntent.setType("text/plain");
                        sendIntent.putExtra("address", phones);
                        sendIntent.putExtra(Intent.EXTRA_TEXT, message);

                        if (defaultSmsPackageName != null)// Can be null in case that there is no default, then the user would be able to choose
                        // any app that support this intent.
                        {
                            sendIntent.setPackage(defaultSmsPackageName);
                        }
                        startActivity(sendIntent);

                    }
                    else // For early versions, do what worked for you before.
                    {
                        Intent smsIntent = new Intent(android.content.Intent.ACTION_VIEW);
                        smsIntent.setType("vnd.android-dir/mms-sms");
                        smsIntent.putExtra("address", phones);
                        smsIntent.putExtra("sms_body",message);
                        startActivity(smsIntent);
                    }
                }
            });
        }

        @JavascriptInterface
        public void AndroidFacebookLogin() {
            this.activity.runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    FacebookLogin();
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
                        websiteLoaded = false;
                        LaunchWebsite();
                        //Toast.makeText(getApplicationContext(), loginResults.getAccessToken().toString(), Toast.LENGTH_SHORT).show();
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
        //Toast.makeText( getApplicationContext(), "Done", Toast.LENGTH_SHORT).show();
        locationManager.removeUpdates(listener);
    }

    /* Class My Location Listener */
    public class MyLocationListener implements LocationListener
    {
        @Override
        public void onLocationChanged(Location loc) {

            latitude = loc.getLatitude();
            longitude = loc.getLongitude();

            //String Text = "My current location is: " +
            //        "Latitude = " + loc.getLatitude() +
            //        "Longitude = " + loc.getLongitude();

            //Toast.makeText( getApplicationContext(), Text, Toast.LENGTH_SHORT).show();

            //if(MyHandler.tag != null && !MyHandler.tag.isEmpty()) {
                //LaunchWebsite();
                SendLatLngToWeb();
                StopGPS(this);
            //}
        }

        private void SendLatLngToWeb()
        {
            if(!latLngSent)
            {
                latLngSent = true;
                webView.loadUrl("javascript:ReceiveLocation('" + latitude + "', '" + longitude + "')");
            }

        }


        @Override
        public void onProviderDisabled(String provider)
        {
            //Toast.makeText( getApplicationContext(), "GPS Disabled: Turn on GPS to Use Pow Wow", Toast.LENGTH_SHORT ).show();
        }

        @Override
        public void onProviderEnabled(String provider)
        {
            //Toast.makeText( getApplicationContext(), "Gps Enabled", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras)
        {

        }
    }


    ///////////////
    //Contacts
    ///////////////
    /*
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
    */
    /////////////////
    //Branch
    /////////////////
    public void GetBranchEvent()
    {
        final Branch branch = Branch.getInstance(getApplicationContext());
        branch.initSession(new Branch.BranchReferralInitListener() {
            @Override
            public void onInitFinished(JSONObject referringParams, BranchError error) {
                if (error == null) {
                    // params are the deep linked params associated with the link that the user clicked -> was re-directed to this app
                    // params will be empty if no data found
                    try
                    {
                        branchEvent = referringParams.getString("referenceId");
                        if(branchEvent != null && branchEvent != "")
                        {
                            webView.loadUrl("javascript:GoToEvent('" + branchEvent + "')");
                        }
                    }
                    catch(Exception ex) { }

                } else {
                    Log.i("MyApp", error.getMessage());
                }
            }
        }, this.getIntent().getData(), this);
    }

    @Override
    public void onNewIntent(Intent intent) {
        this.setIntent(intent);
    }
}
