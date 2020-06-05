package autoupdate.iotagent.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import autoupdate.iotagent.MainActivity;
import autoupdate.iotagent.util.IOTHelper;

/**
 * This is a foreground service. It is responsible for invoking the IOTHelper class in background.
 * It also creates a notification, required for foreground services.
 */
public class PubSubService extends Service {

    public static final String CHANNEL_ID = "ForegroundServiceChannel";
    private final String LOG_TAG = "PubSubService";

    boolean isConnected = false;

    public PubSubService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        createNotificationChannel();
        Intent notificationIntent = new Intent(getApplicationContext(), MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(),
                0, notificationIntent, 0);
        Notification notification = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
                .setContentTitle("Foreground Service")
                .setContentText(String.valueOf(isConnected))
                .setContentIntent(pendingIntent)
                .build();

        startForeground(1, notification);
        try {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        IOTHelper iotHelper = IOTHelper.getInstance(getApplicationContext());
                        iotHelper.init(getFilesDir().getPath());
                        Log.i(LOG_TAG, "IOT Helper invoked");
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "err " + e.getMessage(), e);
                    }
                }
            }).start();
        } catch (Exception e) {
            Log.e(LOG_TAG, e.getMessage());
        }
    }

    @Override
    public void onDestroy() {
        Log.i(LOG_TAG, "Service stopped");
    }


    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Foreground Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }

}

