package ru.vinyarsky.androidaudioexample.ui;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;

import ru.vinyarsky.androidaudioexample.R;
import ru.vinyarsky.androidaudioexample.service.PlayerService;

public class MainActivity extends AppCompatActivity {

    PlayerService.PlayerServiceBinder playerServiceBinder;
    MediaControllerCompat mediaController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindService(new Intent(this, PlayerService.class), new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                playerServiceBinder = (PlayerService.PlayerServiceBinder) service;
                try {
                    mediaController = new MediaControllerCompat(MainActivity.this, playerServiceBinder.getMediaSessionToken());
                }
                catch (RemoteException e) {
                    mediaController = null;
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                playerServiceBinder = null;
                mediaController = null;
            }
        }, BIND_AUTO_CREATE);


        ((Button) findViewById(R.id.play)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mediaController != null)
                    mediaController.getTransportControls().play();
            }
        });

        ((Button) findViewById(R.id.pause)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mediaController != null)
                    mediaController.getTransportControls().pause();
            }
        });

        ((Button) findViewById(R.id.stop)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mediaController != null)
                    mediaController.getTransportControls().stop();
            }
        });
    }
}
