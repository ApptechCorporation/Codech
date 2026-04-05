package com.tyron.code.ui.main.action.other;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import androidx.annotation.NonNull;
import com.tyron.actions.ActionPlaces;
import com.tyron.actions.AnAction;
import androidx.core.content.ContextCompat;
import com.tyron.actions.AnActionEvent;
import com.tyron.actions.CommonDataKeys;
import com.tyron.code.ui.settings.SettingsActivity;
import com.tyron.resources.R;

public class OpenSettingsAction extends AnAction {

  public static final String ID = "openSettingsAction";

  @Override
  public void update(@NonNull AnActionEvent event) {
    event.getPresentation().setVisible(false);
    if (!ActionPlaces.MAIN_TOOLBAR.equals(event.getPlace())) {
      return;
    }

    Context context = event.getData(CommonDataKeys.CONTEXT);
    if (context == null) {
      event.getPresentation().setVisible(false);
      return;
    }

    event.getPresentation().setVisible(true);
    event.getPresentation().setText(event.getDataContext().getString(R.string.menu_settings));
    event.getPresentation().setIcon(ContextCompat.getDrawable(context, R.drawable.ic_menu_options));
  }

  @Override
  public void actionPerformed(@NonNull AnActionEvent e) {
    Intent intent = new Intent();
    intent.setClass(e.getDataContext(), SettingsActivity.class);
    e.getDataContext().startActivity(intent);
  }
}
