package com.example.descargadorpro;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.media.audiofx.Equalizer;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.core.app.NotificationCompat;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MusicService extends Service {

    public static final String ACTION_PLAY_PAUSE = "action_play_pause";
    public static final String ACTION_NEXT       = "action_next";
    public static final String ACTION_PREV       = "action_prev";

    // ─── Repeat modes ───
    public static final int REPEAT_NONE = 0;
    public static final int REPEAT_ALL  = 1;
    public static final int REPEAT_ONE  = 2;

    private MediaPlayer mediaPlayer;
    private final IBinder binder = new MusicBinder();

    private List<File> playlist     = new ArrayList<>();
    private int        currentIndex = -1;

    private OnSongChangedListener songChangedListener;
    private MediaSessionCompat    mediaSession;

    // ─── Repeat & Shuffle ───
    private int     repeatMode  = REPEAT_NONE;
    private boolean shuffleMode = false;

    // ─── Playback Speed ───
    private float playbackSpeed = 1.0f;

    // ─── Equalizer ───
    private Equalizer equalizer;
    private boolean   equalizerEnabled = false;

    // ─── Sleep Timer ───
    private final Handler  sleepHandler  = new Handler();
    private       Runnable sleepRunnable = null;

    // ═══════════════════════════════════════════════════════
    //  Interfaz para la Activity
    // ═══════════════════════════════════════════════════════

    public interface OnSongChangedListener {
        void onSongChanged(File newSong);
        void onPlaybackStateChanged(boolean isPlaying);
    }

    public class MusicBinder extends Binder {
        MusicService getService() { return MusicService.this; }
    }

    // ═══════════════════════════════════════════════════════
    //  Ciclo de vida del servicio
    // ═══════════════════════════════════════════════════════

    @Override
    public void onCreate() {
        super.onCreate();

        mediaPlayer = new MediaPlayer();
        mediaPlayer.setOnCompletionListener(mp -> handleSongCompletion());

        // MediaSession: permite controles en pantalla de bloqueo y notificación
        mediaSession = new MediaSessionCompat(this, "DescargadorProSession");
        mediaSession.setActive(true);

        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override public void onPlay()           { if (!isPlaying()) togglePlayPause(); }
            @Override public void onPause()          { if (isPlaying())  togglePlayPause(); }
            @Override public void onSkipToNext()     { playNext(); }
            @Override public void onSkipToPrevious() { playPrev(); }
            @Override public void onSeekTo(long pos) { seekTo((int) pos); }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case ACTION_PLAY_PAUSE: togglePlayPause(); break;
                case ACTION_NEXT:       playNext();        break;
                case ACTION_PREV:       playPrev();        break;
            }
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return binder; }

    // ═══════════════════════════════════════════════════════
    //  Control de reproducción
    // ═══════════════════════════════════════════════════════

    public void setListener(OnSongChangedListener listener) {
        this.songChangedListener = listener;
    }

    public void setPlaylist(List<File> playlist, int startIndex) {
        this.playlist     = playlist;
        this.currentIndex = startIndex;
        playSong();
    }

    public void updatePlaylistDataOnly(List<File> newPlaylist, int newIndex) {
        this.playlist     = newPlaylist;
        this.currentIndex = newIndex;
    }

    private void playSong() {
        if (playlist.isEmpty() || currentIndex < 0 || currentIndex >= playlist.size()) return;

        File file = playlist.get(currentIndex);
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.reset();
            try {
                mediaPlayer.setDataSource(file.getAbsolutePath());
                mediaPlayer.prepare();
                mediaPlayer.start();

                // Inicializar o re-inicializar el ecualizador con la nueva sesión de audio
                initEqualizer();

                // Aplicar velocidad de reproducción guardada
                applyPlaybackSpeed(playbackSpeed);

                // Actualizar MediaSession y notificación con carátula
                updateMediaSessionMetadata(file);

                if (songChangedListener != null) {
                    songChangedListener.onSongChanged(file);
                    songChangedListener.onPlaybackStateChanged(true);
                }
            } catch (Exception e) {
                e.printStackTrace();
                playNext();
            }
        }
    }

    /**
     * Maneja el fin de canción respetando el modo de repetición.
     */
    private void handleSongCompletion() {
        if (repeatMode == REPEAT_ONE) {
            // Repetir la misma canción
            playSong();
        } else if (repeatMode == REPEAT_ALL) {
            // Avanzar ciclicamente (incluso si es la última)
            currentIndex = (currentIndex + 1) % playlist.size();
            playSong();
        } else {
            // REPEAT_NONE: avanzar solo si no es la última
            if (currentIndex < playlist.size() - 1) {
                currentIndex++;
                playSong();
            } else {
                // Fin de lista: notificar pausa
                if (songChangedListener != null) songChangedListener.onPlaybackStateChanged(false);
                updatePlaybackState();
            }
        }
    }

    public void togglePlayPause() {
        if (mediaPlayer == null) return;
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            if (songChangedListener != null) songChangedListener.onPlaybackStateChanged(false);
        } else {
            mediaPlayer.start();
            if (songChangedListener != null) songChangedListener.onPlaybackStateChanged(true);
        }
        updatePlaybackState();
        updateNotification(null);
    }

    public void playNext() {
        if (playlist.isEmpty()) return;
        if (repeatMode == REPEAT_ONE) {
            playSong();
        } else {
            currentIndex = (currentIndex + 1) % playlist.size();
            playSong();
        }
    }

    public void playPrev() {
        if (playlist.isEmpty()) return;
        // Si llevamos más de 3 segundos reproducidos, volver al inicio de la canción actual
        if (mediaPlayer != null && mediaPlayer.getCurrentPosition() > 3000) {
            mediaPlayer.seekTo(0);
            return;
        }
        currentIndex = (currentIndex - 1 + playlist.size()) % playlist.size();
        playSong();
    }

    public void playSpecificIndex(int index) {
        if (index >= 0 && index < playlist.size()) {
            currentIndex = index;
            playSong();
        }
    }

    // ═══════════════════════════════════════════════════════
    //  Getters básicos
    // ═══════════════════════════════════════════════════════

    public int        getCurrentPosition() { return mediaPlayer != null ? mediaPlayer.getCurrentPosition() : 0; }
    public int        getDuration()        { return mediaPlayer != null ? mediaPlayer.getDuration() : 0; }
    public void       seekTo(int position) { if (mediaPlayer != null) mediaPlayer.seekTo(position); }
    public boolean    isPlaying()          { return mediaPlayer != null && mediaPlayer.isPlaying(); }
    public int        getCurrentIndex()    { return currentIndex; }
    public List<File> getPlaylist()        { return playlist; }

    public File getCurrentSong() {
        if (currentIndex >= 0 && currentIndex < playlist.size())
            return playlist.get(currentIndex);
        return null;
    }

    // ═══════════════════════════════════════════════════════
    //  Repeat & Shuffle
    // ═══════════════════════════════════════════════════════

    public int  getRepeatMode()         { return repeatMode; }
    public void setRepeatMode(int mode) { this.repeatMode = mode; }

    public boolean isShuffle()            { return shuffleMode; }
    public void    setShuffle(boolean on) { this.shuffleMode = on; }

    // ═══════════════════════════════════════════════════════
    //  Playback Speed
    // ═══════════════════════════════════════════════════════

    public float getPlaybackSpeed() { return playbackSpeed; }

    public void setPlaybackSpeed(float speed) {
        this.playbackSpeed = speed;
        applyPlaybackSpeed(speed);
    }

    private void applyPlaybackSpeed(float speed) {
        if (mediaPlayer != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                PlaybackParams params = new PlaybackParams();
                params.setSpeed(speed);
                mediaPlayer.setPlaybackParams(params);
            } catch (Exception ignored) {}
        }
    }

    // ═══════════════════════════════════════════════════════
    //  Equalizer
    // ═══════════════════════════════════════════════════════

    private void initEqualizer() {
        if (mediaPlayer == null) return;
        // Liberar instancia anterior si existe
        if (equalizer != null) {
            try { equalizer.release(); } catch (Exception ignored) {}
            equalizer = null;
        }
        try {
            equalizer = new Equalizer(0, mediaPlayer.getAudioSessionId());
            equalizer.setEnabled(equalizerEnabled);
        } catch (Exception e) {
            equalizer = null;
        }
    }

    public boolean isEqualizerSupported() { return equalizer != null; }
    public boolean isEqualizerEnabled()   { return equalizerEnabled; }

    public void setEqualizerEnabled(boolean enabled) {
        equalizerEnabled = enabled;
        if (equalizer != null) equalizer.setEnabled(enabled);
    }

    public short getEqualizerNumberOfBands() {
        return equalizer != null ? equalizer.getNumberOfBands() : 0;
    }

    public int[] getEqualizerBandLevelRange() {
        if (equalizer != null) {
            short[] range = equalizer.getBandLevelRange();
            return new int[]{ range[0], range[1] };
        }
        return new int[]{ -1500, 1500 };
    }

    public int getEqualizerCenterFreq(short band) {
        return equalizer != null ? equalizer.getCenterFreq(band) : 0;
    }

    public int getEqualizerBandLevel(short band) {
        return equalizer != null ? equalizer.getBandLevel(band) : 0;
    }

    public void setEqualizerBandLevel(short band, short level) {
        if (equalizer != null) equalizer.setBandLevel(band, level);
    }

    public short getEqualizerNumberOfPresets() {
        return equalizer != null ? equalizer.getNumberOfPresets() : 0;
    }

    public int getEqualizerCurrentPreset() {
        return equalizer != null ? equalizer.getCurrentPreset() : -1;
    }

    public String getEqualizerPresetName(short preset) {
        return equalizer != null ? equalizer.getPresetName(preset) : "";
    }

    public void useEqualizerPreset(short preset) {
        if (equalizer != null) equalizer.usePreset(preset);
    }

    // ═══════════════════════════════════════════════════════
    //  Sleep Timer
    // ═══════════════════════════════════════════════════════

    public void startSleepTimer(long minutes) {
        stopSleepTimer();
        sleepRunnable = () -> {
            if (isPlaying()) togglePlayPause();
        };
        sleepHandler.postDelayed(sleepRunnable, minutes * 60 * 1000L);
    }

    public void stopSleepTimer() {
        if (sleepRunnable != null) {
            sleepHandler.removeCallbacks(sleepRunnable);
            sleepRunnable = null;
        }
    }

    // ═══════════════════════════════════════════════════════
    //  MediaSession — Metadata + PlaybackState
    // ═══════════════════════════════════════════════════════

    private void updateMediaSessionMetadata(File file) {
        String rawName = file.getName().replace(".mp3", "").replace(".m4a", "");

        new Thread(() -> {
            Bitmap art = loadBitmapFromFile(file);

            String artist = "Descargador Pro";
            try (MediaMetadataRetriever r = new MediaMetadataRetriever()) {
                r.setDataSource(file.getAbsolutePath());
                String a = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
                if (a != null && !a.isEmpty()) artist = a;
            } catch (Exception ignored) {}

            final String finalArtist = artist;
            final Bitmap finalArt    = art;

            MediaMetadataCompat.Builder meta = new MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE,  rawName)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, finalArtist)
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION,
                            mediaPlayer != null ? mediaPlayer.getDuration() : 0);
            if (finalArt != null) {
                meta.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, finalArt);
            }
            mediaSession.setMetadata(meta.build());

            updatePlaybackState();
            updateNotification(finalArt);
        }).start();
    }

    private void updatePlaybackState() {
        int  state = isPlaying() ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
        long pos   = mediaPlayer != null ? mediaPlayer.getCurrentPosition() : 0;

        PlaybackStateCompat playbackState = new PlaybackStateCompat.Builder()
                .setActions(
                        PlaybackStateCompat.ACTION_PLAY             |
                                PlaybackStateCompat.ACTION_PAUSE            |
                                PlaybackStateCompat.ACTION_PLAY_PAUSE       |
                                PlaybackStateCompat.ACTION_SKIP_TO_NEXT     |
                                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                                PlaybackStateCompat.ACTION_SEEK_TO)
                .setState(state, pos, 1.0f)
                .build();
        mediaSession.setPlaybackState(playbackState);
    }

    // ═══════════════════════════════════════════════════════
    //  Notificación estilo Spotify
    // ═══════════════════════════════════════════════════════

    private void updateNotification(Bitmap albumArt) {
        File currentFile = getCurrentSong();
        if (currentFile == null) return;

        String title  = currentFile.getName().replace(".mp3", "").replace(".m4a", "");
        String artist = "Descargador Pro";

        Intent openApp = new Intent(this, PlayerActivity.class);
        openApp.putExtra("FILE_PATH", "PLAYLIST_MODE");
        openApp.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent piOpen = PendingIntent.getActivity(this, 0, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent prevIntent = new Intent(this, MusicService.class).setAction(ACTION_PREV);
        PendingIntent piPrev = PendingIntent.getService(this, 1, prevIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent playIntent = new Intent(this, MusicService.class).setAction(ACTION_PLAY_PAUSE);
        PendingIntent piPlay = PendingIntent.getService(this, 2, playIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent nextIntent = new Intent(this, MusicService.class).setAction(ACTION_NEXT);
        PendingIntent piNext = PendingIntent.getService(this, 3, nextIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        int playPauseIcon = isPlaying()
                ? android.R.drawable.ic_media_pause
                : android.R.drawable.ic_media_play;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, MusicApplication.CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(artist)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setLargeIcon(albumArt)
                .setContentIntent(piOpen)
                .addAction(android.R.drawable.ic_media_previous, "Anterior",  piPrev)
                .addAction(playPauseIcon,                        "Play/Pausa", piPlay)
                .addAction(android.R.drawable.ic_media_next,    "Siguiente",  piNext)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(isPlaying())
                .setPriority(NotificationCompat.PRIORITY_LOW);

        startForeground(1, builder.build());
    }

    // ═══════════════════════════════════════════════════════
    //  Utilidades
    // ═══════════════════════════════════════════════════════

    private Bitmap loadBitmapFromFile(File file) {
        try (MediaMetadataRetriever r = new MediaMetadataRetriever()) {
            r.setDataSource(file.getAbsolutePath());
            byte[] raw = r.getEmbeddedPicture();
            if (raw != null) return BitmapFactory.decodeByteArray(raw, 0, raw.length);
        } catch (Exception ignored) {}
        return null;
    }

    // ═══════════════════════════════════════════════════════
    //  Destrucción
    // ═══════════════════════════════════════════════════════

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopSleepTimer();
        if (equalizer != null) {
            equalizer.release();
            equalizer = null;
        }
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
        }
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
}