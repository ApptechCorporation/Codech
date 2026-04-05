package com.tyron.code.ui.file.tree;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.view.ViewCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.tyron.actions.ActionManager;
import com.tyron.actions.ActionPlaces;
import com.tyron.actions.CommonDataKeys;
import com.tyron.actions.DataContext;
import com.tyron.builder.project.Project;
import com.tyron.builder.project.api.JavaModule;
import com.tyron.builder.project.api.Module;
import com.tyron.code.ApplicationLoader;
import com.tyron.code.event.EventManager;
import com.tyron.code.ui.editor.impl.FileEditorManagerImpl;
import com.tyron.code.ui.file.CommonFileKeys;
import com.tyron.code.ui.file.FileViewModel;
import com.tyron.code.ui.file.event.RefreshRootEvent;
import com.tyron.code.ui.file.tree.binder.TreeFileNodeViewBinder.TreeFileNodeListener;
import com.tyron.code.ui.file.tree.binder.TreeFileNodeViewFactory;
import com.tyron.code.ui.file.tree.model.TreeFile;
import com.tyron.code.ui.main.MainViewModel;
import com.tyron.code.ui.project.ProjectManager;
import com.tyron.code.util.EventManagerUtilsKt;
import com.tyron.code.util.UiUtilsKt;
import com.tyron.common.util.AndroidUtilities;
import com.tyron.common.util.SingleTextWatcher;
import com.tyron.completion.progress.ProgressManager;
import com.tyron.resources.R;
import com.tyron.ui.treeview.TreeNode;
import com.tyron.ui.treeview.TreeView;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.apache.commons.io.FileUtils;

public class TreeFileManagerFragment extends Fragment {

    /**
     * @deprecated Instantiate this fragment directly without arguments and use {@link
     *     FileViewModel} to update the nodes
     */
    @Deprecated
    public static TreeFileManagerFragment newInstance(File root) {
        TreeFileManagerFragment fragment = new TreeFileManagerFragment();
        Bundle args = new Bundle();
        args.putSerializable("rootFile", root);
        fragment.setArguments(args);
        return fragment;
    }

    private ActivityResultLauncher<Intent> documentPickerLauncher;

    private File rootDir;
    private File currentDir;
    private MainViewModel mMainViewModel;
    private FileViewModel mFileViewModel;
    private TreeView<TreeFile> treeView;

    public TreeFileManagerFragment() {
        super(R.layout.tree_file_manager_fragment);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mMainViewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        mFileViewModel = new ViewModelProvider(requireActivity()).get(FileViewModel.class);

        documentPickerLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        result -> {
            if (result.getResultCode() == Activity.RESULT_OK) {
                Intent data = result.getData();
                if (data != null) {
                    Uri uri = data.getData();
                    if (uri != null) {
                        String name = Objects.requireNonNull(DocumentFile.fromSingleUri(requireContext(), uri))
                                .getName();

                        try {
                            InputStream inputStream = requireContext().getContentResolver().openInputStream(uri);
                            File outputFile = new File(currentDir, name);
                            FileUtils.copyInputStreamToFile(inputStream, outputFile);

                        } catch (IOException ex) {
                            ProgressManager.getInstance()
                                    .runLater(
                                            () -> {
                                                if (isDetached() || getContext() == null) {
                                                    return;
                                                }

                                                AndroidUtilities.showSimpleAlert(
                                                        requireContext(), R.string.error, ex.getLocalizedMessage());
                                            });
                        }

                        TreeNode<TreeFile> node = TreeNode.root(TreeUtil.getNodes(rootDir));
                        ProgressManager.getInstance()
                                .runLater(
                                        () -> {
                                            if (getActivity() == null) {
                                                return;
                                            }
                                            treeView.refreshTreeView(node);
                                        });
                    }
                }
            }
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        ViewCompat.requestApplyInsets(view);
        UiUtilsKt.addSystemWindowInsetToPadding(view, false, true, false, true);

        SwipeRefreshLayout refreshLayout = view.findViewById(R.id.refreshLayout);
        refreshLayout.setOnRefreshListener(
                () ->
                        partialRefresh(
                        () -> {
                            refreshLayout.setRefreshing(false);
                            treeView.refreshTreeView();
                        }));

        // CORREÇÃO: Inicializar rootDir e currentDir a partir do projeto atual
        Project currentProject = ProjectManager.getInstance().getCurrentProject();
        if (currentProject != null) {
            rootDir = currentProject.getRootFile();
            currentDir = rootDir;
        } else {
            // Se não houver projeto, usar o diretório padrão
            rootDir = new File(System.getProperty("user.home"));
            currentDir = rootDir;
        }

        // CORREÇÃO: Criar TreeView com os nós do projeto
        TreeNode<TreeFile> rootNode = TreeNode.root(TreeUtil.getNodes(rootDir));
        treeView = new TreeView<>(requireContext(), rootNode);

        MaterialButton addLibrary = view.findViewById(R.id.addNewLibrary);
        MaterialButton projectInfo = view.findViewById(R.id.projectProperties);

        TextView projectNameText = view.findViewById(R.id.project_name_text);

        // CORREÇÃO: Agora currentDir não será null, então o nome do projeto será exibido
        if (currentDir != null) {
            projectNameText.setText(currentDir.getName());
        } else {
            projectNameText.setText("Projeto");
        }

        addLibrary.setOnClickListener(
                v -> {
                    showNewLibrary();
                });

        projectInfo.setOnClickListener(
                v -> {
                    showProjectInfo();
                });

        HorizontalScrollView horizontalScrollView = view.findViewById(R.id.horizontalScrollView);
        horizontalScrollView.addView(
                treeView.getView(),
                new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        treeView.getView().setNestedScrollingEnabled(false);

        EventManager eventManager = ApplicationLoader.getInstance().getEventManager();

        EventManagerUtilsKt.subscribeEvent(
                eventManager,
                getViewLifecycleOwner(),
                RefreshRootEvent.class,
                (event, unsubscribe) -> {
                    File refreshRoot = event.getRoot();
                    TreeNode<TreeFile> currentRoot = treeView.getRoot();
                    if (currentRoot != null && refreshRoot.equals(currentRoot.getValue().getFile())) {
                        partialRefresh(() -> treeView.refreshTreeView());
                    } else {
                        ProgressManager.getInstance()
                                .runNonCancelableAsync(
                                        () -> {
                                            TreeNode<
                                                    TreeFile> node = TreeNode.root(TreeUtil.getNodes(refreshRoot));
                                            ProgressManager.getInstance()
                                                    .runLater(
                                                            () -> {
                                                                if (getActivity() == null) {
                                                                    return;
                                                                }
                                                                treeView.refreshTreeView(node);
                                                            });
                                        });
                    }
                });

        treeView.setAdapter(
                new TreeFileNodeViewFactory(
                new TreeFileNodeListener() {
                    @Override
                    public void onNodeToggled(TreeNode<TreeFile> treeNode, boolean expanded) {
                        if (treeNode.isLeaf()) {
                            if (treeNode.getValue().getFile().isFile()) {
                                FileEditorManagerImpl.getInstance()
                                        .openFile(requireContext(), treeNode.getValue().getFile(), true);
                            }
                        }
                    }

                    @Override
                    public boolean onNodeLongClicked(
                            View view, TreeNode<TreeFile> treeNode, boolean expanded) {
                        PopupMenu popupMenu = new PopupMenu(requireContext(), view);
                        addMenus(popupMenu, treeNode);
                        // Get the background drawable and set its shape
                        try {
                            Field popupField = PopupMenu.class.getDeclaredField("mPopup");
                            popupField.setAccessible(true);
                            Object menuPopupHelper = popupField.get(popupMenu);
                            Class<
                                    ?> classPopupHelper = Class.forName(menuPopupHelper.getClass().getName());
                            Method setForceIcons = classPopupHelper.getMethod("setForceShowIcon", boolean.class);
                            setForceIcons.invoke(menuPopupHelper, true);
                            PopupWindow popupWindow = (PopupWindow) menuPopupHelper
                                            .getClass()
                                            .getDeclaredField("mPopup")
                                            .get(menuPopupHelper);
                            popupMenu.show();
                        } catch (Exception e) {
                            popupMenu.show();
                        }
                        return true;
                    }

                    @Override
                    public void onNodeClicked(TreeNode<TreeFile> treeNode) {
                        if (!treeNode.isLeaf()) {
                            treeNode.toggle();
                        }
                    }
                }));

        mFileViewModel
                .getRefreshNode()
                .observe(
                        getViewLifecycleOwner(),
                        file -> {
                            ProgressManager.getInstance()
                                    .runNonCancelableAsync(
                                            () -> {
                                                TreeNode<TreeFile> node = TreeNode.root(TreeUtil.getNodes(file));
                                                ProgressManager.getInstance()
                                                        .runLater(
                                                                () -> {
                                                                    if (getActivity() == null) {
                                                                        return;
                                                                    }
                                                                    treeView.refreshTreeView(node);
                                                                });
                                            });
                        });
    }

    private void showNewLibrary() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        builder.setTitle("Add Library");

        LayoutInflater inflater = (LayoutInflater) requireContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View v = inflater.inflate(R.layout.dialog_add_library, null);
        EditText libraryName = v.findViewById(R.id.libraryName);
        builder.setView(v);

        builder.setPositiveButton(
                android.R.string.ok,
                (dialog, which) -> {
                    String name = libraryName.getText().toString();
                    if (!TextUtils.isEmpty(name)) {
                        createLibrary(name);
                        mFileViewModel.refreshNode(rootDir);
                    }
                });

        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }

    private void createLibrary(String name) {
        ProjectManager manager = ProjectManager.getInstance();
        Project project = manager.getCurrentProject();
        File libraryDir = new File(project.getRootFile().getParent(), name);
        if (!libraryDir.exists()) {
            libraryDir.mkdirs();
        }

        // Create build.gradle file
        File buildGradleFile = new File(libraryDir, "build.gradle");
        String buildGradleContent = "plugins {\n" +
                "    id 'java-library'\n" +
                "}\n" +
                "\n" +
                "java {\n" +
                "    sourceCompatibility = JavaVersion.VERSION_11\n" +
                "    targetCompatibility = JavaVersion.VERSION_11\n" +
                "}\n" +
                "\n" +
                "dependencies {\n" +
                "}\n";
        try {
            FileWriter writer = new FileWriter(buildGradleFile);
            writer.write(buildGradleContent);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Create src/main/java directory
        File srcDir = new File(libraryDir, "src/main/java");
        srcDir.mkdirs();

        // Create a sample Java file
        File javaFile = new File(srcDir, "Sample.java");
        String javaCode = "public class Sample {\n" +
                "    public static void main(String[] args) {\n" +
                "        System.out.println(\"Hello from " + name + "\");\n" +
                "    }\n" +
                "}\n";
        try {
            FileWriter writer = new FileWriter(javaFile);
            writer.write(javaCode);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showProjectInfo() {
        ProjectManager manager = ProjectManager.getInstance();
        Project project = manager.getCurrentProject();
        Module module = project.getMainModule();
        JavaModule javaModule = (JavaModule) module;

        String root = javaModule.getRootFile().getName();

        LayoutInflater inflater = (LayoutInflater) requireContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View v = inflater.inflate(R.layout.dialog_project_information, null);
        TextView projectPath = v.findViewById(R.id.projectPath);
        TextView libraryProjects = v.findViewById(R.id.libraryProjects);
        TextView includedProjects = v.findViewById(R.id.includedProjects);
        TextView projectLibraries = v.findViewById(R.id.projectLibraries);

        File prPath = javaModule.getRootFile();
        String pr = root.substring(0, 1).toUpperCase() + root.substring(1) + " " + prPath.getAbsolutePath();

        projectPath.setText(pr);

        List<String> implementationProjects = new ArrayList<>(javaModule.getAllProjects());
        List<String> included = javaModule.getIncludedProjects();

        String implementationText = implementationProjects.isEmpty()
                ? "sub projects: <none>"
                : "sub projects:\n" + String.join("\n", implementationProjects);
        libraryProjects.setText(implementationText);

        String includedText = included.isEmpty()
                ? "included projects: <none>"
                : "included projects:\n" + String.join("\n", included);
        includedProjects.setText(includedText);

        File[] fileLibraries = getProjectLibraries(javaModule.getLibraryDirectory());

        if (fileLibraries != null && fileLibraries.length > 0) {
            StringBuilder libraryTextBuilder = new StringBuilder(prPath.getName() + " libraries:\n");
            for (File fileLibrary : fileLibraries) {
                libraryTextBuilder.append(fileLibrary.getName()).append("\n");
            }
            String libraryText = libraryTextBuilder.toString().trim();
            projectLibraries.setText(libraryText);
        } else {
            projectLibraries.setText(prPath.getName() + " libraries: <none>");
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Project " + "'" + root + "'")
                .setView(v)
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(
                        R.string.variants,
                        (d, w) -> {
                            showBuildVariants();
                        })
                .show();
    }

    private static final String LAST_SELECTED_INDEX_KEY = "last_selected_index";
    private int lastSelectedIndex = 0;

    private void showBuildVariants() {
        List<String> variants = new ArrayList<>();
        variants.add("Release");
        variants.add("Debug");
        final String[] options = variants.toArray(new String[0]);

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(requireContext());
        int defaultSelection = preferences.getInt(LAST_SELECTED_INDEX_KEY, 1);
        lastSelectedIndex = defaultSelection;

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.build_variants)
                .setSingleChoiceItems(
                        options,
                        defaultSelection,
                        (dialog, which) -> {
                            lastSelectedIndex = which;
                            preferences.edit().putInt(LAST_SELECTED_INDEX_KEY, which).apply();
                        })
                .setPositiveButton(
                        android.R.string.ok,
                        (dialog, which) -> {
                            String selectedVariant = options[lastSelectedIndex];
                            if (defaultSelection != lastSelectedIndex) {
                                String message = getString(R.string.switched_to_variant) + " " + selectedVariant;
                                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                            }
                        })
                .show();
    }

    private File[] getProjectLibraries(File dir) {
        File
                [] fileLibraries = dir.listFiles(c -> c.getName().endsWith(".aar") || c.getName().endsWith(".jar"));
        return fileLibraries;
    }

    private void partialRefresh(Runnable callback) {
        ProgressManager.getInstance()
                .runNonCancelableAsync(
                        () -> {
                            if (!treeView.getAllNodes().isEmpty()) {
                                TreeNode<TreeFile> node = treeView.getAllNodes().get(0);
                                TreeUtil.updateNode(node);
                                ProgressManager.getInstance()
                                        .runLater(
                                                () -> {
                                                    if (getActivity() == null) {
                                                        return;
                                                    }
                                                    callback.run();
                                                });
                            }
                        });
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    /**
     * Add menus to the current PopupMenu based on the current {@link TreeNode}
     *
     * @param popupMenu The PopupMenu to add to
     * @param node The current TreeNode in the file tree
     */
    private void addMenus(PopupMenu popupMenu, TreeNode<TreeFile> node) {
        DataContext dataContext = DataContext.wrap(requireContext());
        dataContext.putData(CommonDataKeys.FILE, node.getContent().getFile());
        dataContext.putData(CommonDataKeys.PROJECT, ProjectManager.getInstance().getCurrentProject());
        dataContext.putData(CommonDataKeys.FRAGMENT, TreeFileManagerFragment.this);
        dataContext.putData(CommonDataKeys.ACTIVITY, requireActivity());
        dataContext.putData(CommonFileKeys.TREE_NODE, node);

        ActionManager.getInstance()
                .fillMenu(dataContext, popupMenu.getMenu(), ActionPlaces.FILE_MANAGER, true, false);
    }

    public TreeView<TreeFile> getTreeView() {
        return treeView;
    }

    public MainViewModel getMainViewModel() {
        return mMainViewModel;
    }

    public FileViewModel getFileViewModel() {
        return mFileViewModel;
    }

    private void addLibrary(File gradleFile, String name) {
        String dependencyLine = "\timplementation project(':" + name + "')\n";

        try {
            // Read the contents of the build.gradle file
            FileInputStream inputStream = new FileInputStream(gradleFile);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder stringBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                // Add the dependency line after the last line that starts with "dependencies {"
                if (line.trim().startsWith("dependencies {")) {
                    stringBuilder.append(line).append("\n");
                    while ((line = reader.readLine()) != null) {
                        stringBuilder.append(line).append("\n");
                    }
                    stringBuilder.insert(stringBuilder.lastIndexOf("}"), dependencyLine);
                    break;
                } else {
                    stringBuilder.append(line).append("\n");
                }
            }
            inputStream.close();

            // Write the modified contents back to the build.gradle file
            FileOutputStream outputStream = new FileOutputStream(gradleFile);
            outputStream.write(stringBuilder.toString().getBytes());
            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void addToInclude(File gradleSettingsFile, String name) {
        String includeLine = "include ':" + name + "'\n";

        try {
            // Read the contents of the settings.gradle file
            FileInputStream inputStream = new FileInputStream(gradleSettingsFile);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder stringBuilder = new StringBuilder();
            String line;
            boolean includeExists = false;
            while ((line = reader.readLine()) != null) {
                // Check if the include line already exists
                if (line.trim().equals(includeLine.trim())) {
                    includeExists = true;
                }
                stringBuilder.append(line).append("\n");
            }
            inputStream.close();

            // Add the include line if it does not already exist
            if (!includeExists) {
                stringBuilder.append(includeLine);
            }

            // Write the modified contents back to the settings.gradle file
            FileOutputStream outputStream = new FileOutputStream(gradleSettingsFile);
            outputStream.write(stringBuilder.toString().getBytes());
            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void importFile(File rootDir, File currentDir) {
        this.rootDir = rootDir;
        this.currentDir = currentDir;

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        documentPickerLauncher.launch(intent);
    }
}
