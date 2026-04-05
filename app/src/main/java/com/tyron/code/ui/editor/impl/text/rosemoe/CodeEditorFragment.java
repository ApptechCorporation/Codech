package com.tyron.code.ui.editor.impl.text.rosemoe;

import static io.github.rosemoe.sora2.text.EditorUtil.getDefaultColorScheme;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.ForwardingListener;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;
import com.google.android.material.snackbar.Snackbar;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.itsaky.androidide.lsp.kotlin.KotlinLanguageServer;
import com.sun.tools.javac.util.JCDiagnostic;
import com.tyron.actions.ActionManager;
import com.tyron.actions.ActionPlaces;
import com.tyron.actions.CommonDataKeys;
import com.tyron.actions.DataContext;
import com.tyron.actions.util.DataContextUtils;
import com.tyron.builder.log.LogViewModel;
import com.tyron.builder.model.DiagnosticWrapper;
import com.tyron.builder.project.Project;
import com.tyron.builder.project.api.FileManager;
import com.tyron.builder.project.api.Module;
import com.tyron.builder.project.listener.FileListener;
import com.tyron.code.ApplicationLoader;
import com.tyron.code.analyzer.BaseTextmateAnalyzer;
import com.tyron.code.language.LanguageManager;
import com.tyron.code.language.java.JavaLanguage;
import com.tyron.code.language.kotlin.KotlinLanguage;
import com.tyron.code.language.lsp.SimpleLanguageClientImpl;
import com.tyron.code.language.textmate.EmptyTextMateLanguage;
import com.tyron.code.language.xml.LanguageXML;
import com.tyron.code.ui.editor.CodeAssistCompletionLayout;
import com.tyron.code.ui.editor.CodeAssistCompletionWindow;
import com.tyron.code.ui.editor.EditorViewModel;
import com.tyron.code.ui.editor.Savable;
import com.tyron.code.ui.editor.impl.FileEditorManagerImpl;
import com.tyron.code.ui.editor.scheme.CompiledEditorScheme;
import com.tyron.code.ui.editor.shortcuts.ShortcutAction;
import com.tyron.code.ui.editor.shortcuts.ShortcutItem;
import com.tyron.code.ui.main.MainViewModel;
import com.tyron.code.ui.project.ProjectManager;
import com.tyron.code.ui.settings.EditorSettingsFragment;
import com.tyron.code.ui.theme.ThemeRepository;
import com.tyron.code.util.CoordinatePopupMenu;
import com.tyron.code.util.PopupMenuHelper;
import com.tyron.common.SharedPreferenceKeys;
import com.tyron.common.logging.IdeLog;
import com.tyron.common.util.AndroidUtilities;
import com.tyron.common.util.DebouncerStore;
import com.tyron.completion.java.util.DiagnosticUtil;
import com.tyron.completion.java.util.JavaDataContextUtil;
import com.tyron.completion.lsp.api.ILanguageServer;
import com.tyron.completion.lsp.api.ILanguageServerRegistry;
import com.tyron.completion.lsp.api.LspLanguage;
import com.tyron.completion.progress.ProgressManager;
import com.tyron.diagnostics.DiagnosticProvider;
import com.tyron.editor.CharPosition;
import com.tyron.language.api.CodeAssistLanguage;
import com.tyron.resources.R;
import io.github.rosemoe.sora.event.ClickEvent;
import io.github.rosemoe.sora.event.ContentChangeEvent;
import io.github.rosemoe.sora.event.EditorKeyEvent;
import io.github.rosemoe.sora.event.Event;
import io.github.rosemoe.sora.event.InterceptTarget;
import io.github.rosemoe.sora.event.LongPressEvent;
import io.github.rosemoe.sora.lang.Language;
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer;
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.text.Cursor;
import io.github.rosemoe.sora.widget.DirectAccessProps;
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;
import io.github.rosemoe.sora2.text.EditorUtil;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.apache.commons.vfs2.FileContent;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.VFS;
import org.jetbrains.kotlin.com.intellij.util.ReflectionUtil;

import android.widget.TextView;

@SuppressWarnings("FieldCanBeLocal")
public class CodeEditorFragment extends Fragment
        implements Savable,
                SharedPreferences.OnSharedPreferenceChangeListener,
                FileListener,
                ProjectManager.OnProjectOpenListener {

    private static final Logger LOG = IdeLog.getCurrentLogger(CodeEditorFragment.class);

    public static final String KEY_LINE = "line";
    public static final String KEY_COLUMN = "column";
    public static final String KEY_PATH = "path";

    private static final Map<String, String> SERVER_MAP = Map.of(
            "kt", KotlinLanguageServer.SERVER_ID);

    private static final ExecutorService IO_EXECUTOR = Executors.newSingleThreadExecutor();

    private ILanguageServer createLanguageServer(File file) {
        if (file == null || !file.isFile()) return null;

        String serverID = SERVER_MAP.get(getExtension(file));
        if (serverID == null) return null;

        return ILanguageServerRegistry.getDefault().getServer(serverID);
    }

    private static String getExtension(File file) {
        String name = file.getName();
        int lastDot = name.lastIndexOf('.');
        return lastDot == -1 ? "" : name.substring(lastDot + 1);
    }

    public static CodeEditorFragment newInstance(File file) {
        CodeEditorFragment fragment = new CodeEditorFragment();
        Bundle args = new Bundle();
        args.putString(KEY_PATH, file.getAbsolutePath());
        fragment.setArguments(args);
        return fragment;
    }

    public static CodeEditorFragment newInstance(File file, int line, int column) {
        CodeEditorFragment fragment = new CodeEditorFragment();
        Bundle args = new Bundle();
        args.putInt(KEY_LINE, line);
        args.putInt(KEY_COLUMN, column);
        args.putString(KEY_PATH, file.getAbsolutePath());
        fragment.setArguments(args);
        return fragment;
    }

    private static final String EDITOR_LEFT_LINE_KEY = "line";
    private static final String EDITOR_LEFT_COLUMN_KEY = "column";
    private static final String EDITOR_RIGHT_LINE_KEY = "rightLine";
    private static final String EDITOR_RIGHT_COLUMN_KEY = "rightColumn";

    private CodeEditorView mEditor;
    private Language mLanguage;
    private File mCurrentFile = new File("");
    private MainViewModel mMainViewModel;
    private Bundle mSavedInstanceState;

    private boolean mCanSave = false;
    private boolean mReading = false;
    private View.OnTouchListener mDragToOpenListener;

    public CodeEditorFragment() {
        super(R.layout.code_editor_fragment);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            mCurrentFile = new File(args.getString(KEY_PATH, ""));
        }
        mMainViewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        mSavedInstanceState = savedInstanceState;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mEditor != null) {
            Cursor cursor = mEditor.getCursor();
            outState.putInt(EDITOR_LEFT_LINE_KEY, cursor.getLeftLine());
            outState.putInt(EDITOR_LEFT_COLUMN_KEY, cursor.getLeftColumn());
            outState.putInt(EDITOR_RIGHT_LINE_KEY, cursor.getRightLine());
            outState.putInt(EDITOR_RIGHT_COLUMN_KEY, cursor.getRightColumn());
        }
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    private void onContentChange(com.tyron.editor.Content content) {
        if (com.tyron.completion.java.provider.CompletionEngine.isIndexing()) {
            return;
        }
        
        if (mEditor == null) return;
        Language language = mEditor.getEditorLanguage();
        Project project = ProjectManager.getInstance().getCurrentProject();
        if (project == null) return;
        
        Module module = project.getModule(mCurrentFile);
        if (module == null) return;

        if (language instanceof CodeAssistLanguage) {
            ((CodeAssistLanguage) language).onContentChange(mCurrentFile, content);
        }

        if (language instanceof KotlinLanguage || language instanceof LanguageXML) return;

        ProgressManager.getInstance().runLater(() -> {
            ServiceLoader<DiagnosticProvider> providers = ServiceLoader.load(DiagnosticProvider.class);
            for (DiagnosticProvider provider : providers) {
                List<JCDiagnostic> diagnostics = new ArrayList<>(provider.getDiagnostics(module, mCurrentFile));
                if (mEditor != null) {
                    mEditor.setDiagnostics(diagnostics.stream()
                            .map(DiagnosticWrapper::new)
                            .collect(Collectors.toList()));
                }
            }
        }, 300);
    }

    public void hideEditorWindows() {
        if (mEditor != null) {
            mEditor.hideAutoCompleteWindow();
            mEditor.ensureWindowsDismissed();
        }
    }

    @NonNull
    private ListenableFuture<TextMateColorScheme> getScheme(@Nullable String path) {
        if (path != null && new File(path).exists()) {
            return EditorSettingsFragment.getColorScheme(new File(path));
        } else {
            return Futures.immediateFailedFuture(new Throwable("Scheme path invalid"));
        }
    }

    @SuppressLint("RestrictedApi")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mCanSave = false;

        mEditor = view.findViewById(R.id.code_editor);
        mEditor.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        
        mEditor.setEditable(false);
        configureEditor(mEditor);

        View topView = view.findViewById(R.id.top_view);
        EditorViewModel viewModel = new ViewModelProvider((ViewModelStoreOwner) this).get(EditorViewModel.class);
        viewModel.getAnalyzeState().observe(getViewLifecycleOwner(), analyzing -> {
            if (topView != null) {
                topView.setVisibility(analyzing ? View.VISIBLE : View.GONE);
            }
        });
        mEditor.setViewModel(viewModel);
        ApplicationLoader.getDefaultPreferences().registerOnSharedPreferenceChangeListener(this);

        postConfigureEditor();

        String schemeValue = ApplicationLoader.getDefaultPreferences().getString(SharedPreferenceKeys.SCHEME, null);
        if (schemeValue != null && new File(schemeValue).exists() && ThemeRepository.getColorScheme(schemeValue) != null) {
            TextMateColorScheme scheme = ThemeRepository.getColorScheme(schemeValue);
            if (scheme != null) {
                mEditor.setColorScheme(scheme);
                mEditor.openFile(mCurrentFile);
                initializeLanguage();
                readOrWait();
            }
        } else {
            ListenableFuture<TextMateColorScheme> schemeFuture = getScheme(schemeValue);
            Futures.addCallback(schemeFuture, new FutureCallback<TextMateColorScheme>() {
                @Override
                public void onSuccess(@Nullable TextMateColorScheme result) {
                    if (getContext() == null || mEditor == null || result == null) return;
                    ThemeRepository.putColorScheme(schemeValue, result);
                    mEditor.setColorScheme(result);
                    mEditor.openFile(mCurrentFile);
                    initializeLanguage();
                    readOrWait();
                }

                @Override
                public void onFailure(@NonNull Throwable t) {
                    if (getContext() == null || mEditor == null) return;
                    String key = EditorUtil.isDarkMode(requireContext()) ? ThemeRepository.DEFAULT_NIGHT : ThemeRepository.DEFAULT_LIGHT;
                    TextMateColorScheme scheme = ThemeRepository.getColorScheme(key);
                    if (scheme == null) {
                        scheme = getDefaultColorScheme(requireContext());
                        ThemeRepository.putColorScheme(key, scheme);
                    }
                    mEditor.setColorScheme(scheme);
                    mEditor.openFile(mCurrentFile);
                    initializeLanguage();
                    readOrWait();
                }
            }, ContextCompat.getMainExecutor(requireContext()));
        }
    }

    private void initializeLanguage() {
        if (mEditor == null) return;
        mLanguage = LanguageManager.getInstance().get(mEditor, mCurrentFile);
        if (mLanguage == null) {
            mLanguage = new EmptyTextMateLanguage();
        }
        mEditor.setEditorLanguage(mLanguage);
    }

    private void configureEditor(@NonNull CodeEditorView editor) {
        editor.setEditable(false);
        editor.setColorScheme(new CompiledEditorScheme(requireContext()));
        editor.setBackgroundAnalysisEnabled(false);
        editor.setTypefaceText(ResourcesCompat.getFont(requireContext(), R.font.jetbrains_mono_regular));
        editor.getComponent(EditorAutoCompletion.class).setLayout(new CodeAssistCompletionLayout());
        editor.setLigatureEnabled(true);
        editor.setDiagnostics(new DiagnosticsContainer());
        editor.setHighlightCurrentBlock(false);
        editor.setHighlightBracketPair(false);
        editor.setEdgeEffectColor(Color.TRANSPARENT);
        editor.setUndoEnabled(true);
        editor.openFile(mCurrentFile);
        editor.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        editor.setInputType(EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS | EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE | EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);

        SharedPreferences pref = ApplicationLoader.getDefaultPreferences();
        editor.setWordwrap(pref.getBoolean(SharedPreferenceKeys.EDITOR_WORDWRAP, false));
        editor.setTextSize(Integer.parseInt(pref.getString(SharedPreferenceKeys.FONT_SIZE, "12")));

        DirectAccessProps props = editor.getProps();
        props.overScrollEnabled = false;
        props.allowFullscreen = false;
        props.stickyScroll = true;
        props.deleteEmptyLineFast = pref.getBoolean(SharedPreferenceKeys.DELETE_WHITESPACES, false);
    }

    private void postConfigureEditor() {
        mEditor.setOnTouchListener((view12, motionEvent) -> {
            if (mDragToOpenListener instanceof ForwardingListener) {
                PopupMenuHelper.setForwarding((ForwardingListener) mDragToOpenListener);
                mDragToOpenListener.onTouch(view12, motionEvent);
            }
            return false;
        });

        mEditor.subscribeEvent(EditorKeyEvent.class, (event, unsubscribe) -> {
            CodeAssistCompletionWindow window = (CodeAssistCompletionWindow) mEditor.getComponent(EditorAutoCompletion.class);
            if (event.getKeyCode() == KeyEvent.KEYCODE_ENTER || event.getKeyCode() == KeyEvent.KEYCODE_TAB) {
                if (window.isShowing() && window.trySelect()) {
                    event.setResult(true);
                    Field mInterceptTargets = ReflectionUtil.findFieldInHierarchy(Event.class, field -> "mInterceptTargets".equals(field.getName()));
                    if (mInterceptTargets != null) {
                        mInterceptTargets.setAccessible(true);
                        try {
                            mInterceptTargets.set(event, InterceptTarget.TARGET_EDITOR);
                        } catch (IllegalAccessException e) {
                            LOG.severe("Reflection failed: " + e.getMessage());
                        }
                    }
                    mEditor.requestFocus();
                }
            }
        });

        mEditor.subscribeEvent(LongPressEvent.class, (event, unsubscribe) -> {
            event.intercept();
            updateFile(mEditor.getText());
            Cursor cursor = mEditor.getCursor();
            CharSequence text = mEditor.getText();
            int textLength = text.length();

            if (cursor.isSelected()) {
                int index = mEditor.getCharIndex(event.getLine(), event.getColumn());
                if (index >= 0 && index < textLength) {
                    int cursorLeft = cursor.getLeft();
                    int cursorRight = cursor.getRight();
                    char c = text.charAt(index);
                    if (Character.isWhitespace(c)) {
                        mEditor.setSelection(event.getLine(), event.getColumn());
                    } else if (index < cursorLeft || index > cursorRight) {
                        EditorUtil.selectWord(mEditor, event.getLine(), event.getColumn());
                    }
                } else {
                    mEditor.setSelection(event.getLine(), event.getColumn());
                }
            } else {
                int index = event.getIndex();
                if (index >= 0 && index < textLength) {
                    char c = text.charAt(index);
                    if (!Character.isWhitespace(c)) {
                        EditorUtil.selectWord(mEditor, event.getLine(), event.getColumn());
                    } else {
                        mEditor.setSelection(event.getLine(), event.getColumn());
                    }
                } else {
                    mEditor.setSelection(event.getLine(), event.getColumn());
                }
            }
            ProgressManager.getInstance().runLater(() -> showPopupMenu(event), 100);
        });

        mEditor.subscribeEvent(ClickEvent.class, (event, unsubscribe) -> {
            try {
                Cursor cursor = mEditor.getCursor();
                if (cursor != null && cursor.isSelected()) {
                    int index = mEditor.getCharIndex(event.getLine(), event.getColumn());
                    CharSequence text = mEditor.getText();
                    if (index >= 0 && index < text.length()) {
                        int cursorLeft = cursor.getLeft();
                        int cursorRight = cursor.getRight();
                        if (!Character.isWhitespace(text.charAt(index)) && index >= cursorLeft && index <= cursorRight) {
                            mEditor.showSoftInput();
                            event.intercept();
                        }
                    }
                }
            } catch (Exception e) {
                LOG.severe("Error ClickEvent: " + e.getMessage());
            }
        });

        mEditor.subscribeEvent(ContentChangeEvent.class, (event, unsubscribe) -> {
            if (event.getAction() == ContentChangeEvent.ACTION_SET_NEW_TEXT) return;
            updateFile(event.getEditor().getText());
            DebouncerStore.DEFAULT.registerOrGetDebouncer("contentChange").debounce(300, () -> {
                try {
                    if (mEditor != null) onContentChange(mEditor.getContent());
                } catch (Throwable t) {
                    LOG.severe("Error in onContentChange: " + t);
                }
            });
        });

        LogViewModel logViewModel = new ViewModelProvider(requireActivity()).get(LogViewModel.class);
        mEditor.setDiagnosticsListener(diagnostics -> {
            for (DiagnosticWrapper diagnostic : diagnostics) {
                DiagnosticUtil.setLineAndColumn(diagnostic, mEditor);
            }
            ProgressManager.getInstance().runLater(() -> logViewModel.updateLogs(LogViewModel.DEBUG, diagnostics));
        });
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences pref, String key) {
        if (mEditor == null) return;
        switch (key) {
            case SharedPreferenceKeys.FONT_SIZE:
                mEditor.setTextSize(Integer.parseInt(pref.getString(key, "12")));
                break;
            case SharedPreferenceKeys.EDITOR_WORDWRAP:
                mEditor.setWordwrap(pref.getBoolean(key, false));
                break;
            case SharedPreferenceKeys.DELETE_WHITESPACES:
                mEditor.getProps().deleteEmptyLineFast = pref.getBoolean(key, false);
                break;
            case SharedPreferenceKeys.SCHEME:
                String schemePath = pref.getString(key, null);
                Futures.addCallback(getScheme(schemePath), new FutureCallback<TextMateColorScheme>() {
                    @Override
                    public void onSuccess(@Nullable TextMateColorScheme result) {
                        if (getContext() == null || mEditor == null || result == null) return;
                        mEditor.setColorScheme(result);
                        if (mLanguage != null && mLanguage.getAnalyzeManager() instanceof BaseTextmateAnalyzer) {
                            mLanguage.getAnalyzeManager().rerun();
                        }
                    }
                    @Override
                    public void onFailure(@NonNull Throwable t) {
                        if (getContext() == null || mEditor == null) return;
                        mEditor.setColorScheme(getDefaultColorScheme(requireContext()));
                        if (mLanguage != null) mLanguage.getAnalyzeManager().rerun();
                    }
                }, ContextCompat.getMainExecutor(requireContext()));
                break;
        }
    }

    private void showPopupMenu(LongPressEvent event) {
        MotionEvent e = event.getCausingEvent();
        if (e == null || getContext() == null || mEditor == null) return;
        CoordinatePopupMenu popupMenu = new CoordinatePopupMenu(requireContext(), mEditor, Gravity.BOTTOM);
        DataContext dataContext = createDataContext();
        if (dataContext == null) return;
        ActionManager.getInstance().fillMenu(dataContext, popupMenu.getMenu(), ActionPlaces.EDITOR, true, false);
        popupMenu.show((int) e.getX(), ((int) e.getY()) - AndroidUtilities.dp(24));
        ProgressManager.getInstance().runLater(() -> {
            popupMenu.setOnDismissListener(d -> mDragToOpenListener = null);
            mDragToOpenListener = popupMenu.getDragToOpenListener();
        }, 300);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Project currentProject = ProjectManager.getInstance().getCurrentProject();
        if (currentProject != null) {
            Module module = currentProject.getModule(mCurrentFile);
            if (module != null) module.getFileManager().removeSnapshotListener(this);
        }
        ProjectManager.getInstance().removeOnProjectOpenListener(this);
        mEditor = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (ProjectManager.getInstance().getCurrentProject() != null && mCanSave) {
            save(true);
            ProjectManager.getInstance().getCurrentProject().getModule(mCurrentFile).getFileManager().closeFileForSnapshot(mCurrentFile);
        }
        ApplicationLoader.getDefaultPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        hideEditorWindows();
        save(true);
    }

    @Override
    public void onSnapshotChanged(File file, CharSequence contents) {
        if (mCurrentFile.equals(file) && mEditor != null) {
            if (!mEditor.getText().toString().contentEquals(contents)) {
                int left = mEditor.getCursor().getLeft();
                mEditor.setText(contents);
                int pos = Math.min(left, contents.length());
                CharPosition position = mEditor.getCharPosition(pos);
                mEditor.setSelection(position.getLine(), position.getColumn());
            }
        }
    }

    @Override
    public boolean canSave() {
        return mCanSave && !mReading;
    }

    @Override
    public void save(boolean toDisk) {
        if (!mCanSave || mReading || !mCurrentFile.exists() || mEditor == null) return;
        String content = mEditor.getText().toString();
        Project project = ProjectManager.getInstance().getCurrentProject();
        if (project != null && !toDisk) {
            project.getModule(mCurrentFile).getFileManager().setSnapshotContent(mCurrentFile, content, false);
        } else {
            IO_EXECUTOR.execute(() -> {
                try {
                    FileUtils.writeStringToFile(mCurrentFile, content, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    LOG.severe("Unable to save file: " + mCurrentFile.getAbsolutePath() + " - " + e.getMessage());
                }
            });
        }
        mEditor.dispatchDocumentSaveEvent();
    }

    @Override
    public void onProjectOpened(@NonNull Project project) {
        ProgressManager.getInstance().runLater(() -> readFile(project, mSavedInstanceState));
        setupLanguageServer();
    }

    private void setupLanguageServer() {
        if (mEditor == null) return;
        Language lang = mEditor.getEditorLanguage();
        if (lang instanceof KotlinLanguage && ((KotlinLanguage) lang).kotlinEnvironment == null) {
            ((KotlinLanguage) lang).initEnv();
        }
        if (lang instanceof LspLanguage) {
            ILanguageServer server = createLanguageServer(mCurrentFile);
            mEditor.setLanguageServer(server);
            ((LspLanguage) lang).setLanguageServer(server);
            if (SimpleLanguageClientImpl.isInitialized()) {
                mEditor.setLanguageClient(SimpleLanguageClientImpl.getInstance());
            }
            mEditor.dispatchDocumentOpenEvent();
        }
    }

    private void readOrWait() {
        Project project = ProjectManager.getInstance().getCurrentProject();
        if (project != null) {
            readFile(project, mSavedInstanceState);
            setupLanguageServer();
        } else {
            ProjectManager.getInstance().addOnProjectOpenListener(this);
        }
    }

    private ListenableFuture<String> readFileAsync() {
        return Futures.submit(() -> {
            FileSystemManager manager = VFS.getManager();
            try (FileObject fileObject = manager.resolveFile(mCurrentFile.toURI())) {
                return fileObject.getContent().getString(StandardCharsets.UTF_8);
            }
        }, IO_EXECUTOR);
    }

    private void readFile(@NonNull Project currentProject, @Nullable Bundle savedInstanceState) {
        mCanSave = false;
        Module module = currentProject.getModule(mCurrentFile);
        FileManager fileManager = module.getFileManager();
        fileManager.addSnapshotListener(this);

        if (fileManager.isOpened(mCurrentFile)) {
            fileManager.getFileContent(mCurrentFile).ifPresent(contents -> {
                if (mEditor != null) {
                    mEditor.setText(contents);
                    mCanSave = true;
                }
            });
            return;
        }

        mReading = true;
        if (mEditor != null) mEditor.setBackgroundAnalysisEnabled(false);
        
        Futures.addCallback(readFileAsync(), new FutureCallback<String>() {
            @Override
            public void onSuccess(@Nullable String result) {
                mReading = false;
                if (getContext() == null || mEditor == null) return;
                mCanSave = true;
                mEditor.setBackgroundAnalysisEnabled(true);
                mEditor.setEditable(true);
                fileManager.openFileForSnapshot(mCurrentFile, result);
                Bundle bundle = new Bundle();
                bundle.putBoolean("loaded", true);
                bundle.putBoolean("bg", true);
                mEditor.setText(result, bundle);
                if (savedInstanceState != null) {
                    restoreState(savedInstanceState);
                } else {
                    int line = getArguments() != null ? getArguments().getInt(KEY_LINE, 0) : 0;
                    int col = getArguments() != null ? getArguments().getInt(KEY_COLUMN, 0) : 0;
                    if (line < mEditor.getText().getLineCount()) setCursorPosition(line, col);
                }
            }
            @Override
            public void onFailure(@NonNull Throwable t) {
                mReading = false;
                if (getContext() != null) checkCanSave();
                LOG.severe("Unable to read file: " + mCurrentFile + " - " + t.getMessage());
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    private void checkCanSave() {
        if (!mCanSave && mEditor != null) {
            Snackbar.make(mEditor, R.string.editor_error_file, Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.menu_close, v -> FileEditorManagerImpl.getInstance().closeFile(mCurrentFile))
                    .show();
        }
    }

    private void restoreState(@NonNull Bundle savedInstanceState) {
        if (mEditor == null) return;
        int leftLine = savedInstanceState.getInt(EDITOR_LEFT_LINE_KEY, 0);
        int leftColumn = savedInstanceState.getInt(EDITOR_LEFT_COLUMN_KEY, 0);
        int rightLine = savedInstanceState.getInt(EDITOR_RIGHT_LINE_KEY, 0);
        int rightColumn = savedInstanceState.getInt(EDITOR_RIGHT_COLUMN_KEY, 0);
        Content text = mEditor.getText();
        if (leftLine >= text.getLineCount() || rightLine >= text.getLineCount()) return;
        if (leftLine != rightLine || leftColumn != rightColumn) {
            mEditor.setSelectionRegion(leftLine, leftColumn, rightLine, rightColumn, true);
        } else {
            mEditor.setSelection(leftLine, leftColumn);
        }
    }

    private void updateFile(CharSequence contents) {
        Project project = ProjectManager.getInstance().getCurrentProject();
        if (project == null) return;
        Module module = project.getModule(mCurrentFile);
        if (module != null && module.getFileManager().isOpened(mCurrentFile)) {
            module.getFileManager().setSnapshotContent(mCurrentFile, contents.toString(), this);
        }
    }

    public CodeEditorView getEditor() {
        return mEditor;
    }

    public void undo() {
        if (mEditor != null && mEditor.canUndo()) mEditor.undo();
    }

    public void redo() {
        if (mEditor != null && mEditor.canRedo()) mEditor.redo();
    }

    public void setCursorPosition(int line, int column) {
        if (mEditor != null) mEditor.getCursor().set(line, column);
    }

    public void performShortcut(ShortcutItem item) {
        if (mEditor == null) return;
        for (ShortcutAction action : item.actions) {
            action.apply(mEditor, item);
        }
    }

    public void format() {
        if (mEditor != null) mEditor.formatCodeAsync();
    }

    public void search() {
        if (mEditor != null) {
            mEditor.getSearcher().stopSearch();
            mEditor.beginSearchMode();
        }
    }

    public void analyze() {
        if (mEditor != null && !mReading) mEditor.rerunAnalysis();
    }

    private DataContext createDataContext() {
        if (mEditor == null) return null;
        Project currentProject = ProjectManager.getInstance().getCurrentProject();
        DataContext dataContext = DataContextUtils.getDataContext(mEditor);
        dataContext.putData(CommonDataKeys.PROJECT, currentProject);
        dataContext.putData(CommonDataKeys.ACTIVITY, requireActivity());
        dataContext.putData(CommonDataKeys.FILE_EDITOR_KEY, mMainViewModel.getCurrentFileEditor());
        dataContext.putData(CommonDataKeys.FILE, mCurrentFile);
        dataContext.putData(CommonDataKeys.EDITOR, mEditor);
        if (currentProject != null && mLanguage instanceof JavaLanguage) {
            JavaDataContextUtil.addEditorKeys(dataContext, currentProject, mCurrentFile, mEditor.getCursor().getLeft());
            return dataContext;
        }
        DiagnosticWrapper diagnosticWrapper = DiagnosticUtil.getDiagnosticWrapper(mEditor.getDiagnosticsList(), mEditor.getCursor().getLeft(), mEditor.getCursor().getRight());
        if (diagnosticWrapper == null && mLanguage instanceof LanguageXML) {
            diagnosticWrapper = DiagnosticUtil.getXmlDiagnosticWrapper(mEditor.getDiagnosticsList(), mEditor.getCursor().getLeftLine());
        }
        dataContext.putData(CommonDataKeys.DIAGNOSTIC, diagnosticWrapper);
        return dataContext;
    }

    @Override
    public void onProjectOpen(Project project) {
        onProjectOpened(project);
    }
}
