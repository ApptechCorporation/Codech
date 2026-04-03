package com.tyron.code.ui.main;

import android.Manifest;
import android.app.Activity;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.transition.MaterialSharedAxis;
import com.tyron.builder.project.Project;
import com.tyron.code.tasks.git.GitCloneTask;
import com.tyron.code.ui.project.ImportProjectProgressFragment;
import com.tyron.code.ui.project.adapter.ProjectManagerAdapter;
import com.tyron.code.ui.settings.SettingsActivity;
import com.tyron.code.ui.wizard.WizardFragment;
import com.tyron.common.SharedPreferenceKeys;
import com.tyron.common.util.AndroidUtilities;
import dev.mutwakil.codeassist.R;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;

public class HomeFragment extends Fragment {

    public static final String TAG = HomeFragment.class.getSimpleName();

    private MaterialButton create_new_project;
    private MaterialButton clone_git_repository;
    private MaterialButton import_project;
    private MaterialButton open_custom_project;
    private MaterialButton open_project_manager;
    private TextView configure_settings;
    private TextView empty_message;

    private RecyclerView mRecyclerView;
    private ProjectManagerAdapter mAdapter;

    private SharedPreferences mPreferences;
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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.home_fragment, container, false);
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
        mRecyclerView = view.findViewById(R.id.open_project_list);
        empty_message = view.findViewById(R.id.empty_message);

        mAdapter = new ProjectManagerAdapter();
        mAdapter.setOnProjectSelectedListener(this::openProject);
        mAdapter.setOnProjectLongClickListener(this::inflateProjectMenus);

        mRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        mRecyclerView.setAdapter(mAdapter);

        loadProjects();

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

        configure_settings.setOnClickListener(v ->
                startActivity(new Intent(requireActivity(), SettingsActivity.class)));
    }

    private boolean inflateProjectMenus(View view, Project project) {
        String[] options = {"Rename", "Delete", "Copy Path"};

        new MaterialAlertDialogBuilder(requireContext())
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: // Rename
                            View v = LayoutInflater.from(requireContext())
                                    .inflate(R.layout.base_textinput_layout, null);
                            TextInputLayout layout = v.findViewById(R.id.textinput_layout);
                            final Editable rename = layout.getEditText().getText();

                            new MaterialAlertDialogBuilder(requireContext())
                                    .setTitle("Rename")
                                    .setView(v)
                                    .setPositiveButton("OK", (d, w) -> {
                                        try {
                                            File oldDir = project.getRootFile();
                                            File newDir = new File(oldDir.getParent(), rename.toString());
                                            oldDir.renameTo(newDir);
                                            loadProjects();
                                        } catch (Exception e) {
                                            Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_LONG).show();
                                        }
                                    })
                                    .setNegativeButton("Cancel", null)
                                    .show();
                            break;

                        case 1: // Delete
                            deleteProject(project);
                            break;

                        case 2: // Copy Path
                            ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                            clipboard.setText(project.getRootFile().toString());
                            Toast.makeText(requireContext(), "Copied", Toast.LENGTH_SHORT).show();
                            break;
                    }
                }).show();

        return true;
    }

    private void deleteProject(Project project) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                FileUtils.forceDelete(project.getRootFile());

                if (!isAdded() || getActivity() == null) return;

                requireActivity().runOnUiThread(() -> {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), "Deleted", Toast.LENGTH_SHORT).show();
                    loadProjects();
                });

            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private void loadProjects() {
        Executors.newSingleThreadExecutor().execute(() -> {
            String path = Environment.getExternalStorageDirectory() + "/Codech/Projects";
            File projectDir = new File(path);

            if (!projectDir.exists()) projectDir.mkdirs();

            File[] dirs = projectDir.listFiles(File::isDirectory);
            List<Project> list = new ArrayList<>();

            if (dirs != null) {
                for (File dir : dirs) {
                    list.add(new Project(dir, "app"));
                }
            }

            if (!isAdded() || getActivity() == null) return;

            requireActivity().runOnUiThread(() -> {
                if (!isAdded()) return;
                mAdapter.submitList(list);

                if (list.isEmpty()) {
                    empty_message.setVisibility(View.VISIBLE);
                    mRecyclerView.setVisibility(View.GONE);
                } else {
                    empty_message.setVisibility(View.GONE);
                    mRecyclerView.setVisibility(View.VISIBLE);
                }
            });
        });
    }

    private void openProject(Project project) {
        MainFragment fragment = MainFragment.newInstance(
                project.getRootFile().getAbsolutePath(), "app");

        getParentFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit();
    }

    private void setSavePath(String path) {
        mPreferences.edit()
                .putString(SharedPreferenceKeys.PROJECT_SAVE_PATH, path)
                .apply();
    }

    private void requestPermissions() {
        mPermissionLauncher.launch(new String[]{
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
        });
    }
}