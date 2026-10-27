package com.example.dualworld.ui;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.dualworld.R;
import com.example.dualworld.core.VirtualAppModel;
import com.example.dualworld.core.VirtualCore;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class AppListActivity extends AppCompatActivity {

    private RecyclerView mRecyclerView;
    private ProgressBar mProgressBar;
    private AvailableAppAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_list);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        mProgressBar = findViewById(R.id.progressBar);
        mRecyclerView = findViewById(R.id.recyclerView);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        mAdapter = new AvailableAppAdapter(app -> {
            int nextUserId = 0;
            for (VirtualAppModel model : VirtualCore.get().getClonedApps()) {
                if (model.packageName.equals(app.packageName)) {
                    nextUserId = Math.max(nextUserId, model.userId + 1);
                }
            }

            boolean success = VirtualCore.get().installPackage(app.packageName, nextUserId);
            if (success) {
                Toast.makeText(AppListActivity.this, R.string.clone_success, Toast.LENGTH_SHORT).show();
                finish();
            } else {
                Toast.makeText(AppListActivity.this, "Failed to clone app", Toast.LENGTH_SHORT).show();
            }
        });
        mRecyclerView.setAdapter(mAdapter);

        loadInstalledApps();
    }

    private void loadInstalledApps() {
        mProgressBar.setVisibility(View.VISIBLE);
        mRecyclerView.setVisibility(View.GONE);

        Executors.newSingleThreadExecutor().execute(() -> {
            PackageManager pm = getPackageManager();
            List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            List<VirtualAppModel> apps = new ArrayList<>();

            String myPkg = getPackageName();
            for (ApplicationInfo info : packages) {
                if (info.packageName.equals(myPkg)) continue;
                if (pm.getLaunchIntentForPackage(info.packageName) == null) continue;

                CharSequence name = pm.getApplicationLabel(info);
                VirtualAppModel model = new VirtualAppModel(info.packageName, name.toString(), 0, info.sourceDir);
                try {
                    model.icon = pm.getApplicationIcon(info);
                } catch (Exception ignored) {}
                apps.add(model);
            }

            new Handler(Looper.getMainLooper()).post(() -> {
                mProgressBar.setVisibility(View.GONE);
                mRecyclerView.setVisibility(View.VISIBLE);
                mAdapter.updateData(apps);
            });
        });
    }
}
