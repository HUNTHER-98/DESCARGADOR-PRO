package com.example.descargadorpro;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DownloadsActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private TextView emptyText;
    private HistoryAdapter adapter;
    private final List<File> files = new ArrayList<>();

    private final BroadcastReceiver newDownloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            loadFiles();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_downloads);

        // Toolbar con botón de regreso
        Toolbar toolbar = findViewById(R.id.downloadsToolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
            getSupportActionBar().setHomeAsUpIndicator(android.R.drawable.ic_menu_revert);
        }

        recyclerView = findViewById(R.id.downloadsRecyclerView);
        emptyText = findViewById(R.id.emptyDownloadsText);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HistoryAdapter(this, files);
        recyclerView.setAdapter(adapter);

        // Registrar el receptor para nuevas descargas en tiempo real
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(newDownloadReceiver, new IntentFilter("com.example.descargadorpro.ACTION_NEW_DOWNLOAD"), Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(newDownloadReceiver, new IntentFilter("com.example.descargadorpro.ACTION_NEW_DOWNLOAD"));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadFiles();
    }

    private void loadFiles() {
        files.clear();
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (downloadsDir.exists() && downloadsDir.isDirectory()) {
            scanDirectory(downloadsDir);
        }
        Collections.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        adapter.notifyDataSetChanged();

        if (files.isEmpty()) {
            emptyText.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyText.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
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
                        files.add(f);
                    }
                }
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(newDownloadReceiver);
        } catch (Exception ignored) {}
    }
}
