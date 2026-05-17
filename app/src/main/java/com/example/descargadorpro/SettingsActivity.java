package com.example.descargadorpro;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;

public class SettingsActivity extends AppCompatActivity {

    private EditText editFolderName;
    private SwitchCompat switchSequential;
    private Spinner spinnerDefaultQuality;
    private Spinner spinnerThreads;
    private Button btnSaveSettings;
    private Button btnResetSettings;

    private static final String PREFS_NAME = "descargador_prefs";
    private static final String KEY_FOLDER_NAME = "folder_name";
    private static final String KEY_SEQUENTIAL_DOWNLOAD = "sequential_download";
    private static final String KEY_DEFAULT_QUALITY = "default_quality";
    private static final String KEY_DOWNLOAD_THREADS = "download_threads";

    private final String[] qualityOptions = {
            "Mejor Calidad (Auto)",
            "1080p",
            "720p",
            "480p",
            "Solo Audio (MP3)"
    };

    private final String[] threadLabels = {
            "1 fragmento (Estándar)",
            "3 fragmentos (Rápido - Por Defecto)",
            "5 fragmentos (Extremo)",
            "8 fragmentos (Súper Carga)"
    };

    private final String[] threadValues = {"1", "3", "5", "8"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Configurar Toolbar
        Toolbar toolbar = findViewById(R.id.settingsToolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        // Inicializar vistas
        editFolderName = findViewById(R.id.editFolderName);
        switchSequential = findViewById(R.id.switchSequential);
        spinnerDefaultQuality = findViewById(R.id.spinnerDefaultQuality);
        spinnerThreads = findViewById(R.id.spinnerThreads);
        btnSaveSettings = findViewById(R.id.btnSaveSettings);
        btnResetSettings = findViewById(R.id.btnResetSettings);

        // Inicializar Spinners con adaptadores personalizados premium
        ArrayAdapter<String> qualityAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, qualityOptions);
        spinnerDefaultQuality.setAdapter(qualityAdapter);

        ArrayAdapter<String> threadsAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, threadLabels);
        spinnerThreads.setAdapter(threadsAdapter);

        // Cargar configuraciones guardadas
        loadSettings();

        // Listeners de botones
        btnSaveSettings.setOnClickListener(v -> saveSettings());
        btnResetSettings.setOnClickListener(v -> resetSettingsToDefault());
    }

    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // 1. Nombre de la carpeta
        String folderName = prefs.getString(KEY_FOLDER_NAME, "DescargadorPro");
        editFolderName.setText(folderName);

        // 2. Descarga secuencial
        boolean sequential = prefs.getBoolean(KEY_SEQUENTIAL_DOWNLOAD, true);
        switchSequential.setChecked(sequential);

        // 3. Calidad por defecto
        int defaultQuality = prefs.getInt(KEY_DEFAULT_QUALITY, 0);
        if (defaultQuality >= 0 && defaultQuality < qualityOptions.length) {
            spinnerDefaultQuality.setSelection(defaultQuality);
        }

        // 4. Hilos de descarga (mapear valor de hilos a índice del spinner)
        String threads = prefs.getString(KEY_DOWNLOAD_THREADS, "3");
        int threadIndex = 1; // Por defecto index 1 ("3")
        for (int i = 0; i < threadValues.length; i++) {
            if (threadValues[i].equals(threads)) {
                threadIndex = i;
                break;
            }
        }
        spinnerThreads.setSelection(threadIndex);
    }

    private void saveSettings() {
        String folderName = editFolderName.getText().toString().trim();
        if (folderName.isEmpty()) {
            Toast.makeText(this, "Por favor ingresa un nombre para la carpeta", Toast.LENGTH_SHORT).show();
            return;
        }

        // Evitar caracteres prohibidos en nombres de carpetas
        if (folderName.contains("/") || folderName.contains("\\") || folderName.contains(":") || folderName.contains("*")) {
            Toast.makeText(this, "El nombre de la carpeta contiene caracteres inválidos", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean sequential = switchSequential.isChecked();
        int defaultQuality = spinnerDefaultQuality.getSelectedItemPosition();
        int threadPosition = spinnerThreads.getSelectedItemPosition();
        String threadsValue = threadValues[threadPosition];

        // Guardar en SharedPreferences
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_FOLDER_NAME, folderName);
        editor.putBoolean(KEY_SEQUENTIAL_DOWNLOAD, sequential);
        editor.putInt(KEY_DEFAULT_QUALITY, defaultQuality);
        editor.putString(KEY_DOWNLOAD_THREADS, threadsValue);
        editor.apply();

        Toast.makeText(this, "Configuración guardada exitosamente", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void resetSettingsToDefault() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.clear(); // Limpiar todo para regresar a valores predeterminados
        editor.apply();

        // Recargar los valores en los campos de la interfaz
        loadSettings();

        Toast.makeText(this, "Ajustes restablecidos a valores de fábrica", Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
