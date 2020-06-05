package autoupdate.iotagent;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;

import autoupdate.iotagent.service.PubSubService;

public class MainActivity extends AppCompatActivity {

    private final String LOG_TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Environment.getExternalStorageDirectory().mkdirs();
        getApplicationContext().startService(new Intent("Background MQTT", null, this, PubSubService.class));
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

}
