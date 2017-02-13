/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.application.options.schemes;

import com.intellij.openapi.options.Scheme;
import com.intellij.openapi.ui.MessageType;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.*;
import java.util.Collection;

public class EditableSchemesCombo<T extends Scheme> {
  
  // region Message constants
  public static final String EMPTY_NAME_MESSAGE = "The name must not be empty";
  public static final String NAME_ALREADY_EXISTS_MESSAGE = "Name is already in use. Please change to unique name.";
  private static final String EDITING_HINT = "Enter to save, Esc to cancel.";
  public static final int COMBO_WIDTH = 200;
  // endregion
  
  private SchemesCombo<T> myComboBox;
  private JPanel myRootPanel;
  private AbstractSchemesPanel<T> mySchemesPanel;
  private final CardLayout myLayout;
  private final JTextField myNameEditorField;

  private final static KeyStroke ESC_KEY_STROKE = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0, false);
  private final static KeyStroke ENTER_KEY_STROKE = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, false);

  public EditableSchemesCombo(@NotNull AbstractSchemesPanel<T> schemesPanel) {
    mySchemesPanel = schemesPanel;
    myLayout = new CardLayout();
    myRootPanel = new JPanel(myLayout);
    createCombo();
    myRootPanel.add(myComboBox);
    myNameEditorField = createNameEditorField();
    myRootPanel.add(myNameEditorField);
    myRootPanel.setPreferredSize(new Dimension(JBUI.scale(COMBO_WIDTH), myNameEditorField.getPreferredSize().height));
    myRootPanel.setMaximumSize(new Dimension(JBUI.scale(COMBO_WIDTH), Short.MAX_VALUE));
  }

  private JTextField createNameEditorField() {
    JTextField nameEditorField = new JTextField();
    nameEditorField.registerKeyboardAction(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        revertSchemeName();
        cancelEdit();
      }
    }, ESC_KEY_STROKE, JComponent.WHEN_FOCUSED);
    nameEditorField.registerKeyboardAction(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        stopEdit();
      }
    }, ENTER_KEY_STROKE, JComponent.WHEN_FOCUSED);
    nameEditorField.addFocusListener(new FocusAdapter() {
      @Override
      public void focusLost(FocusEvent e) {
        stopEdit();
      }
    });
    nameEditorField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        validateOnTyping();
      }
    });
    return nameEditorField;
  }

  private void validateOnTyping() {
    String currName = myNameEditorField.getText();
    SchemesCombo.MySchemeListItem<T> selectedItem = myComboBox.getSelectedItem();
    if (selectedItem != null && !currName.equals(selectedItem.getSchemeName())) {
      String validationMessage = validateSchemeName(currName, mySchemesPanel.getModel().isProjectScheme(ObjectUtils.notNull(selectedItem.getScheme())));
      if (validationMessage != null) {
        mySchemesPanel.showInfo(validationMessage, MessageType.ERROR);
        return;
      }
    }
    showHint();
  }

  private void showHint() {
    mySchemesPanel.showInfo(EDITING_HINT, MessageType.INFO);
  }

  private void revertSchemeName() {
    SchemesCombo.MySchemeListItem<T> selectedItem = myComboBox.getSelectedItem();
    if (selectedItem != null) {
      myNameEditorField.setText(selectedItem.getSchemeName());
    }
  }

  public void updateSelected() {
    myComboBox.repaint();
  }

  private void stopEdit() {
    String newName = myNameEditorField.getText();
    SchemesCombo.MySchemeListItem<T> selectedItem = myComboBox.getSelectedItem();
    if (selectedItem != null) {
      if (newName.equals(selectedItem.getSchemeName())) {
        cancelEdit();
        return;
      }
      boolean isProjectScheme = mySchemesPanel.getModel().isProjectScheme(ObjectUtils.notNull(selectedItem.getScheme()));
      String validationMessage = validateSchemeName(newName, isProjectScheme);
      if (validationMessage != null) {
        mySchemesPanel.showInfo(validationMessage, MessageType.ERROR);
      }
      else {
        cancelEdit();
        if (selectedItem.getScheme() != null) {
          mySchemesPanel.getActions().renameScheme(selectedItem.getScheme(), newName);
        }
      }
    }
  }
  
  public void cancelEdit() {
    mySchemesPanel.clearInfo();
    myLayout.first(myRootPanel);
    myRootPanel.requestFocus();
  }

  private void createCombo() {
    myComboBox = new SchemesCombo<T>() {
      @Override
      protected boolean supportsProjectSchemes() {
        return mySchemesPanel.getModel().supportsProjectSchemes();
      }

      @Override
      protected boolean isProjectScheme(@NotNull T scheme) {
        return mySchemesPanel.getModel().isProjectScheme(scheme);
      }

      @NotNull
      @Override
      protected SimpleTextAttributes getSchemeAttributes(T scheme) {
        SchemesModel<T> model = mySchemesPanel.getModel();
        SimpleTextAttributes baseAttributes = model.canDeleteScheme(scheme)
               ? SimpleTextAttributes.REGULAR_ATTRIBUTES
               : SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES;
        if (model.canResetScheme(scheme) && model.differsFromDefault(scheme)) {
          return baseAttributes.derive(-1, JBColor.BLUE, null, null);
        }
        return baseAttributes;
      }
    };
    myComboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        mySchemesPanel.getActions().onSchemeChanged(getSelectedScheme());
      }
    });
  }
  
  public void startEdit() {
    T scheme = getSelectedScheme();
    if (scheme != null) {
      showHint();
      myNameEditorField.setText(scheme.getName());
      myLayout.last(myRootPanel);
      myNameEditorField.requestFocus();
    }
  }

  public void resetSchemes(@NotNull Collection<T> schemes) {
    myComboBox.resetSchemes(schemes);
  }

  @Nullable
  public T getSelectedScheme() {
    return myComboBox.getSelectedScheme();
  }

  public void selectScheme(@Nullable T scheme) {
    myComboBox.selectScheme(scheme);
  }
  
  public JComponent getComponent() {
    return myRootPanel;
  }

  @Nullable
  private String validateSchemeName(@NotNull String name, boolean isProjectScheme) {
    if (name.isEmpty()) {
      return EMPTY_NAME_MESSAGE;
    }
    else if (mySchemesPanel.getModel().containsScheme(name, isProjectScheme)) {
      return NAME_ALREADY_EXISTS_MESSAGE;
    }
    return null;
  }
  

}
