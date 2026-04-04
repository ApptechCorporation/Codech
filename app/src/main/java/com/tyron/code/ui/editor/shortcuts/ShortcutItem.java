package com.tyron.code.ui.editor.shortcuts;

import java.util.List;

public class ShortcutItem {

  public ShortcutItem() {}

  public ShortcutItem(List<ShortcutAction> actions, String label, String kind) {
    this.actions = actions;
    this.label = label;
    this.kind = kind;
  }

  // Novo construtor para suportar ícones (resource ID)
  public ShortcutItem(List<ShortcutAction> actions, int iconRes, String kind) {
    this.actions = actions;
    this.iconRes = iconRes;
    this.kind = kind;
  }

  public List<ShortcutAction> actions;

  public String kind;

  public String label;

  // Novo campo para armazenar o ID do recurso de imagem
  public int iconRes = 0;
}
