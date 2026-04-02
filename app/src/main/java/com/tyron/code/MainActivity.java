package com.tyron.code;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.KeyEvent;
import android.widget.Toast;
import android.content.SharedPreferences;

import android.provider.Settings;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;

import com.google.android.material.button.*;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import com.tyron.code.ui.main.HomeFragment;
import com.tyron.resources.R;

public class MainActivity extends AppCompatActivity {

    private SharedPreferences storage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        storage = getSharedPreferences("storage", Activity.MODE_PRIVATE);

        HomeFragment homeFragment = new HomeFragment();
        if (getSupportFragmentManager().findFragmentByTag(HomeFragment.TAG) == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, homeFragment, HomeFragment.TAG)
                    .commit();
        }

        OpenAppSettings();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @Override
    public boolean onKeyShortcut(int keyCode, KeyEvent event) {
        return super.onKeyShortcut(keyCode, event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return super.onKeyUp(keyCode, event);
    }

    public void _OpenAppSettings() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(MainActivity.this);
        builder.setIcon(R.drawable.icon_folder_round);
        builder.setTitle("Permitir acesso");
        builder.setMessage("Permita que o aplicativo acesse o armazenamento do seu dispositivo para salvar arquivos e pastas. \n\n(Acesso para gerenciar todos os arquivos)");
        builder.setCancelable(false);
        builder.setPositiveButton("Permitir", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                Uri uri = Uri.fromParts("package", getPackageName(), null);
                intent.setData(uri);

                if (intent.resolveActivity(getPackageManager()) != null) {
                    startActivityForResult(intent, 11);
                } else {

                    SketchwareUtil.showMessage(getApplicationContext(), "Permissão concedida ✅");
                    storage.edit().putString("storage", "storage").apply();
                }
            }
        });
        AlertDialog dialog = builder.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 11) {
            Toast.makeText(MainActivity.this, "Não permitir ❌", Toast.LENGTH_SHORT).show();
        }
        switch (requestCode) {
            default:
                break;
        }
    }
}
