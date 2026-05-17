package com.example.descargadorpro;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.os.Binder;
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

    private MediaPlayer mediaPlayer;
    private final IBinder binder = new MusicBinder();

    private List<File> playlist    = new ArrayList<>();
    private int        currentIndex = -1;

    private OnSongChangedListener songChangedListener;
    private MediaSessionCompat    mediaSession;

    // ─────────────────────────────────────────────
    //  Interfaz para la Activity
    // ─────────────────────────────────────────────

    public interface OnSongChangedListener {
        void onSongChanged(File newSong);
        void onPlaybackStateChanged(boolean isPlaying);
    }

    public class MusicBinder extends Binder {
        MusicService getService() { return MusicService.this; }
    }

    // ─────────────────────────────────────────────
    //  Ciclo de vida del servicio
    // ─────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();

        mediaPlayer = new MediaPlayer();
        mediaPlayer.setOnCompletionListener(mp -> playNext());

        // MediaSession: permite controles en pantalla de bloqueo y notificación
        mediaSession = new MediaSessionCompat(this, "DescargadorProSession");
        mediaSession.setActive(true);

        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override public void onPlay()             { if (!isPlaying()) togglePlayPause(); }
            @Override public void onPause()            { if (isPlaying())  togglePlayPause(); }
            @Override public void onSkipToNext()       { playNext(); }
            @Override public void onSkipToPrevious()   { playPrev(); }
            @Override public void onSeekTo(long pos)   { seekTo((int) pos); }
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

    // ─────────────────────────────────────────────
    //  Control de reproducción
    // ─────────────────────────────────────────────

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
        updateNotification(null); // Actualizar ícono play/pausa en notificación
    }

    public void playNext() {
        if (playlist.isEmpty()) return;
        currentIndex = (currentIndex + 1) % playlist.size();
        playSong();
    }

    public void playPrev() {
        if (playlist.isEmpty()) return;
        currentIndex = (currentIndex - 1 + playlist.size()) % playlist.size();
        playSong();
    }

    public void playSpecificIndex(int index) {
        if (index >= 0 && index < playlist.size()) {
            currentIndex = index;
            playSong();
        }
    }

    // ─────────────────────────────────────────────
    //  Getters
    // ─────────────────────────────────────────────

    public int  getCurrentPosition() { return mediaPlayer != null ? mediaPlayer.getCurrentPosition() : 0; }
    public int  getDuration()        { return mediaPlayer != null ? mediaPlayer.getDuration() : 0; }
    public void seekTo(int position) { if (mediaPlayer != null) mediaPlayer.seekTo(position); }
    public boolean isPlaying()       { return mediaPlayer != null && mediaPlayer.isPlaying(); }
    public int  getCurrentIndex()    { return currentIndex; }
    public List<File> getPlaylist()  { return playlist; }

    public File getCurrentSong() {
        if (currentIndex >= 0 && currentIndex < playlist.size())
            return playlist.get(currentIndex);
        return null;
    }

    // ─────────────────────────────────────────────
    //  MediaSession — Metadata + PlaybackState
    // ─────────────────────────────────────────────

    /**
     * Carga la carátula en un hilo secundario y actualiza MediaSession + notificación.
     */
    private void updateMediaSessionMetadata(File file) {
        String rawName = file.getName().replace(".mp3", "").replace(".m4a", "");

        new Thread(() -> {
            Bitmap art = loadBitmapFromFile(file);

            // Extraer artista desde metadatos
            String artist = "Descargador Pro";
            try (MediaMetadataRetriever r = new MediaMetadataRetriever()) {
                r.setDataSource(file.getAbsolutePath());
                String a = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
                if (a != null && !a.isEmpty()) artist = a;
            } catch (Exception ignored) {}

            final String finalArtist = artist;
            final Bitmap finalArt    = art;

            // Metadata en MediaSession (título, artista, carátula, duración)
            MediaMetadataCompat.Builder meta = new MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, rawName)
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
        int state = isPlaying() ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
        long pos  = mediaPlayer != null ? mediaPlayer.getCurrentPosition() : 0;

        PlaybackStateCompat playbackState = new PlaybackStateCompat.Builder()
                .setActions(
                        PlaybackStateCompat.ACTION_PLAY |
                        PlaybackStateCompat.ACTION_PAUSE |
                        PlaybackStateCompat.ACTION_PLAY_PAUSE |
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                        PlaybackStateCompat.ACTION_SEEK_TO)
                .setState(state, pos, 1.0f)
                .build();
        mediaSession.setPlaybackState(playbackState);
    }

    // ─────────────────────────────────────────────
    //  Notificación estilo Spotify
    // ─────────────────────────────────────────────

    private void updateNotification(Bitmap albumArt) {
        File currentFile = getCurrentSong();
        if (currentFile == null) return;

        String title  = currentFile.getName().replace(".mp3", "").replace(".m4a", "");
        String artist = "Descargador Pro";

        // Intent para abrir la app al tocar la notificación
        Intent openApp = new Intent(this, PlayerActivity.class);
        openApp.putExtra("FILE_PATH", "PLAYLIST_MODE");
        openApp.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent piOpen = PendingIntent.getActivity(this, 0, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Botón Anterior
        Intent prevIntent = new Intent(this, MusicService.class).setAction(ACTION_PREV);
        PendingIntent piPrev = PendingIntent.getService(this, 1, prevIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Botón Play/Pausa
        Intent playIntent = new Intent(this, MusicService.class).setAction(ACTION_PLAY_PAUSE);
        PendingIntent piPlay = PendingIntent.getService(this, 2, playIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Botón Siguiente
        Intent nextIntent = new Intent(this, MusicService.class).setAction(ACTION_NEXT);
        PendingIntent piNext = PendingIntent.getService(this, 3, nextIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        int playPauseIcon = isPlaying()
                ? android.R.drawable.ic_media_pause
                : android.R.drawable.ic_media_play;

        // Construir la notificación
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, MusicApplication.CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(artist)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setLargeIcon(albumArt)                          // ← carátula grande
                .setContentIntent(piOpen)
                .addAction(android.R.drawable.ic_media_previous, "Anterior", piPrev)
                .addAction(playPauseIcon, "Play/Pausa",          piPlay)
                .addAction(android.R.drawable.ic_media_next,    "Siguiente", piNext)
                // MediaStyle: conecta la notificación al MediaSession
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2))
                // Mostrar controles en pantalla de bloqueo
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(isPlaying())
                .setPriority(NotificationCompat.PRIORITY_LOW);

        startForeground(1, builder.build());
    }

    // ─────────────────────────────────────────────
    //  Utilidades
    // ─────────────────────────────────────────────

    private Bitmap loadBitmapFromFile(File file) {
        try (MediaMetadataRetriever r = new MediaMetadataRetriever()) {
            r.setDataSource(file.getAbsolutePath());
            byte[] raw = r.getEmbeddedPicture();
            if (raw != null) return BitmapFactory.decodeByteArray(raw, 0, raw.length);
        } catch (Exception ignored) {}
        return null;
    }

    // ─────────────────────────────────────────────
    //  Destrucción
    // ─────────────────────────────────────────────

    @Override
    public void onDestroy() {
        super.onDestroy();
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
