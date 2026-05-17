package com.example.descargadorpro;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.List;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    private final List<File> files;
    private final Context context;

    public HistoryAdapter(Context context, List<File> files) {
        this.context = context;
        this.files = files;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        File file = files.get(position);
        holder.itemTitle.setText(file.getName());
        long sizeInMb = file.length() / (1024 * 1024);
        holder.itemSize.setText(sizeInMb + " MB");

        holder.itemView.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(context, PlayerActivity.class);
                intent.putExtra("FILE_PATH", file.getAbsolutePath());
                context.startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(context, "Error al abrir el reproductor", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public int getItemCount() {
        return files.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView itemTitle;
        TextView itemSize;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            itemTitle = itemView.findViewById(R.id.itemTitle);
            itemSize = itemView.findViewById(R.id.itemSize);
        }
    }
}
