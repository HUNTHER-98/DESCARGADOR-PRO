package com.example.descargadorpro;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PlayerActivity extends AppCompatActivity implements MusicService.OnSongChangedListener {

    // Contenedores
    private View videoContainer;
    private View audioContainer;

    // Video
    private VideoView videoView;

    // Audio Premium
    private ImageView albumArt;
    private TextView audioTitle;
    private TextView audioArtist;
    private TextView textCurrentTime;
    private TextView textTotalTime;
    private SeekBar seekBar;
    private ImageButton btnPlayPause;
    private ImageButton btnNext;
    private ImageButton btnPrev;
    private RecyclerView playlistRecyclerView;

    // Servicio en segundo plano
    private MusicService musicService;
    private boolean isBound = false;

    private final Handler handler = new Handler();
    private Runnable seekBarUpdater;

    private List<File> playlist = new ArrayList<>();
    private int startIndex = 0;
    private PlaylistAdapter playlistAdapter;

    private final BroadcastReceiver newDownloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshPlaylist();
        }
    };

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.MusicBinder binder = (MusicService.MusicBinder) service;
            musicService = binder.getService();
            isBound = true;
            musicService.setListener(PlayerActivity.this);

            // Si el servicio ya tiene una playlist activa, usarla (reproducción en background)
            List<File> servicePlaylist = musicService.getPlaylist();
            if (servicePlaylist != null && !servicePlaylist.isEmpty()) {
                playlist.clear();
                playlist.addAll(servicePlaylist);
                if (playlistAdapter != null) playlistAdapter.notifyDataSetChanged();
                File currentSong = musicService.getCurrentSong();
                if (currentSong != null) {
                    onSongChanged(currentSong);
                    onPlaybackStateChanged(musicService.isPlaying());
                }
            } else {
                // Primera vez: pasar la playlist
                musicService.setPlaylist(playlist, startIndex);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        setContentView(R.layout.activity_player);

        videoContainer = findViewById(R.id.videoContainer);
        audioContainer = findViewById(R.id.audioContainer);
        videoView = findViewById(R.id.videoView);

        albumArt = findViewById(R.id.albumArt);
        audioTitle = findViewById(R.id.audioTitle);
        audioArtist = findViewById(R.id.audioArtist);
        textCurrentTime = findViewById(R.id.textCurrentTime);
        textTotalTime = findViewById(R.id.textTotalTime);
        seekBar = findViewById(R.id.seekBar);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnNext = findViewById(R.id.btnNext);
        btnPrev = findViewById(R.id.btnPrev);
        playlistRecyclerView = findViewById(R.id.playlistRecyclerView);

        String filePath = getIntent().getStringExtra("FILE_PATH");
        if (filePath == null || filePath.isEmpty()) {
            Toast.makeText(this, "No se pudo cargar el archivo", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (filePath.endsWith(".mp4") || filePath.endsWith(".mkv") || filePath.endsWith(".webm")) {
            audioContainer.setVisibility(View.GONE);
            videoContainer.setVisibility(View.VISIBLE);
            setupVideoPlayer(new File(filePath));
        } else {
            videoContainer.setVisibility(View.GONE);
            audioContainer.setVisibility(View.VISIBLE);
            setupAudioUI(filePath);
        }

        // Registrar el receptor para nuevas descargas en tiempo real
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(newDownloadReceiver, new IntentFilter("com.example.descargadorpro.ACTION_NEW_DOWNLOAD"), Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(newDownloadReceiver, new IntentFilter("com.example.descargadorpro.ACTION_NEW_DOWNLOAD"));
        }
    }

    // ─────────────────────────────────────────────
    //  VIDEO
    // ─────────────────────────────────────────────

    private void setupVideoPlayer(File file) {
        MediaController mediaController = new MediaController(this);
        mediaController.setAnchorView(videoView);
        videoView.setMediaController(mediaController);
        videoView.setVideoPath(file.getAbsolutePath());
        videoView.setOnPreparedListener(mp -> {
            videoView.start();
            videoView.setKeepScreenOn(true);
        });
        videoView.setOnCompletionListener(mp -> finish());
        videoView.setOnErrorListener((mp, what, extra) -> {
            Toast.makeText(this, "Error al reproducir video", Toast.LENGTH_SHORT).show();
            finish();
            return true;
        });
    }

    // ─────────────────────────────────────────────
    //  AUDIO + SERVICIO
    // ─────────────────────────────────────────────

    private void setupAudioUI(String initialFilePath) {
        // Cargar playlist desde Descargas
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (downloadsDir.exists() && downloadsDir.isDirectory()) {
            scanDirectory(downloadsDir);
        }
        Collections.sort(playlist, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));

        // Encontrar el índice inicial
        if (!initialFilePath.equals("PLAYLIST_MODE")) {
            for (int i = 0; i < playlist.size(); i++) {
                if (playlist.get(i).getAbsolutePath().equals(initialFilePath)) {
                    startIndex = i;
                    break;
                }
            }
        }

        // Configurar RecyclerView de playlist
        playlistRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        playlistAdapter = new PlaylistAdapter();
        playlistRecyclerView.setAdapter(playlistAdapter);

        // Botones de control
        btnPlayPause.setOnClickListener(v -> {
            if (isBound) musicService.togglePlayPause();
        });
        btnNext.setOnClickListener(v -> {
            if (isBound) musicService.playNext();
        });
        btnPrev.setOnClickListener(v -> {
            if (isBound) musicService.playPrev();
        });

        // SeekBar
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser && isBound) {
                    musicService.seekTo(progress);
                    textCurrentTime.setText(formatTime(progress));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { stopSeekBarUpdater(); }
            @Override public void onStopTrackingTouch(SeekBar sb) { startSeekBarUpdater(); }
        });

        // Iniciar y conectar el servicio
        Intent serviceIntent = new Intent(this, MusicService.class);
        startService(serviceIntent);
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void scanDirectory(File dir) {
        File[] found = dir.listFiles();
        if (found != null) {
            for (File f : found) {
                if (f.isDirectory()) {
                    scanDirectory(f); // Buscar recursivamente en subcarpetas
                } else if (f.isFile()) {
                    String name = f.getName().toLowerCase();
                    if (name.endsWith(".mp3") || name.endsWith(".m4a") || name.endsWith(".mp4")
                            || name.endsWith(".wav") || name.endsWith(".aac") || name.endsWith(".opus")
                            || name.endsWith(".ogg") || name.endsWith(".webm") || name.endsWith(".mkv")) {
                        playlist.add(f);
                    }
                }
            }
        }
    }

    private void refreshPlaylist() {
        runOnUiThread(() -> {
            playlist.clear();
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (downloadsDir.exists() && downloadsDir.isDirectory()) {
                scanDirectory(downloadsDir);
            }
            Collections.sort(playlist, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));

            // Actualizar la playlist del servicio si está conectado
            if (isBound && musicService != null) {
                File currentSong = musicService.getCurrentSong();
                int newIndex = -1;
                if (currentSong != null) {
                    for (int i = 0; i < playlist.size(); i++) {
                        if (playlist.get(i).getAbsolutePath().equals(currentSong.getAbsolutePath())) {
                            newIndex = i;
                            break;
                        }
                    }
                }
                musicService.updatePlaylistDataOnly(playlist, newIndex);
            }

            if (playlistAdapter != null) {
                playlistAdapter.notifyDataSetChanged();
            }
        });
    }

    // ─────────────────────────────────────────────
    //  OnSongChangedListener (callbacks del Service)
    // ─────────────────────────────────────────────

    @Override
    public void onSongChanged(File newSong) {
        runOnUiThread(() -> {
            String name = newSong.getName().replace(".mp3", "").replace(".m4a", "");
            audioTitle.setText(name);

            // Cargar carátula con MediaMetadataRetriever
            loadAlbumArt(newSong);

            // Actualizar duración
            if (isBound) {
                int duration = musicService.getDuration();
                seekBar.setMax(duration);
                textTotalTime.setText(formatTime(duration));
                seekBar.setProgress(0);
                textCurrentTime.setText("0:00");
            }

            // Sincronizar índice en la playlist visual
            for (int i = 0; i < playlist.size(); i++) {
                if (playlist.get(i).getAbsolutePath().equals(newSong.getAbsolutePath())) {
                    final int idx = i;
                    if (playlistAdapter != null) {
                        playlistAdapter.setCurrentIndex(idx);
                        playlistRecyclerView.scrollToPosition(idx);
                    }
                    break;
                }
            }

            startSeekBarUpdater();
        });
    }

    @Override
    public void onPlaybackStateChanged(boolean isPlaying) {
        runOnUiThread(() -> {
            if (isPlaying) {
                btnPlayPause.setImageResource(R.drawable.ic_pause);
                startSeekBarUpdater();
            } else {
                btnPlayPause.setImageResource(R.drawable.ic_play);
                stopSeekBarUpdater();
            }
        });
    }

    // ─────────────────────────────────────────────
    //  Carátula
    // ─────────────────────────────────────────────

    private void loadAlbumArt(File audioFile) {
        // Ejecutar en hilo de fondo para no bloquear la UI
        new Thread(() -> {
            Bitmap art = null;
            try (MediaMetadataRetriever retriever = new MediaMetadataRetriever()) {
                retriever.setDataSource(audioFile.getAbsolutePath());
                byte[] rawArt = retriever.getEmbeddedPicture();
                if (rawArt != null) {
                    art = BitmapFactory.decodeByteArray(rawArt, 0, rawArt.length);
                }
                // Extraer artista si está disponible
                final String artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
                final Bitmap finalArt = art;
                runOnUiThread(() -> {
                    if (finalArt != null) {
                        albumArt.setImageBitmap(finalArt);
                        albumArt.setPadding(0, 0, 0, 0); // quitar padding cuando hay carátula real
                        albumArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    } else {
                        albumArt.setImageResource(R.drawable.ic_music_placeholder);
                        albumArt.setPadding(60, 60, 60, 60);
                        albumArt.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                    }
                    if (artist != null && !artist.isEmpty()) {
                        audioArtist.setText(artist);
                    } else {
                        audioArtist.setText("Descargador Pro");// Si no hay artista, poner "Descargador Pro"
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    albumArt.setImageResource(R.drawable.ic_music_placeholder);// Si hay un error, poner "Descargador Pro"
                    audioArtist.setText("Descargador Pro");
                });
            }
        }).start();
    }

    // ─────────────────────────────────────────────
    //  SeekBar Updater
    // ─────────────────────────────────────────────

    private void startSeekBarUpdater() {
        stopSeekBarUpdater();
        seekBarUpdater = new Runnable() {
            @Override
            public void run() {
                if (isBound && musicService.isPlaying()) {
                    int pos = musicService.getCurrentPosition();
                    seekBar.setProgress(pos);
                    textCurrentTime.setText(formatTime(pos));
                    handler.postDelayed(this, 500);
                }
            }
        };
        handler.post(seekBarUpdater);
    }

    private void stopSeekBarUpdater() {
        if (seekBarUpdater != null) {
            handler.removeCallbacks(seekBarUpdater);
            seekBarUpdater = null;
        }
    }

    private String formatTime(int ms) {
        int totalSeconds = ms / 1000;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    // ─────────────────────────────────────────────
    //  Lifecycle
    // ─────────────────────────────────────────────

    @Override
    protected void onResume() {
        super.onResume();
        // Re-sincronizar UI si el servicio ya estaba corriendo
        if (isBound && musicService != null) {
            File current = musicService.getCurrentSong();
            if (current != null) {
                onSongChanged(current);
                onPlaybackStateChanged(musicService.isPlaying());
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopSeekBarUpdater();
        // No desconectar el servicio al salir, para que siga en segundo plano
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopSeekBarUpdater();
        if (isBound) {
            musicService.setListener(null);
            unbindService(serviceConnection);
            isBound = false;
        }
        if (videoView != null) {
            videoView.stopPlayback();
        }
        try {
            unregisterReceiver(newDownloadReceiver);
        } catch (Exception ignored) {}
    }

    // ─────────────────────────────────────────────
    //  Adapter de Playlist
    // ─────────────────────────────────────────────

    private class PlaylistAdapter extends RecyclerView.Adapter<PlaylistAdapter.ViewHolder> {

        private int currentIndex = 0;

        public void setCurrentIndex(int index) {
            int old = this.currentIndex;
            this.currentIndex = index;
            notifyItemChanged(old);
            notifyItemChanged(index);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_playlist, parent, false);// ← layout personalizado
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            File file = playlist.get(position);
            holder.title.setText(file.getName().replace(".mp3", "").replace(".m4a", "").replace(".mp4", ""));

            if (position == currentIndex) {
                holder.title.setTextColor(0xFF667EEA);
                holder.playingIndicator.setVisibility(View.VISIBLE);
            } else {
                holder.title.setTextColor(0xFFFFFFFF);
                holder.playingIndicator.setVisibility(View.INVISIBLE);
            }

            // Inicializar vistas con placeholders mientras se carga la carátula real
            holder.icon.setImageResource(R.drawable.ic_music_placeholder);
            holder.icon.setPadding(12, 12, 12, 12);
            holder.icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            holder.artist.setText("Descargador Pro");

            final String filePath = file.getAbsolutePath();
            holder.icon.setTag(filePath);

            // Cargar metadatos y carátula en segundo plano para scroll fluido a 60 FPS
            new Thread(() -> {
                Bitmap art = null;
                String artist = null;
                try (MediaMetadataRetriever retriever = new MediaMetadataRetriever()) {
                    retriever.setDataSource(filePath);
                    byte[] rawArt = retriever.getEmbeddedPicture();
                    if (rawArt != null) {
                        art = BitmapFactory.decodeByteArray(rawArt, 0, rawArt.length);
                    }
                    artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
                } catch (Exception ignored) {}

                final Bitmap finalArt = art;
                final String finalArtist = artist;

                runOnUiThread(() -> {
                    // Comprobar tag para evitar fallos por reciclaje de celdas
                    if (filePath.equals(holder.icon.getTag())) {
                        if (finalArt != null) {
                            holder.icon.setImageBitmap(finalArt);
                            holder.icon.setPadding(0, 0, 0, 0);
                            holder.icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        } else {
                            holder.icon.setImageResource(R.drawable.ic_music_placeholder);
                            holder.icon.setPadding(12, 12, 12, 12);
                            holder.icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                        }

                        if (finalArtist != null && !finalArtist.isEmpty()) {
                            holder.artist.setText(finalArtist);
                        } else {
                            holder.artist.setText("Descargador Pro");
                        }
                    }
                });
            }).start();

            holder.itemView.setOnClickListener(v -> {
                if (isBound && position != currentIndex) {
                    musicService.playSpecificIndex(position);
                }
            });
        }

        @Override
        public int getItemCount() {
            return playlist.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView title;
            TextView artist;
            ImageView icon;
            ImageView playingIndicator;

            ViewHolder(View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.playlistItemTitle);
                artist = itemView.findViewById(R.id.playlistItemArtist);
                icon = itemView.findViewById(R.id.playlistItemIcon);
                playingIndicator = itemView.findViewById(R.id.playingIndicator);
            }
        }
    }
}
