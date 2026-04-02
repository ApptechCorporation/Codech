package com.tyron.code.ui.project;

import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.transition.MaterialFade;

import com.tyron.builder.project.Project;
import com.tyron.code.ApplicationLoader;
import com.tyron.code.ui.main.MainFragment;
import com.tyron.code.ui.project.adapter.ProjectManagerAdapter;
import com.tyron.common.SharedPreferenceKeys;
import com.tyron.common.util.AndroidUtilities;
import com.tyron.completion.progress.ProgressManager;
import com.tyron.resources.R;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Executors;

import org.apache.commons.io.FileUtils;

public class ProjectFragment extends Fragment {

    private RecyclerView mRecyclerView;
    private ProjectManagerAdapter mAdapter;
    private Project project = null;

    public static final String TAG = ProjectFragment.class.getSimpleName();

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        return inflater.inflate(R.layout.project_manager_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {

        mRecyclerView = view.findViewById(R.id.projects_recycler);

        mAdapter = new ProjectManagerAdapter();
        mAdapter.setOnProjectSelectedListener(this::openProject);
        mAdapter.setOnProjectLongClickListener(this::inflateProjectMenus);

        mRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        mRecyclerView.setAdapter(mAdapter);

        loadProjects();
    }

    private boolean inflateProjectMenus(View view, Project project) {
        this.project = project;

        String[] option = {"Rename", "Delete", "Copy Path"};

        new MaterialAlertDialogBuilder(requireContext())
                .setItems(option, (dialog, which) -> {
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

                        case 2: // Copy path
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

                if (!isAdded() || getActivity() == null) return; // ← ADICIONAR

                requireActivity().runOnUiThread(() -> {
                    if (!isAdded()) return; // ← ADICIONAR
                    Toast.makeText(requireContext(), "Deleted", Toast.LENGTH_SHORT).show();
                    loadProjects();
                });

            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private void openProject(Project project) {

        MainFragment fragment = MainFragment.newInstance(
                project.getRootFile().getAbsolutePath(),
                "app"
        );

        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit();
    }

    private void loadProjects() {
        Executors.newSingleThreadExecutor().execute(() -> {
            String path = Environment.getExternalStorageDirectory() + "/Codech/Projects";
            File projectDir = new File(path);

            if (!projectDir.exists()) {
                projectDir.mkdirs();
            }

            File[] dirs = projectDir.listFiles(File::isDirectory);
            List<Project> list = new ArrayList<>();

            if (dirs != null) {
                for (File dir : dirs) {
                    list.add(new Project(dir, "app"));
                }
            }

            if (!isAdded() || getActivity() == null) return; // ← ADICIONAR

            requireActivity().runOnUiThread(() -> {
                if (!isAdded()) return; // ← ADICIONAR
                mAdapter.submitList(list);
            });
        });
    }
}
