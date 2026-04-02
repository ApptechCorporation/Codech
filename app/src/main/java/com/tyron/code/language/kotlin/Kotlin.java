package com.tyron.code.language.kotlin;

import com.tyron.code.ApplicationLoader;
import com.tyron.code.language.Language;
import com.tyron.common.ApplicationProvider;
import com.tyron.common.SharedPreferenceKeys;
import com.tyron.editor.Editor;
import java.io.File;

public class Kotlin implements Language {
  @Override
  public boolean isApplicable(File ext) {
    return ext.getName().endsWith(".kt");
  }

  @Override
  public io.github.rosemoe.sora.lang.Language get(Editor editor) {
    return useFastKotlinLsp() ? new KotlinLanguage2(editor) : new KotlinLanguage(editor);
  }
  public boolean useFastKotlinLsp(){
    return ApplicationLoader.getDefaultPreferences().getBoolean(SharedPreferenceKeys.FAST_KOTLIN_LSP, false);
  }
}
