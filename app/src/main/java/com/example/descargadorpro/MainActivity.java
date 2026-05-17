package com.example.descargadorpro;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;
import com.google.android.material.textfield.TextInputEditText;
import com.yausername.ffmpeg.FFmpeg;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLException;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import com.yausername.youtubedl_android.YoutubeDL.UpdateChannel;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    // Drawer
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private ActionBarDrawerToggle drawerToggle;

    // Controles de descarga
    private TextInputEditText urlInput;
    private Spinner qualitySpinner;
    private Button btnDownload;
    private Button btnUpdate;
    private Button btnOpenPlayer;
    private ProgressBar progressBar;
    private TextView statusText;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    // Cola de descargas
    private androidx.cardview.widget.CardView queueCardView;
    private TextView queueTitle;
    private android.widget.LinearLayout queueContainer;
    private final java.util.List<DownloadTask> downloadQueue = new java.util.ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // ── Toolbar + Drawer ──────────────────────────
        Toolbar toolbar = findViewById(R.id.mainToolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navigationView);

        drawerToggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar,
                R.string.nav_open, R.string.nav_close);
        drawerToggle.getDrawerArrowDrawable().setColor(getResources().getColor(android.R.color.white, null));
        drawerLayout.addDrawerListener(drawerToggle);
        drawerToggle.syncState();

        // Marcar "Descargar" como seleccionado por defecto
        navigationView.setCheckedItem(R.id.nav_home);

        navigationView.setNavigationItemSelectedListener(item -> {
            drawerLayout.closeDrawers();
            int id = item.getItemId();

            if (id == R.id.nav_downloads) {
                startActivity(new Intent(this, DownloadsActivity.class));
            } else if (id == R.id.nav_player) {
                Intent intent = new Intent(this, PlayerActivity.class);
                intent.putExtra("FILE_PATH", "PLAYLIST_MODE");
                startActivity(intent);
            } else if (id == R.id.nav_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
            }
            // nav_home: ya estamos aquí, solo cierra el drawer
            return true;
        });

        // ── Vistas de descarga ────────────────────────
        urlInput      = findViewById(R.id.urlInput);
        qualitySpinner = findViewById(R.id.qualitySpinner);
        btnDownload   = findViewById(R.id.btnDownload);
        btnUpdate     = findViewById(R.id.btnUpdate);
        btnOpenPlayer = findViewById(R.id.btnOpenPlayer);
        progressBar   = findViewById(R.id.progressBar);
        statusText    = findViewById(R.id.statusText);

        setupQualitySpinner();
        checkPermissions();
        initYoutubeDL();

        btnDownload.setOnClickListener(v -> startDownload());
        btnUpdate.setOnClickListener(v -> updateYoutubeDL());
        btnOpenPlayer.setOnClickListener(v -> {
            Intent intent = new Intent(this, PlayerActivity.class);
            intent.putExtra("FILE_PATH", "PLAYLIST_MODE");
            startActivity(intent);
        });

        // ── Inicialización de la Cola de Descarga ──────
        queueCardView = findViewById(R.id.queueCardView);
        queueTitle = findViewById(R.id.queueTitle);
        queueContainer = findViewById(R.id.queueContainer);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Recargar calidad por defecto si cambió en los ajustes
        android.content.SharedPreferences prefs = getSharedPreferences("descargador_prefs", Context.MODE_PRIVATE);
        int defQuality = prefs.getInt("default_quality", 0);
        if (qualitySpinner != null && defQuality >= 0 && defQuality < qualitySpinner.getCount()) {
            qualitySpinner.setSelection(defQuality);
        }
    }

    private void setupQualitySpinner() {
        String[] options = {"Mejor Calidad (Auto)", "1080p", "720p", "480p", "Solo Audio (MP3)"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, options);
        qualitySpinner.setAdapter(adapter);

        // Cargar selección por defecto desde configuraciones
        android.content.SharedPreferences prefs = getSharedPreferences("descargador_prefs", Context.MODE_PRIVATE);
        int defQuality = prefs.getInt("default_quality", 0);
        if (defQuality >= 0 && defQuality < options.length) {
            qualitySpinner.setSelection(defQuality);
        }
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ (API 30+) - Requiere Acceso a Todos los Archivos para listFiles()
            if (!Environment.isExternalStorageManager()) {
                try {
                    Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.addCategory("android.intent.category.DEFAULT");
                    intent.setData(android.net.Uri.parse(String.format("package:%s", getPackageName())));
                    startActivity(intent);
                } catch (Exception e) {
                    Intent intent = new Intent();
                    intent.setAction(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivity(intent);
                }
                Toast.makeText(this, "Permite el acceso a todos los archivos para poder escanear y guardar tu música", Toast.LENGTH_LONG).show();
            }

            // Solicitar POST_NOTIFICATIONS en Android 13+ para controles estilo Spotify
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
                }
            }
        } else {
            // Android 10 y anteriores
            java.util.ArrayList<String> permissions = new java.util.ArrayList<>();
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
            if (!permissions.isEmpty()) {
                ActivityCompat.requestPermissions(this, permissions.toArray(new String[0]), 1);
            }
        }
    }

    private void initYoutubeDL() {
        try {
            YoutubeDL.getInstance().init(this);
            FFmpeg.getInstance().init(this);
            // Actualización silenciosa al iniciar para evitar descargas lentas por extractores desactualizados
            silentUpdateYoutubeDL();
        } catch (YoutubeDLException e) {
            Log.e("YoutubeDL", "Error init", e);
        }
    }

    private void silentUpdateYoutubeDL() {
        executorService.execute(() -> {
            try {
                Log.d("YoutubeDL", "Iniciando actualización silenciosa de yt-dlp...");
                YoutubeDL.getInstance().updateYoutubeDL(this, UpdateChannel.STABLE.INSTANCE);
                Log.d("YoutubeDL", "Actualización silenciosa de yt-dlp completada con éxito.");
            } catch (Exception e) {
                Log.e("YoutubeDL", "Error en actualización silenciosa de yt-dlp", e);
            }
        });
    }

    private void updateYoutubeDL() {
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setProgress(0);
        statusText.setVisibility(View.VISIBLE);
        statusText.setText("Actualizando yt-dlp... (Por favor, espera)");
        btnUpdate.setEnabled(false);
        btnDownload.setEnabled(false);

        executorService.execute(() -> {
            try {
                YoutubeDL.getInstance().updateYoutubeDL(this, UpdateChannel.STABLE.INSTANCE);
                runOnUiThread(() -> {
                    statusText.setText("¡Actualización completada! Ya puedes descargar.");
                    btnUpdate.setEnabled(true);
                    btnDownload.setEnabled(true);
                    progressBar.setVisibility(View.GONE);
                });
            } catch (Exception e) {
                Log.e("YoutubeDL", "Error updating", e);
                runOnUiThread(() -> {
                    statusText.setText("Error al actualizar: " + e.getMessage());
                    btnUpdate.setEnabled(true);
                    btnDownload.setEnabled(true);
                });
            }
        });
    }

    private void startDownload() {
        String url = urlInput.getText() != null ? urlInput.getText().toString().trim() : "";
        if (url.isEmpty()) {
            Toast.makeText(this, "Por favor ingresa un enlace", Toast.LENGTH_SHORT).show();
            return;
        }

        int selectedOption = qualitySpinner.getSelectedItemPosition();
        String[] options = {"Mejor Calidad (Auto)", "1080p", "720p", "480p", "Solo Audio (MP3)"};
        String qualityLabel = options[selectedOption];

        // Crear la tarea de descarga
        DownloadTask task = new DownloadTask(url, selectedOption, qualityLabel);

        // Inflar el diseño visual del item para esta descarga en cola
        View itemView = getLayoutInflater().inflate(R.layout.item_queue, queueContainer, false);
        task.view = itemView;
        updateTaskUI(task); // Rellenar inicialmente con los datos de espera

        // Añadir a la cola y al contenedor LinearLayout
        downloadQueue.add(task);
        queueContainer.addView(itemView);

        queueCardView.setVisibility(View.VISIBLE);
        updateQueueTitle();

        // Limpiar el campo de texto y avisar al usuario
        urlInput.setText("");
        Toast.makeText(this, "Añadido a la lista de espera", Toast.LENGTH_SHORT).show();

        // Ejecutar en el SingleThreadExecutor o en hilos paralelos según configuración
        android.content.SharedPreferences prefs = getSharedPreferences("descargador_prefs", Context.MODE_PRIVATE);
        boolean sequential = prefs.getBoolean("sequential_download", true);

        if (sequential) {
            executorService.execute(() -> executeQueueDownload(task));
        } else {
            new Thread(() -> executeQueueDownload(task)).start();
        }
    }

    private void executeQueueDownload(DownloadTask task) {
        // Ejecuta en el hilo secundario del executor
        runOnUiThread(() -> {
            task.status = "Descargando";
            updateQueueTitle();
            updateTaskUI(task);
        });

        try {
            android.content.SharedPreferences prefs = getSharedPreferences("descargador_prefs", Context.MODE_PRIVATE);
            String folderName = prefs.getString("folder_name", "DescargadorPro");
            String threads = prefs.getString("download_threads", "3");

            File downloadsDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS);
            File appDir = new File(downloadsDir, folderName);
            File targetDir;

            if (task.qualityOption == 4) { // Solo Audio
                targetDir = new File(appDir, "Musica");
            } else { // Video
                targetDir = new File(appDir, "Video");
            }

            if (!targetDir.exists()) {
                targetDir.mkdirs();
            }

            YoutubeDLRequest request = new YoutubeDLRequest(task.url);
            request.addOption("-o", targetDir.getAbsolutePath() + "/%(title)s.%(ext)s");
            request.addOption("-N", threads); // Descargar en fragmentos paralelos dinámicos

            if (task.qualityOption == 4) { // Solo Audio
                request.addOption("-x");
                request.addOption("--audio-format", "mp3");
                request.addOption("--embed-thumbnail");
                request.addOption("--add-metadata");
            } else {
                String fmt = "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best";
                if (task.qualityOption == 1) fmt = "bestvideo[height<=1080][ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best";
                else if (task.qualityOption == 2) fmt = "bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best";
                else if (task.qualityOption == 3) fmt = "bestvideo[height<=480][ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best";
                request.addOption("-f", fmt);
            }

            // Intentar obtener el título real en segundo plano antes de iniciar la descarga
            try {
                com.yausername.youtubedl_android.mapper.VideoInfo videoInfo = YoutubeDL.getInstance().getInfo(task.url);
                if (videoInfo != null && videoInfo.getTitle() != null) {
                    runOnUiThread(() -> {
                        task.title = videoInfo.getTitle();
                        updateTaskUI(task);
                    });
                }
            } catch (Exception ignored) {}

            YoutubeDL.getInstance().execute(request, "TaskID_" + System.currentTimeMillis(), (progress, eta, line) -> {
                runOnUiThread(() -> {
                    task.progress = progress.intValue();
                    task.eta = String.valueOf(eta);
                    updateTaskUI(task);
                });
                return kotlin.Unit.INSTANCE;
            });

            runOnUiThread(() -> {
                task.status = "Completado";
                task.progress = 100;
                task.eta = "";
                updateQueueTitle();
                updateTaskUI(task);
                Toast.makeText(MainActivity.this, "¡Descarga completada!\n" + task.title, Toast.LENGTH_LONG).show();

                // Enviar broadcast para notificar al reproductor y a la lista de descargas
                Intent broadcastIntent = new Intent("com.example.descargadorpro.ACTION_NEW_DOWNLOAD");
                sendBroadcast(broadcastIntent);
            });

        } catch (Exception e) {
            Log.e("YoutubeDL", "Error downloading task", e);
            runOnUiThread(() -> {
                task.status = "Error";
                task.eta = "";
                updateQueueTitle();
                updateTaskUI(task);
                Toast.makeText(MainActivity.this, "Error en " + task.title + ": " + e.getMessage(), Toast.LENGTH_LONG).show();
            });
        }
    }

    private void updateTaskUI(DownloadTask task) {
        if (task.view == null) return;

        TextView titleTv = task.view.findViewById(R.id.queueItemTitle);
        TextView qualityTv = task.view.findViewById(R.id.queueItemQuality);
        TextView statusTv = task.view.findViewById(R.id.queueItemStatus);
        TextView etaTv = task.view.findViewById(R.id.queueItemEta);
        ProgressBar progressBar = task.view.findViewById(R.id.queueItemProgress);

        titleTv.setText(task.title);
        qualityTv.setText(task.qualityLabel);

        switch (task.status) {
            case "En espera":
                statusTv.setText("En cola...");
                statusTv.setTextColor(getResources().getColor(android.R.color.darker_gray, null));
                etaTv.setText("");
                progressBar.setVisibility(View.GONE);
                break;
            case "Descargando":
                statusTv.setText("Descargando: " + task.progress + "%");
                statusTv.setTextColor(getResources().getColor(R.color.color_accent, null));
                if (!task.eta.isEmpty() && !task.eta.equals("null")) {
                    etaTv.setText("ETA: " + task.eta + "s");
                } else {
                    etaTv.setText("");
                }
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setProgress(task.progress);
                break;
            case "Completado":
                statusTv.setText("¡Completado!");
                statusTv.setTextColor(getResources().getColor(R.color.color_secondary, null));
                etaTv.setText("");
                progressBar.setVisibility(View.GONE);
                break;
            case "Error":
                statusTv.setText("Error al descargar");
                statusTv.setTextColor(android.graphics.Color.RED);
                etaTv.setText("");
                progressBar.setVisibility(View.GONE);
                break;
        }
    }

    private void updateQueueTitle() {
        int pending = 0;
        for (DownloadTask task : downloadQueue) {
            if (task.status.equals("En espera") || task.status.equals("Descargando")) {
                pending++;
            }
        }
        if (pending > 0) {
            queueTitle.setText("Lista de espera (" + pending + " pendientes)");
        } else {
            queueTitle.setText("Lista de espera (Todo completado)");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }

    // ── Clases Auxiliares de la Cola de Descarga ──────

    public static class DownloadTask {
        String url;
        int qualityOption;
        String qualityLabel;
        int progress = 0;
        String status = "En espera"; // "En espera", "Descargando", "Completado", "Error"
        String title;
        String eta = "";
        View view; // Referencia a la vista inflada en la interfaz

        public DownloadTask(String url, int qualityOption, String qualityLabel) {
            this.url = url;
            this.qualityOption = qualityOption;
            this.qualityLabel = qualityLabel;
            this.title = url; // Inicialmente muestra el enlace hasta obtener el título
        }
    }
}