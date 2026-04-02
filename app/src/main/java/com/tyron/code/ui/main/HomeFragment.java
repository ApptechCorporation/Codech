package com.tyron.code.ui.main;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.FrameLayout;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.transition.MaterialSharedAxis;
import com.tyron.builder.project.Project;
import com.tyron.code.tasks.git.GitCloneTask;
import com.tyron.code.ui.project.ImportProjectProgressFragment;
import com.tyron.code.ui.project.ProjectFragment;
import com.tyron.code.ui.settings.SettingsActivity;
import com.tyron.code.ui.wizard.WizardFragment;
import com.tyron.common.SharedPreferenceKeys;
import com.tyron.common.util.AndroidUtilities;
import com.tyron.completion.progress.ProgressManager;
import com.tyron.resources.R;

import java.io.File;
import java.util.Objects;

public class HomeFragment extends Fragment {

    private MaterialButton create_new_project;
    private MaterialButton clone_git_repository;
    private MaterialButton import_project;
    private MaterialButton open_custom_project;
    private MaterialButton open_project_manager;

    private TextView configure_settings;

    // ✅ PADRÃO CORRETO
    private FrameLayout open_project_list;

    private SharedPreferences mPreferences;
    private boolean mShowDialogOnPermissionGrant;
    private ActivityResultLauncher<String[]> mPermissionLauncher;

    private final ActivityResultLauncher<
            Intent> documentPickerLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == Activity.RESULT_OK) {
            Intent data = result.getData();
            if (data != null && data.getData() != null) {
                Uri uri = data.getData();
                String name = Objects.requireNonNull(
                        DocumentFile.fromSingleUri(requireContext(), uri)).getName();

                if (name != null) {
                    name = name.substring(0, name.lastIndexOf('.'));
                    String path = Environment.getExternalStorageDirectory() + "/Codech";

                    File project = new File(path, name);
                    if (project.exists()) {
                        AndroidUtilities.showToast(
                                getString(R.string.project_already_exists, project.getName()));
                        return;
                    }

                    ImportProjectProgressFragment fragment = ImportProjectProgressFragment.Companion.newInstance(uri);

                    fragment.setOnButtonClickedListener(() -> {
                        openProject(new Project(project));
                        fragment.dismiss();
                    });

                    fragment.show(getChildFragmentManager(),
                            ImportProjectProgressFragment.TAG);
                }
            }
        }
    });

    private final ActivityResultLauncher<
            Intent> documentPickerLauncher2 = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == Activity.RESULT_OK) {
            Intent data = result.getData();
            if (data != null && data.getData() != null) {
                Uri uri = data.getData();
                File file = new File(uri.getPath());
                String[] split = file.getPath().split(":");

                String path = Environment.getExternalStorageDirectory()
                                .getAbsolutePath() + "/" + split[1];

                openProject(new Project(new File(path)));
            }
        }
    });

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setExitTransition(new MaterialSharedAxis(MaterialSharedAxis.X, false));
        mPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext());
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {

        MaterialToolbar toolbar = view.findViewById(R.id.toolbar);
        toolbar.setVisibility(View.GONE);

        create_new_project = view.findViewById(R.id.createNewProject);
        clone_git_repository = view.findViewById(R.id.gitCloneRepo);
        import_project = view.findViewById(R.id.importProject);
        open_custom_project = view.findViewById(R.id.openProject);
        open_project_manager = view.findViewById(R.id.openProjectManager);
        configure_settings = view.findViewById(R.id.configureSettings);

        // ✅ CORREÇÃO AQUI
        open_project_list = view.findViewById(R.id.open_project_list);

        showProjectManager(open_project_list);

        create_new_project.setOnClickListener(v -> {
            WizardFragment wizardFragment = new WizardFragment();
            wizardFragment.setOnProjectCreatedListener(this::openProject);

            getParentFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, wizardFragment)
                    .addToBackStack(null)
                    .commit();
        });

        clone_git_repository.setOnClickListener(v ->
                GitCloneTask.INSTANCE.clone(requireContext()));

        import_project.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("application/zip");
            documentPickerLauncher.launch(intent);
        });

        open_custom_project.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            documentPickerLauncher2.launch(intent);
        });

        configure_settings.setOnClickListener(v -> {
            startActivity(new Intent(requireActivity(), SettingsActivity.class));
        });
    }

    public void showProjectManager(final View fragmentView) {
        getParentFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentView, new ProjectFragment())
                .commit();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.home_fragment, container, false);
    }

    private void setSavePath(String path) {
        mPreferences.edit()
                .putString(SharedPreferenceKeys.PROJECT_SAVE_PATH, path)
                .apply();
    }

    private void savePath() {
        String path = Environment.getExternalStorageDirectory() + "/Codech/Projects";
        File file = new File(path);
        if (!file.exists()) file.mkdirs();
        setSavePath(path);
    }

    private void openProject(Project project) {
        MainFragment fragment = MainFragment.newInstance(
                project.getRootFile().getAbsolutePath(), "app");

        getParentFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit();
    }

    private void requestPermissions() {
        mPermissionLauncher.launch(new String[]{
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
        });
    }
}