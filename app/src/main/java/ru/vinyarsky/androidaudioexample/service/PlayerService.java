package ru.vinyarsky.androidaudioexample.service;

import android.app.Notification;
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
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.ext.okhttp.OkHttpDataSourceFactory;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
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

    private final int NOTIFICATION_ID = 404;

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
        mediaSession.setMediaButtonReceiver(PendingIntent.getBroadcast(appContext, 0, mediaButtonIntent, 0));

        exoPlayer = ExoPlayerFactory.newSimpleInstance(new DefaultRenderersFactory(this), new DefaultTrackSelector(), new DefaultLoadControl());
        exoPlayer.addListener(exoPlayerListener);
        DataSource.Factory httpDataSourceFactory = new OkHttpDataSourceFactory(new OkHttpClient(), Util.getUserAgent(this, getString(R.string.app_name)), null);
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
        int currentState = PlaybackStateCompat.STATE_STOPPED;

        @Override
        public void onPlay() {
            if (!exoPlayer.getPlayWhenReady()) {
                startService(new Intent(getApplicationContext(), PlayerService.class));

                MusicRepository.Track track = musicRepository.getCurrent();
                updateMetadataFromTrack(track);

                prepareToPlay(track.getUri());

                int audioFocusResult = audioManager.requestAudioFocus(audioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
                if (audioFocusResult != AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
                    return;

                mediaSession.setActive(true); // Сразу после получения фокуса
                mediaSession.setPlaybackState(stateBuilder.setState(PlaybackStateCompat.STATE_PLAYING, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1).build());
                currentState = PlaybackStateCompat.STATE_PLAYING;
                refreshNotificationAndForegroundStatus(currentState);

                registerReceiver(becomingNoisyReceiver, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));

                exoPlayer.setPlayWhenReady(true);
            }
        }

        @Override
        public void onPause() {
            if (exoPlayer.getPlayWhenReady()) {
                exoPlayer.setPlayWhenReady(false);

                mediaSession.setPlaybackState(stateBuilder.setState(PlaybackStateCompat.STATE_PAUSED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1).build());
                currentState = PlaybackStateCompat.STATE_PAUSED;

                refreshNotificationAndForegroundStatus(currentState);

                unregisterReceiver(becomingNoisyReceiver);
            }
        }

        @Override
        public void onStop() {
            exoPlayer.setPlayWhenReady(false);

            audioManager.abandonAudioFocus(audioFocusChangeListener);

            mediaSession.setActive(false);

            mediaSession.setPlaybackState(stateBuilder.setState(PlaybackStateCompat.STATE_STOPPED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1).build());
            currentState = PlaybackStateCompat.STATE_STOPPED;

            refreshNotificationAndForegroundStatus(currentState);

            stopSelf();
        }

        @Override
        public void onSkipToNext() {
            MusicRepository.Track track = musicRepository.getNext();
            updateMetadataFromTrack(track);

            refreshNotificationAndForegroundStatus(currentState);

            prepareToPlay(track.getUri());
        }

        @Override
        public void onSkipToPrevious() {
            MusicRepository.Track track = musicRepository.getPrevious();
            updateMetadataFromTrack(track);

            refreshNotificationAndForegroundStatus(currentState);

            prepareToPlay(track.getUri());
        }

        private void prepareToPlay(Uri uri) {
            if (!uri.equals(currentUri)) {
                currentUri = uri;
                ExtractorMediaSource mediaSource = new ExtractorMediaSource(uri, dataSourceFactory, extractorsFactory, null, null);
                exoPlayer.prepare(mediaSource);
            }
        }

        private void updateMetadataFromTrack(MusicRepository.Track track) {
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, BitmapFactory.decodeResource(getResources(), track.getBitmapResId()));
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.getTitle());
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.getArtist());
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.getArtist());
            metadataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, track.getDuration());
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
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
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

    private ExoPlayer.EventListener exoPlayerListener = new ExoPlayer.EventListener() {
        @Override
        public void onTimelineChanged(Timeline timeline, Object manifest) {
        }

        @Override
        public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
        }

        @Override
        public void onLoadingChanged(boolean isLoading) {
        }

        @Override
        public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
            if (playWhenReady && playbackState == ExoPlayer.STATE_ENDED) {
                mediaSessionCallback.onSkipToNext();
            }
        }

        @Override
        public void onPlayerError(ExoPlaybackException error) {
        }

        @Override
        public void onPositionDiscontinuity() {
        }

        @Override
        public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
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

    private void refreshNotificationAndForegroundStatus(int playbackState) {
        switch (playbackState) {
            case PlaybackStateCompat.STATE_PLAYING: {
                startForeground(NOTIFICATION_ID, getNotification(playbackState));
                break;
            }
            case PlaybackStateCompat.STATE_PAUSED: {
                NotificationManagerCompat.from(PlayerService.this).notify(NOTIFICATION_ID, getNotification(playbackState));
                stopForeground(false);
                break;
            }
            default: {
                stopForeground(true);
                break;
            }
        }
    }

    private Notification getNotification(int playbackState) {
        NotificationCompat.Builder builder = MediaStyleHelper.from(this, mediaSession);
        builder.addAction(new NotificationCompat.Action(android.R.drawable.ic_media_previous, getString(R.string.previous), MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)));

        if (playbackState == PlaybackStateCompat.STATE_PLAYING)
            builder.addAction(new NotificationCompat.Action(android.R.drawable.ic_media_pause, getString(R.string.pause), MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY_PAUSE)));
        else
            builder.addAction(new NotificationCompat.Action(android.R.drawable.ic_media_play, getString(R.string.play), MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY_PAUSE)));

        builder.addAction(new NotificationCompat.Action(android.R.drawable.ic_media_next, getString(R.string.next), MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_NEXT)));
        builder.setStyle(new NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(1)
                .setShowCancelButton(true)
                .setCancelButtonIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_STOP))
                .setMediaSession(mediaSession.getSessionToken())); // setMediaSession требуется для Android Wear
        builder.setSmallIcon(R.mipmap.ic_launcher);
        builder.setColor(ContextCompat.getColor(this, R.color.colorPrimaryDark)); // The whole background (in MediaStyle), not just icon background
        builder.setShowWhen(false);
        builder.setPriority(NotificationCompat.PRIORITY_HIGH);
        builder.setOnlyAlertOnce(true);

        return builder.build();
    }
}
