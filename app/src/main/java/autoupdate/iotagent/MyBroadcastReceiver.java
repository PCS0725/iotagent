package autoupdate.iotagent;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

import autoupdate.iotagent.service.PubSubService;

public class MyBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "MyBroadcastReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {

        String action = intent.getAction();
        StringBuilder sb = new StringBuilder();
        sb.append("Action: " + action + "\n");
        String log = sb.toString();
        Log.d(TAG, log);
        Toast.makeText(context, log, Toast.LENGTH_LONG).show();
        context.startForegroundService(new Intent("Background MQTT", null, context, PubSubService.class));
    }
}
