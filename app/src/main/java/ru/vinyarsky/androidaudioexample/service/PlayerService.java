package ru.vinyarsky.androidaudioexample.service;

import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaButtonReceiver;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v7.app.NotificationCompat;

import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ext.okhttp.OkHttpDataSourceFactory;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.cache.Cache;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.upstream.cache.CacheDataSourceFactory;
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor;
import com.google.android.exoplayer2.upstream.cache.SimpleCache;
import com.google.android.exoplayer2.util.Util;

import java.io.File;

import okhttp3.OkHttpClient;
import ru.vinyarsky.androidaudioexample.ui.MainActivity;
import ru.vinyarsky.androidaudioexample.R;

final public class PlayerService extends Service {

    private final MediaMetadataCompat.Builder metadataBuilder = new MediaMetadataCompat.Builder();

    private final PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder().setActions(
            PlaybackStateCompat.ACTION_PLAY
                    | PlaybackStateCompat.ACTION_STOP
                    | PlaybackStateCompat.ACTION_PAUSE
                    | PlaybackStateCompat.ACTION_PLAY_PAUSE
                    | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                    | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
    );

    private MediaSessionCompat mediaSession;

    private AudioManager audioManager;

    private SimpleExoPlayer exoPlayer;
    private ExtractorsFactory extractorsFactory;
    private DataSource.Factory dataSourceFactory;

    private final MusicRepository musicRepository = new MusicRepository();

    @Override
    public void onCreate() {
        super.onCreate();

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        mediaSession = new MediaSessionCompat(this, "PlayerService");
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setCallback(mediaSessionCallback);

        Context appContext = getApplicationContext();

        Intent activityIntent = new Intent(appContext, MainActivity.class);
        mediaSession.setSessionActivity(PendingIntent.getActivity(appContext, 0, activityIntent, 0));

        Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON, null, appContext, MediaButtonReceiver.class);
        mediaSession.setMediaButtonReceiver(PendingIntent.getBroadcast(this, 0, mediaButtonIntent, 0));

        exoPlayer = ExoPlayerFactory.newSimpleInstance(new DefaultRenderersFactory(this), new DefaultTrackSelector(), new DefaultLoadControl());
        DataSource.Factory httpDataSourceFactory = new OkHttpDataSourceFactory(new OkHttpClient(), Util.getUserAgent(this, "AndroidAudioExample"), null);
        Cache cache = new SimpleCache(new File(this.getCacheDir().getAbsolutePath() + "/exoplayer"), new LeastRecentlyUsedCacheEvictor(1024 * 1024 * 100)); // 100 Mb max
        this.dataSourceFactory = new CacheDataSourceFactory(cache, httpDataSourceFactory, CacheDataSource.FLAG_BLOCK_ON_CACHE | CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
        this.extractorsFactory = new DefaultExtractorsFactory();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        MediaButtonReceiver.handleIntent(mediaSession, intent);
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mediaSession.release();
        exoPlayer.release();
    }

    private MediaSessionCompat.Callback mediaSessionCallback = new MediaSessionCompat.Callback() {

        private Uri currentUri;

        @Override
        public void onPlay() {
            if (!exoPlayer.getPlayWhenReady()) {
                MusicRepository.Track track = musicRepository.getCurrent();
                updateMetadataFromTrack(track);

                if (!track.getUri().equals(currentUri)) {
                    currentUri = track.getUri();
                    ExtractorMediaSource mediaSource = new ExtractorMediaSource(currentUri, dataSourceFactory, extractorsFactory, null, null);
                    exoPlayer.prepare(mediaSource);
                }

                int audioFocusResult = audioManager.requestAudioFocus(audioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
                if (audioFocusResult != AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
                    return;

                mediaSession.setActive(true); // Сразу после получения фокуса
                mediaSession.setPlaybackState(stateBuilder.setState(PlaybackStateCompat.STATE_PLAYING, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1).build());
                showPlayingNotification();

                registerReceiver(becomingNoisyReceiver, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));

                exoPlayer.setPlayWhenReady(true);
            }
        }

        @Override
        public void onPause() {
            if (exoPlayer.getPlayWhenReady()) {
                exoPlayer.setPlayWhenReady(false);
                mediaSession.setPlaybackState(stateBuilder.setState(PlaybackStateCompat.STATE_PAUSED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1).build());
                showPausedNotification();
                unregisterReceiver(becomingNoisyReceiver);
            }
        }

        @Override
        public void onStop() {
            if (exoPlayer.getPlayWhenReady()) {
                exoPlayer.setPlayWhenReady(false);

                audioManager.abandonAudioFocus(audioFocusChangeListener);
                hideNotification();

                mediaSession.setActive(false);
                mediaSession.setPlaybackState(stateBuilder.setState(PlaybackStateCompat.STATE_STOPPED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1).build());
            }
        }

        @Override
        public void onSkipToNext() {
            MusicRepository.Track track = musicRepository.getNext();
            updateMetadataFromTrack(track);

            currentUri = track.getUri();
            ExtractorMediaSource mediaSource = new ExtractorMediaSource(currentUri, dataSourceFactory, extractorsFactory, null, null);
            exoPlayer.prepare(mediaSource);
        }

        @Override
        public void onSkipToPrevious() {
            MusicRepository.Track track = musicRepository.getPrevious();
            updateMetadataFromTrack(track);

            currentUri = track.getUri();
            ExtractorMediaSource mediaSource = new ExtractorMediaSource(currentUri, dataSourceFactory, extractorsFactory, null, null);
            exoPlayer.prepare(mediaSource);
        }

        private void updateMetadataFromTrack(MusicRepository.Track track) {
            //Notification icon in card
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, BitmapFactory.decodeResource(getResources(), track.getBitmapResId()));
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, BitmapFactory.decodeResource(getResources(), track.getBitmapResId()));

            //lock screen icon for pre lollipop
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, BitmapFactory.decodeResource(getResources(), track.getBitmapResId()));
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, track.getTitle());
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, track.getSubtitle());
            metadataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, musicRepository.getCurrentTrackNumber());
            metadataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, musicRepository.getTrackCount());
            mediaSession.setMetadata(metadataBuilder.build());
        }
    };

    private AudioManager.OnAudioFocusChangeListener audioFocusChangeListener = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int focusChange) {
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_GAIN:
                    mediaSessionCallback.onPlay(); // Не очень красиво
                    break;
                case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
                    mediaSessionCallback.onPause();
                    break;
                default:
                    mediaSessionCallback.onPause();
                    break;
            }
        }
    };

    private final BroadcastReceiver becomingNoisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Disconnecting headphones - stop playback
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                mediaSessionCallback.onPause();
            }
        }
    };

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new PlayerServiceBinder();
    }

    public class PlayerServiceBinder extends Binder {
        public MediaSessionCompat.Token getMediaSessionToken() {
            return mediaSession.getSessionToken();
        }
    }

    private void showPlayingNotification() {
        NotificationCompat.Builder builder = MediaStyleHelper.from(this, mediaSession);
        builder.addAction(new NotificationCompat.Action(android.R.drawable.ic_media_pause, "Pause", MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY_PAUSE)));
        builder.setStyle(new NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0)
                .setShowCancelButton(true)
                .setCancelButtonIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_STOP))
                .setMediaSession(mediaSession.getSessionToken())); // setMediaSession требуется для Android Wear
        builder.setSmallIcon(R.mipmap.ic_launcher);
        builder.setColor(ContextCompat.getColor(this, R.color.colorPrimaryDark)); // The whole background (in MediaStyle), not just icon background

        startForeground(1, builder.build());
    }

    private void showPausedNotification() {
        NotificationCompat.Builder builder = MediaStyleHelper.from(this, mediaSession);
        builder.addAction(new NotificationCompat.Action(android.R.drawable.ic_media_play, "Play", MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY_PAUSE)));
        builder.setStyle(new NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0)
                .setShowCancelButton(true)
                .setCancelButtonIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_STOP))
                .setMediaSession(mediaSession.getSessionToken()));
        builder.setSmallIcon(R.mipmap.ic_launcher);
        builder.setColor(ContextCompat.getColor(this, R.color.colorPrimaryDark));

        NotificationManagerCompat.from(this).notify(1, builder.build());
        stopForeground(false);
    }

    private void hideNotification() {
        stopForeground(true);
    }
}
