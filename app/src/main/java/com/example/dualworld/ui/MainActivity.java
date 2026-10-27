package com.example.dualworld.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.dualworld.R;
import com.example.dualworld.core.VirtualAppModel;
import com.example.dualworld.core.VirtualCore;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private RecyclerView mRecyclerView;
    private TextView mEmptyView;
    private ClonedAppAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mRecyclerView = findViewById(R.id.recyclerView);
        mEmptyView = findViewById(R.id.emptyView);
        ExtendedFloatingActionButton fabAdd = findViewById(R.id.fabAdd);

        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new ClonedAppAdapter(new ClonedAppAdapter.OnItemClickListener() {
            @Override
            public void onLaunchClick(VirtualAppModel app) {
                boolean launched = VirtualCore.get().launchApp(MainActivity.this, app.packageName, app.userId);
                if (!launched) {
                    Toast.makeText(MainActivity.this, "Unable to launch clone", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onDeleteClick(VirtualAppModel app) {
                VirtualCore.get().uninstallPackage(app.packageName, app.userId);
                Toast.makeText(MainActivity.this, R.string.clone_removed, Toast.LENGTH_SHORT).show();
                refreshClonedList();
            }

            @Override
            public void onRenameClick(VirtualAppModel app) {
                showRenameDialog(app);
            }
        });
        mRecyclerView.setAdapter(mAdapter);

        fabAdd.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, AppListActivity.class);
            startActivity(intent);
        });
    }

    private void showRenameDialog(VirtualAppModel app) {
        EditText input = new EditText(this);
        input.setText(app.appName);
        input.setSelectAllOnFocus(true);

        new AlertDialog.Builder(this)
                .setTitle("Rename Clone")
                .setMessage("Enter new display label for this account:")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String newName = input.getText().toString().trim();
                    if (!newName.isEmpty()) {
                        VirtualCore.get().renameClone(app.packageName, app.userId, newName);
                        refreshClonedList();
                        Toast.makeText(this, "Renamed to " + newName, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshClonedList();
    }

    private void refreshClonedList() {
        List<VirtualAppModel> list = VirtualCore.get().getClonedApps();
        mAdapter.updateData(list);
        if (list.isEmpty()) {
            mEmptyView.setVisibility(View.VISIBLE);
            mRecyclerView.setVisibility(View.GONE);
        } else {
            mEmptyView.setVisibility(View.GONE);
            mRecyclerView.setVisibility(View.VISIBLE);
        }
    }
}
