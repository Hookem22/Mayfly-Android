package com.joinpowwow.powwow;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.telephony.TelephonyManager;

import com.microsoft.windowsazure.mobileservices.authentication.MobileServiceUser;
import com.microsoft.windowsazure.notifications.NotificationsHandler;

import java.util.UUID;

public class MyHandler extends NotificationsHandler {

    public static final int NOTIFICATION_ID = 1;
    public static String tag;
    private NotificationManager mNotificationManager;
    NotificationCompat.Builder builder;
    Context ctx;
    private static final String PUSH_TOKEN_ID = "PUSH_TOKEN_ID";

    @Override
    public void onRegistered(final Context context,  final String gcmRegistrationId) {
        super.onRegistered(context, gcmRegistrationId);

        new AsyncTask<Void, Void, Void>() {

            protected Void doInBackground(Void... params) {
                try {
                    SharedPreferences sharedPrefs = context.getSharedPreferences(
                            PUSH_TOKEN_ID, Context.MODE_PRIVATE);
                    tag = sharedPrefs.getString(PUSH_TOKEN_ID, null);

                    MainActivity.mClient.getPush().unregisterAll(gcmRegistrationId);
                    MainActivity.mClient.getPush().register(gcmRegistrationId, new String[]{tag});

                    return null;
                }
                catch(Exception e) {
                    // handle error
                }
                return null;
            }
        }.execute();
    }

    @Override
    public void onReceive(Context context, Bundle bundle) {
        ctx = context;
        String nhMessage = bundle.getString("message");

        sendNotification(nhMessage);
    }

    private void sendNotification(String msg) {
        mNotificationManager = (NotificationManager)
                ctx.getSystemService(Context.NOTIFICATION_SERVICE);

        PendingIntent contentIntent = PendingIntent.getActivity(ctx, 0,
                new Intent(ctx, MainActivity.class), 0);

        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(ctx)
                        .setSmallIcon(R.drawable.logopush)
                        .setContentTitle("Pow Wow")
                        .setStyle(new NotificationCompat.BigTextStyle()
                                .bigText(msg))
                        .setContentText(msg);

        Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        mBuilder.setSound(alarmSound);

        mBuilder.setContentIntent(contentIntent);
        mNotificationManager.notify(NOTIFICATION_ID, mBuilder.build());
    }

}
