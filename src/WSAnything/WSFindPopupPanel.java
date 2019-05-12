// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package WSAnything;

import WSAnything.Core.*;
import WSAnything.UI.*;
import com.intellij.CommonBundle;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.find.*;
import com.intellij.find.editorHeaderActions.ShowMoreOptions;
import com.intellij.find.impl.*;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.ui.UISettings;
import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.MnemonicHelper;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.application.ApplicationActivationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.progress.util.ReadTask;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.ui.popup.ComponentPopupBuilder;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopeUtil;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.ui.table.JBTable;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.FindUsagesProcessPresentation;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.usages.UsageViewPresentation;
import com.intellij.usages.impl.UsagePreviewPanel;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.*;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class WSFindPopupPanel extends JBPanel implements FindUI,WSEventListener  {
    private static final Logger LOG = Logger.getInstance(WSFindPopupPanel.class);

    private static final KeyStroke ENTER = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0);
    private static final KeyStroke ENTER_WITH_MODIFIERS =
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, SystemInfo.isMac ? InputEvent.META_DOWN_MASK : InputEvent.ALT_DOWN_MASK);
    private static final KeyStroke C_WITH_MODIFIERS =
            KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.ALT_DOWN_MASK);
    private static final KeyStroke F_WITH_MODIFIERS =
            KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.ALT_DOWN_MASK);
    private static final KeyStroke REPLACE_ALL =
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK | InputEvent.ALT_MASK);

    private static final String SERVICE_KEY = "find.popup";
    private static final String SPLITTER_SERVICE_KEY = "find.popup.splitter";
    @NotNull
    private final WSFindUIHelper myHelper;
    @NotNull
    private final Project myProject;
    @NotNull
    private final Disposable myDisposable;
    private final Alarm myPreviewUpdater;
    //@NotNull private final FindPopupScopeUI myScopeUI;
    private JComponent myCodePreviewComponent;
    private SearchTextArea mySearchTextArea;
    private SearchTextArea myReplaceTextArea;
    private ActionListener myOkActionListener;
    private final AtomicBoolean myCanClose = new AtomicBoolean(true);
    private final AtomicBoolean myIsPinned = new AtomicBoolean(false);
    private JBLabel myFileCountHintLabel;
    private JBLabel myLineCountHintLabel;
    private JBLabel myCopyResultHintLabel;
    private Alarm mySearchRescheduleOnCancellationsAlarm;
    private volatile ProgressIndicatorBase myResultsPreviewSearchProgress;

    private JLabel myTitleLabel;
    private JButton myHistoryPrev;
    private JButton myHistoryNext;

    private StateRestoringCheckBox myCbCaseSensitive;
    private StateRestoringCheckBox myCbPreserveCase;
    private StateRestoringCheckBox myCbWholeWordsOnly;
    private StateRestoringCheckBox myCbRegularExpressions;
    private StateRestoringCheckBox myCbFileFilter;
    private StateRestoringCheckBox myConsiderFileName;
    private StateRestoringCheckBox myCurrFileOnly;
    private StateRestoringCheckBox myFollowCursor;
    private JBLabel myCurrFileOnlyHintLabel;
    private JBLabel myConsiderFileNameHintLabel;
    private ActionToolbarImpl myScopeSelectionToolbar;
    private ComboBox myFileMaskField;
    private ActionButton myFilterContextButton;
    private ActionButton myTabResultsButton;
    private ActionButton myPinButton;
    private JButton myCopyResultButton;
    private JButton myReplaceAllButton;
    private JButton myReplaceSelectedButton;
    private JTextArea mySearchComponent;
    private JTextArea myReplaceComponent;
    private String mySelectedContextName = FindBundle.message("find.context.anywhere.scope.label");
    private JPanel myScopeDetailsPanel;

    private JBTable myResultsPreviewTable;  // 结果列表
    private UsagePreviewPanel myUsagePreviewPanel;  // 使用预览
    private JBPopup myBalloon;
    private LoadingDecorator myLoadingDecorator;
    private int myLoadingHash;
    private JPanel myTitlePanel;
    private String myUsagesCount;
    private String myFilesCount;
    private UsageViewPresentation myUsageViewPresentation;
    private Boolean m_bFirstTime = true;
    private static int s_tmpIndex = 0;
    private CaretListener m_lastCaretListener;
    private CaretModel m_lastCaretModel;
    private static WSFindPopupPanel pInstance;
    private static List<WSFindTextResult> g_Historys = new ArrayList<>();
    private int g_historyIndex = -1;

    public WSFindPopupPanel(Project pro) {
        WSProjectListener.getInstance().getWSProject().registerEventListener(this);
        WSFindUIHelper helper = new WSFindUIHelper(pro);
        myHelper = helper;
        myProject = myHelper.getProject();
        myDisposable = Disposer.newDisposable();
        myPreviewUpdater = new Alarm(myDisposable);
        //myScopeUI = FindPopupScopeUIProvider.getInstance().create(this);

        Disposer.register(myDisposable, () -> {
            finishPreviousPreviewSearch();
            if (mySearchRescheduleOnCancellationsAlarm != null)
                Disposer.dispose(mySearchRescheduleOnCancellationsAlarm);
            if (myUsagePreviewPanel != null) Disposer.dispose(myUsagePreviewPanel);
        });

        initComponents();
        initByModel();
        initKeyBoardShortCut();

        ApplicationManager.getApplication().invokeLater(this::scheduleResultsUpdate, ModalityState.any());
        if (SystemInfo.isWindows) {
            ApplicationManager.getApplication().getMessageBus()
                    .connect(myDisposable).subscribe(ApplicationActivationListener.TOPIC,
                    new ApplicationActivationListener() {
                        @Override
                        public void applicationDeactivated(IdeFrame ideFrame) {
                            closeImmediately();
                        }
                    });
        }
        WSProjectListener.getInstance().getWSProject().registerEventListener(this);
        ApplicationManager.getApplication().invokeLater(()->{
            if(WSUtil.isCurrFileTmpFile()) {
                myCurrFileOnly.setSelected(true);
                myCurrFileOnly.setEnabled(false);
                myConsiderFileName.setEnabled(false);
            }
        });
    }

    public void initKeyBoardShortCut() {
        new AnAction() {
            @Override
            public void actionPerformed(AnActionEvent e) {
                myCurrFileOnly.setEnabled(true);
                myCurrFileOnly.doClick();
            }
        }.registerCustomShortcutSet(new CustomShortcutSet(C_WITH_MODIFIERS), this);

        new AnAction() {
            @Override
            public void actionPerformed(AnActionEvent e) {
                myConsiderFileName.setEnabled(true);
                myConsiderFileName.doClick();
            }
        }.registerCustomShortcutSet(new CustomShortcutSet(F_WITH_MODIFIERS), this);

    }
    @Override
    public void showUI() {
        FSLog.log.info("ShowUI begin");
        if (myBalloon != null && myBalloon.isVisible()) {
            return;
        }
        if (myBalloon != null && !myBalloon.isDisposed()) {
            myBalloon.cancel();
        }
        if (myBalloon == null || myBalloon.isDisposed()) {
            final ComponentPopupBuilder builder = JBPopupFactory.getInstance().createComponentPopupBuilder(this, mySearchComponent);
            myBalloon = builder
                    .setProject(myHelper.getProject())
                    .setMovable(true)
                    .setResizable(true)
                    .setMayBeParent(true)
                    .setCancelOnClickOutside(true)
                    .setRequestFocus(true)
                    .setCancelKeyEnabled(false)
                    .setCancelCallback(() -> {
                        //mario memo close
                        boolean canBeClosed = canBeClosed();
                        if (canBeClosed) {
                            saveSettings();
                        }
                        return canBeClosed;
                    })
                    .createPopup();
            Disposer.register(myBalloon, myDisposable);
            registerCloseAction(myBalloon);
            final Window window = WindowManager.getInstance().suggestParentWindow(myProject);
            Component parent = UIUtil.findUltimateParent(window);
            RelativePoint showPoint = null;
            Point screenPoint = DimensionService.getInstance().getLocation(SERVICE_KEY);
            if (screenPoint != null) {
                if (parent != null) {
                    SwingUtilities.convertPointFromScreen(screenPoint, parent);
                    showPoint = new RelativePoint(parent, screenPoint);
                } else {
                    showPoint = new RelativePoint(screenPoint);
                }
            }
            if (parent != null && showPoint == null) {
                int height = UISettings.getInstance().getShowNavigationBar() ? 135 : 115;
                if (parent instanceof IdeFrameImpl && ((IdeFrameImpl) parent).isInFullScreen()) {
                    height -= 20;
                }
                showPoint = new RelativePoint(parent, new Point((parent.getSize().width - getPreferredSize().width) / 2, height));
            }
            mySearchComponent.selectAll();
            WindowMoveListener windowListener = new WindowMoveListener(this);
            myTitlePanel.addMouseListener(windowListener);
            myTitlePanel.addMouseMotionListener(windowListener);
            addMouseListener(windowListener);
            addMouseMotionListener(windowListener);
            Dimension panelSize = getPreferredSize();
            Dimension prev = DimensionService.getInstance().getSize(SERVICE_KEY);
            if (!myCbPreserveCase.isVisible()) {
                panelSize.width += myCbPreserveCase.getPreferredSize().width + 8;
            }
            panelSize.width += JBUI.scale(24);//hidden 'loading' icon
            panelSize.height *= 2;
            if (prev != null && prev.height < panelSize.height) prev.height = panelSize.height;
            myBalloon.setMinimumSize(panelSize);
            if (prev == null) {
                panelSize.height *= 1.5;
                panelSize.width *= 1.15;
            }
            myBalloon.setSize(prev != null ? prev : panelSize);

            IdeEventQueue.getInstance().getPopupManager().closeAllPopups(false);
            if (showPoint != null && showPoint.getComponent() != null) {
                myBalloon.show(showPoint);
            } else {
                myBalloon.showCenteredInCurrentWindow(myProject);
            }
            JRootPane rootPane = getRootPane();
            if (rootPane != null && myHelper.isReplaceState()) {
                rootPane.setDefaultButton(myReplaceSelectedButton);
            }
        }
        FSLog.log.info("ShowUI end");
    }

    @Override
    public void doLayout() {
        Dimension prefSize = getMinimumSize();
        if (myBalloon instanceof AbstractPopup && !myBalloon.isDisposed()) {
            // Window window = ((AbstractPopup)myBalloon).getPopupWindow();
            //Dimension minimumSize = window != null ? window.getMinimumSize() : null;
            myBalloon.setMinimumSize(prefSize);
 /*     if (minimumSize != null && prefSize.width > minimumSize.width) {
        myBalloon.moveToFitScreen();
      }*/
            if (m_bFirstTime) {
                m_bFirstTime = false;
                myBalloon.moveToFitScreen();
            }
        }

        super.doLayout();
    }

    protected boolean canBeClosed() {
        myBalloon.moveToFitScreen();
        if (myIsPinned.get()) return false;

        /*if (!myCanClose.get()) return false;


        if (!ApplicationManager.getApplication().isActive()) return SystemInfo.isWindows;
    if (KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow() == null) return SystemInfo.isWindows;
    if (myFileMaskField.isPopupVisible()) {
      myFileMaskField.setPopupVisible(false);
      return false;
    }
    List<JBPopup> popups = ContainerUtil.filter(JBPopupFactory.getInstance().getChildPopups(this), popup -> !popup.isDisposed());
    if (!popups.isEmpty()) {
      for (JBPopup popup : popups) {
        popup.cancel();
      }
      return false;
    }
    return !myScopeUI.hideAllPopups();*/
        return true;
    }

    public void saveSettings() {
        // save last search string
        FindInProjectSettings findInProjectSettings = FindInProjectSettings.getInstance(myProject);
        String str = this.getStringToFind();
        findInProjectSettings.addStringToFind(str);

 /*   DimensionService.getInstance().setSize(SERVICE_KEY, myBalloon.getSize(), myHelper.getProject() );
    DimensionService.getInstance().setLocation(SERVICE_KEY, myBalloon.getLocationOnScreen(), myHelper.getProject() );
    FindSettings findSettings = FindSettings.getInstance();
    myScopeUI.applyTo(findSettings, mySelectedScope);
    myHelper.updateFindSettings();
    applyTo(FindManager.getInstance(myProject).getFindInProjectModel());*/
    }

    @NotNull
    @Override
    public Disposable getDisposable() {
        return myDisposable;
    }

    @NotNull
    public Project getProject() {
        return myProject;
    }

    @NotNull
    public WSFindUIHelper getHelper() {
        return myHelper;
    }

    @NotNull
    public JBPopup getBalloon() {
        return myBalloon;
    }

    @NotNull
    public AtomicBoolean getCanClose() {
        return myCanClose;
    }

    private void initComponents() {
        myTitleLabel = new JBLabel(FindBundle.message("find.in.path.dialog.title"), UIUtil.ComponentStyle.REGULAR);
        myTitleLabel.setFont(myTitleLabel.getFont().deriveFont(Font.BOLD));
        //myTitleLabel.setBorder(JBUI.Borders.emptyRight(16));
        myLoadingDecorator = new LoadingDecorator(new JLabel(EmptyIcon.ICON_16), getDisposable(), 250, true, new AsyncProcessIcon("FindInPathLoading"));
        myLoadingDecorator.setLoadingText("");
        myCbCaseSensitive = createCheckBox("find.popup.case.sensitive");
        ItemListener liveResultsPreviewUpdateListener = __ -> scheduleResultsUpdate();
        myCbCaseSensitive.addItemListener(liveResultsPreviewUpdateListener);
        myCbPreserveCase = createCheckBox("find.options.replace.preserve.case");
        myCbPreserveCase.addItemListener(liveResultsPreviewUpdateListener);
        myCbPreserveCase.setVisible(/*myHelper.getModel().isReplaceState()*/false);
        myCbWholeWordsOnly = createCheckBox("find.popup.whole.words");
        myCbWholeWordsOnly.addItemListener(liveResultsPreviewUpdateListener);
        myCbRegularExpressions = createCheckBox("find.popup.regex");
        myCbRegularExpressions.addItemListener(liveResultsPreviewUpdateListener);
        myCbFileFilter = createCheckBox("find.popup.filemask");
        myConsiderFileName = createCheckBox("Consider FileName");
        myCurrFileOnly = createCheckBox("Search CurrFile Only");
        myCurrFileOnly.setText("Search CurrFile Only");
        myFollowCursor = createCheckBox("Follow Cursor");
        myFollowCursor.setText("Follow Cursor");
        myConsiderFileName.setText("Consider FileName");
        myCurrFileOnlyHintLabel = new JBLabel("");
        myCurrFileOnlyHintLabel.setEnabled(false);
        myConsiderFileNameHintLabel = new JBLabel("");
        myConsiderFileNameHintLabel.setEnabled(false);
        myCurrFileOnlyHintLabel.setText(KeymapUtil.getKeystrokeText(C_WITH_MODIFIERS));
        myConsiderFileNameHintLabel.setText(KeymapUtil.getKeystrokeText(F_WITH_MODIFIERS));

        myCurrFileOnly.addItemListener(__ ->{
            this.findSettingsChanged();
        });
        myConsiderFileName.addItemListener(__ ->{
            this.findSettingsChanged();
        });

        myFollowCursor.addItemListener(__ ->{
            this.toggleFollowCursor();
        });

        myCbFileFilter.addItemListener(__ -> {
            if (myCbFileFilter.isSelected()) {
                myFileMaskField.setEnabled(true);
                if (myCbFileFilter.getClientProperty("dontRequestFocus") == null) {
                    IdeFocusManager.getInstance(myProject).requestFocus(myFileMaskField, true);
                    myFileMaskField.getEditor().selectAll();
                }
            } else {
                myFileMaskField.setEnabled(false);
                if (myCbFileFilter.getClientProperty("dontRequestFocus") == null) {
                    IdeFocusManager.getInstance(myProject).requestFocus(mySearchComponent, true);
                }
            }
        });
        myCbFileFilter.addItemListener(liveResultsPreviewUpdateListener);
        myFileMaskField = new ComboBox() {

            @Override
            public Dimension getPreferredSize() {
                int width = 0;
                int buttonWidth = 0;
                Component[] components = getComponents();
                for (Component component : components) {
                    Dimension size = component.getPreferredSize();
                    int w = size != null ? size.width : 0;
                    if (component instanceof JButton) {
                        buttonWidth = w;
                    }
                    width += w;
                }
                ComboBoxEditor editor = getEditor();
                if (editor != null) {
                    Component editorComponent = editor.getEditorComponent();
                    if (editorComponent != null) {
                        FontMetrics fontMetrics = editorComponent.getFontMetrics(editorComponent.getFont());
                        width = Math.max(width, fontMetrics.stringWidth(String.valueOf(getSelectedItem())) + buttonWidth);
                        //Let's reserve some extra space for just one 'the next' letter
                        width += fontMetrics.stringWidth("m");
                    }
                }
                Dimension size = super.getPreferredSize();
                Insets insets = getInsets();
                width += insets.left + insets.right;
                size.width = Math.min(JBUI.scale(500), Math.max(JBUI.scale(80), width));
                return size;
            }
        };
        myFileMaskField.setEditable(true);
        myFileMaskField.setMaximumRowCount(8);
        myFileMaskField.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                scheduleResultsUpdate();
            }
        });
        Component editorComponent = myFileMaskField.getEditor().getEditorComponent();
        if (editorComponent instanceof EditorTextField) {
            final EditorTextField etf = (EditorTextField) editorComponent;
            etf.addDocumentListener(new DocumentListener() {
                @Override
                public void documentChanged(com.intellij.openapi.editor.event.DocumentEvent event) {
                    onFileMaskChanged();
                }
            });
        } else {
            if (editorComponent instanceof JTextComponent) {
                ((JTextComponent) editorComponent).getDocument().addDocumentListener(new DocumentAdapter() {
                    @Override
                    protected void textChanged(DocumentEvent e) {
                        onFileMaskChanged();
                    }
                });
            } else {
                assert false;
            }
        }

        AnAction myShowFilterPopupAction = new MyShowFilterPopupAction();
        myFilterContextButton =
                new ActionButton(myShowFilterPopupAction, myShowFilterPopupAction.getTemplatePresentation(), ActionPlaces.UNKNOWN,
                        ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE) {
                    @Override
                    public int getPopState() {
                        int state = super.getPopState();
                        if (state != ActionButtonComponent.NORMAL) return state;
                        return mySelectedContextName.equals(FindDialog.getPresentableName(FindModel.SearchContext.ANY))
                                ? ActionButtonComponent.NORMAL
                                : ActionButtonComponent.PUSHED;
                    }
                };
        myShowFilterPopupAction.registerCustomShortcutSet(myShowFilterPopupAction.getShortcutSet(), this);
        ToggleAction pinAction = new ToggleAction(null, null, AllIcons.General.Pin_tab) {
            @Override
            public boolean isDumbAware() {
                return true;
            }

            @Override
            public boolean isSelected(AnActionEvent e) {
                return myIsPinned.get();
            }

            @Override
            public void setSelected(AnActionEvent e, boolean state) {
                myIsPinned.set(state);
            }
        };
        myPinButton = new ActionButton(pinAction, pinAction.getTemplatePresentation(), ActionPlaces.UNKNOWN, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE);


        DefaultActionGroup tabResultsContextGroup = new DefaultActionGroup();
        tabResultsContextGroup.add(new ToggleAction(FindBundle.message("find.options.skip.results.tab.with.one.usage.checkbox")) {
            @Override
            public boolean isSelected(AnActionEvent e) {
                return myHelper.isSkipResultsWithOneUsage();
            }

            @Override
            public void setSelected(AnActionEvent e, boolean state) {
                myHelper.setSkipResultsWithOneUsage(state);
            }

            @Override
            public void update(@NotNull AnActionEvent e) {
                super.update(e);
                e.getPresentation().setVisible(!myHelper.isReplaceState());
            }
        });
        tabResultsContextGroup.add(new ToggleAction(FindBundle.message("find.open.in.new.tab.checkbox")) {
            @Override
            public boolean isSelected(AnActionEvent e) {
                return myHelper.isUseSeparateView();
            }

            @Override
            public void setSelected(AnActionEvent e, boolean state) {
                myHelper.setUseSeparateView(state);
            }

            @Override
            public void update(@NotNull AnActionEvent e) {
                super.update(e);
                e.getPresentation().setEnabled(myHelper.getModel().isOpenInNewTabEnabled());
                e.getPresentation().setVisible(myHelper.getModel().isOpenInNewTabVisible());
            }
        });
        tabResultsContextGroup.setPopup(true);
        Presentation tabSettingsPresentation = new Presentation();
        tabSettingsPresentation.setIcon(AllIcons.General.SecondaryGroup);
        myTabResultsButton =
                new ActionButton(tabResultsContextGroup, tabSettingsPresentation, ActionPlaces.UNKNOWN, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE);
/*        new AnAction() {
            @Override
            public void actionPerformed(AnActionEvent e) {
                myTabResultsButton.click();
            }
        }.registerCustomShortcutSet(CustomShortcutSet.fromString("alt DOWN"), this);*/
        myHistoryPrev = new JButton("",AllIcons.General.ArrowLeft);
        myHistoryNext = new JButton("",AllIcons.General.ArrowRight);

        myHistoryPrev.addActionListener(__ -> onClickPrev());
        myHistoryNext.addActionListener(__ -> onClickNext());


        myCopyResultButton = new JButton(FindBundle.message("find.popup.find.button"));
        myReplaceAllButton = new JButton(FindBundle.message("find.popup.replace.all.button"));
        myReplaceSelectedButton = new JButton(FindBundle.message("find.popup.replace.selected.button", 0));

        myOkActionListener = __ -> doOK_new(true);
        myCopyResultButton.addActionListener(myOkActionListener);
        boolean enterAsOK = Registry.is("ide.find.enter.as.ok", false);

        new DumbAwareAction() {
            @Override
            public void update(AnActionEvent e) {
                e.getPresentation().setEnabled(
                        e.getData(CommonDataKeys.EDITOR) == null ||
                                SwingUtilities.isDescendingFrom(e.getData(PlatformDataKeys.CONTEXT_COMPONENT), myFileMaskField));
            }

            @Override
            public void actionPerformed(AnActionEvent e) {
                if (SwingUtilities.isDescendingFrom(e.getData(PlatformDataKeys.CONTEXT_COMPONENT), myFileMaskField) && myFileMaskField.isPopupVisible()) {
                    myFileMaskField.hidePopup();
                    return;
                }
                if (enterAsOK) {
                    myOkActionListener.actionPerformed(null);
                } else {
                    if (myHelper.isReplaceState()) {
                        myReplaceSelectedButton.doClick();
                    } else {
                        navigateToSelectedUsage(null);
                    }
                }
            }
        }.registerCustomShortcutSet(new CustomShortcutSet(ENTER), this);
        DumbAwareAction.create(e -> {
            if (enterAsOK) {
                navigateToSelectedUsage(null);
            } else {
                myOkActionListener.actionPerformed(null);
            }
        }).registerCustomShortcutSet(new CustomShortcutSet(ENTER_WITH_MODIFIERS), this);

        DumbAwareAction.create(e -> {
            myReplaceAllButton.doClick();
        }).registerCustomShortcutSet(new CustomShortcutSet(REPLACE_ALL), this);
        myReplaceAllButton.setToolTipText(KeymapUtil.getKeystrokeText(REPLACE_ALL));

        List<Shortcut> navigationKeyStrokes = ContainerUtil.newArrayList();
        KeyStroke viewSourceKeyStroke = KeymapUtil.getKeyStroke(CommonShortcuts.getViewSource());
        if (viewSourceKeyStroke != null && !Comparing.equal(viewSourceKeyStroke, ENTER_WITH_MODIFIERS) && !Comparing.equal(viewSourceKeyStroke, ENTER)) {
            navigationKeyStrokes.add(new KeyboardShortcut(viewSourceKeyStroke, null));
        }
        KeyStroke editSourceKeyStroke = KeymapUtil.getKeyStroke(CommonShortcuts.getEditSource());
        if (editSourceKeyStroke != null && !Comparing.equal(editSourceKeyStroke, ENTER_WITH_MODIFIERS) && !Comparing.equal(editSourceKeyStroke, ENTER)) {
            navigationKeyStrokes.add(new KeyboardShortcut(editSourceKeyStroke, null));
        }
        if (!navigationKeyStrokes.isEmpty()) {
            new AnAction() {
                @Override
                public void actionPerformed(AnActionEvent e) {
                    navigateToSelectedUsage(e);
                }
            }.registerCustomShortcutSet(new CustomShortcutSet(navigationKeyStrokes.toArray(Shortcut.EMPTY_ARRAY)), this);
        }

        mySearchComponent = new JTextArea();
        mySearchComponent.setColumns(25);
        mySearchComponent.setRows(1);
        myReplaceComponent = new JTextArea();
        myReplaceComponent.setColumns(25);
        myReplaceComponent.setRows(1);
        mySearchTextArea = new SearchTextArea(mySearchComponent, true, true);
        myReplaceTextArea = new SearchTextArea(myReplaceComponent, false, false);
        mySearchTextArea.setMultilineEnabled(false);
        myReplaceTextArea.setMultilineEnabled(false);

        //Pair<FindPopupScopeUI.ScopeType, JComponent>[] scopeComponents = myScopeUI.getComponents();
        //Pair<FindPopupScopeUI.ScopeType, JComponent>[] scopeComponents = new Pair<FindPopupScopeUI.ScopeType, JComponent>[]{};
        myScopeDetailsPanel = new JPanel(new CardLayout());
        myScopeDetailsPanel.setBorder(JBUI.Borders.emptyBottom(UIUtil.isUnderDefaultMacTheme() ? 0 : 3));

        //List<AnAction> scopeActions = new ArrayList<>(scopeComponents.length);
        List<AnAction> scopeActions = new ArrayList<>();
/*    for (Pair<FindPopupScopeUI.ScopeType, JComponent> scopeComponent : scopeComponents) {
      FindPopupScopeUI.ScopeType scopeType = scopeComponent.first;
      scopeActions.add(new MySelectScopeToggleAction(scopeType));
      myScopeDetailsPanel.add(scopeType.name, scopeComponent.second);
    }*/
        myScopeSelectionToolbar = createToolbar(scopeActions.toArray(AnAction.EMPTY_ARRAY));
        myScopeSelectionToolbar.setMinimumButtonSize(ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE);
        //mySelectedScope = scopeComponents[0].first;

        myScopeSelectionToolbar = createToolbar();

        myResultsPreviewTable = new JBTable() {
            @Override
            public Dimension getPreferredScrollableViewportSize() {
                return new Dimension(getWidth(), 1 + getRowHeight() * 4);
            }
        };
        myResultsPreviewTable.setFocusable(false);
        myResultsPreviewTable.getEmptyText().setShowAboveCenter(false);
        myResultsPreviewTable.setShowColumns(false);
        myResultsPreviewTable.getSelectionModel().setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        myResultsPreviewTable.setShowGrid(false);
        myResultsPreviewTable.setIntercellSpacing(JBUI.emptySize());
        new DoubleClickListener() {
            @Override
            protected boolean onDoubleClick(MouseEvent event) {
                if (event.getSource() != myResultsPreviewTable) return false;
                navigateToSelectedUsage(null);
                return true;
            }
        }.installOn(myResultsPreviewTable);
        myResultsPreviewTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                myResultsPreviewTable.transferFocus();
            }
        });
        applyFont(JBUI.Fonts.label(), myCbCaseSensitive, myCbPreserveCase, myCbWholeWordsOnly, myCbRegularExpressions,
                myResultsPreviewTable);
        ScrollingUtil.installActions(myResultsPreviewTable, false, mySearchComponent);
        ScrollingUtil.installActions(myResultsPreviewTable, false, myReplaceComponent);
        ScrollingUtil.installActions(myResultsPreviewTable, false, myReplaceSelectedButton);

        ActionListener helpAction = __ -> HelpManager.getInstance().invokeHelp("reference.dialogs.findinpath");
        registerKeyboardAction(helpAction, KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        registerKeyboardAction(helpAction, KeyStroke.getKeyStroke(KeyEvent.VK_HELP, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        myUsageViewPresentation = new UsageViewPresentation();
        myUsagePreviewPanel = new UsagePreviewPanel(myProject, myUsageViewPresentation, Registry.is("ide.find.as.popup.editable.code")) {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(myResultsPreviewTable.getWidth(), Math.max(getHeight(), getLineHeight() * 15));
            }
        };
        Disposer.register(myDisposable, myUsagePreviewPanel);
        /// mariotodo preview
        final Runnable updatePreviewRunnable = () -> {
            if (Disposer.isDisposed(myDisposable)) return;
            int[] selectedRows = myResultsPreviewTable.getSelectedRows();
            final List<UsageInfo> selection = new SmartList<>();
            VirtualFile file = null;
            for (int row : selectedRows) {
                WSFindTextResult result = (WSFindTextResult) myResultsPreviewTable.getModel().getValueAt(row, 0);
                UsageInfo2UsageAdapter adapter = WSUtil.getMergedUsageAdapter(result);
                if(adapter != null) {
                    file = adapter.getFile();
                    if (adapter.isValid()) {
                        selection.addAll(Arrays.asList(adapter.getMergedInfos()));
                    }
                }
            }
            String title = file != null ? getTitle(file) : "null";
            myReplaceSelectedButton.setText(FindBundle.message("find.popup.replace.selected.button", selection.size()));
            //FindInProjectUtil.setupViewPresentation(myUsageViewPresentation, FindSettings.getInstance().isShowResultsInSeparateView(), myHelper.getModel().clone());
            myUsagePreviewPanel.updateLayout(selection);
            if (myUsagePreviewPanel.getCannotPreviewMessage(selection) == null && title != null) {
                myUsagePreviewPanel.setBorder(IdeBorderFactory.createTitledBorder(title, false, new JBInsets(8, 0, 0, 0)).setShowLine(false));
            } else {
                myUsagePreviewPanel.setBorder(JBUI.Borders.empty());
            }
        };
        myResultsPreviewTable.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            myPreviewUpdater.addRequest(updatePreviewRunnable, 50); //todo[vasya]: remove this dirty hack of updating preview panel after clicking on Replace button
        });
        DocumentAdapter documentAdapter = new DocumentAdapter() {
            @Override
            protected void textChanged(DocumentEvent e) {
                String text = mySearchComponent.getText();
                int len = text.length();
                mySearchComponent.setRows(Math.max(1, Math.min(3, StringUtil.countChars(mySearchComponent.getText(), '\n') + 1)));
                myReplaceComponent.setRows(Math.max(1, Math.min(3, StringUtil.countChars(myReplaceComponent.getText(), '\n') + 1)));

                if (myBalloon == null) return;
                if (e.getDocument() == mySearchComponent.getDocument()) {
                    scheduleResultsUpdate();
                }
                if (e.getDocument() == myReplaceComponent.getDocument()) {
                    applyTo(myHelper.getModel());
                    ApplicationManager.getApplication().invokeLater(updatePreviewRunnable);
                }
            }
        };
        mySearchComponent.getDocument().addDocumentListener(documentAdapter);
        myReplaceComponent.getDocument().addDocumentListener(documentAdapter);

        mySearchRescheduleOnCancellationsAlarm = new Alarm();

        JBSplitter splitter = new JBSplitter(true, .33f);
        splitter.setSplitterProportionKey(SPLITTER_SERVICE_KEY);
        splitter.setDividerWidth(JBUI.scale(2));
        splitter.getDivider().setBackground(OnePixelDivider.BACKGROUND);
        JBScrollPane scrollPane = new JBScrollPane(myResultsPreviewTable) {
            @Override
            public Dimension getMinimumSize() {
                Dimension size = super.getMinimumSize();
                size.height = myResultsPreviewTable.getPreferredScrollableViewportSize().height;
                return size;
            }
        };
        scrollPane.setBorder(JBUI.Borders.empty());
        splitter.setFirstComponent(scrollPane);

        JPanel bottomPanel = new JPanel(new MigLayout("flowx, ins 4 4 0 4, fillx, hidemode 2, gap 0"));

        myCopyResultHintLabel = new JBLabel("");
        myCopyResultHintLabel.setEnabled(false);
        bottomPanel.add(myCopyResultHintLabel,"dock west,gap left 3");
        bottomPanel.add(myCopyResultButton,"dock west");
        bottomPanel.add(myFollowCursor,"dock west");

        myFileCountHintLabel = new JBLabel("");
        myLineCountHintLabel = new JBLabel("");
        bottomPanel.add(Box.createHorizontalGlue(), "growx, pushx");
        bottomPanel.add(myFileCountHintLabel,"wrap");
        bottomPanel.add(Box.createHorizontalGlue(), "growx, pushx");
        bottomPanel.add(myLineCountHintLabel);




        ApplicationManager.getApplication().invokeLater(() -> {
            updateFileLineNums();
        });


        myCodePreviewComponent = myUsagePreviewPanel.createComponent();
        splitter.setSecondComponent(myCodePreviewComponent);
        JPanel scopesPanel = new JPanel(new MigLayout("flowx, gap 26, ins 0"));
        scopesPanel.add(myScopeSelectionToolbar.getComponent());
        scopesPanel.add(myScopeDetailsPanel, "growx, pushx");
        setLayout(new MigLayout("flowx, ins 4, gap 0, fillx, hidemode 3"));
        int cbGapLeft = myCbCaseSensitive.getInsets().left;
        int cbGapRight = myCbCaseSensitive.getInsets().right;
        myTitlePanel = new JPanel(new MigLayout("flowx, ins 0, gap 0, fillx, filly"));
        myTitlePanel.add(myTitleLabel);
        myTitlePanel.add(myLoadingDecorator.getComponent(), "w 24, wmin 24");
        myTitlePanel.add(myHistoryPrev);
        myTitlePanel.add(myHistoryNext);

        myTitlePanel.add(Box.createHorizontalGlue(), "growx, pushx");

        int gap = Math.max(0, JBUI.scale(16) - cbGapLeft - cbGapRight);
        JPanel regexpPanel = new JPanel(new BorderLayout(4, 5));
        regexpPanel.add(myCbRegularExpressions, BorderLayout.CENTER);
        regexpPanel.add(RegExHelpPopup.createRegExLink("<html><body><b>?</b></body></html>", myCbRegularExpressions, LOG), BorderLayout.EAST);
        AnAction[] actions = {
/*      new DefaultCustomComponentAction(() -> myCbCaseSensitive),
      new DefaultCustomComponentAction(() -> JBUI.Borders.emptyLeft(gap).wrap(myCbPreserveCase)),
      new DefaultCustomComponentAction(() -> JBUI.Borders.emptyLeft(gap).wrap(myCbWholeWordsOnly)),
      new DefaultCustomComponentAction(() -> JBUI.Borders.emptyLeft(gap).wrap(regexpPanel)),*/
        };

        @SuppressWarnings("InspectionUniqueToolbarId")
        ActionToolbarImpl checkboxesToolbar =
                (ActionToolbarImpl) ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, new DefaultActionGroup(actions), true);
/*    checkboxesToolbar.setForceShowFirstComponent(true);
    checkboxesToolbar.setSkipWindowAdjustments(true);*/

        add(myTitlePanel, "sx 2, growx, growx 200, growy");
        add(checkboxesToolbar, "gapright 8");
        add(myCurrFileOnlyHintLabel);
        add(myCurrFileOnly,"gapright 16");
        add(myConsiderFileNameHintLabel,"gapleft 4,");
        add(myConsiderFileName,"gapright 16");
        //add(myCbFileFilter);
        //add(myFileMaskField, "gapleft 4, gapright 16");
        if (Registry.is("ide.find.as.popup.allow.pin") || ApplicationManager.getApplication().isInternal()) {
            JPanel twoButtons = new JPanel(new GridLayout(1, 2, 4, 0));
            //twoButtons.add(myFilterContextButton);
            twoButtons.add(myPinButton);
            add(twoButtons, "wrap");
        } else {
            //add(myFilterContextButton, "wrap");
        }
        add(mySearchTextArea, "pushx, growx, sx 10, gaptop 4, wrap");
        add(myReplaceTextArea, "pushx, growx, sx 10, gaptop 4, wrap");
        add(scopesPanel, "sx 10, pushx, growx, ax left, wrap, gaptop 4, gapbottom 4");
        add(splitter, "pushx, growx, growy, pushy, sx 10, wrap, pad -4 -4 4 4");
        add(bottomPanel, "pushx, growx, dock south, sx 10");

        MnemonicHelper.init(this);
        setFocusCycleRoot(true);
        setFocusTraversalPolicy(new LayoutFocusTraversalPolicy() {
            @Override
            public Component getComponentAfter(Container container, Component c) {
                return c == myResultsPreviewTable ? mySearchComponent : super.getComponentAfter(container, c);
            }
        });
        updateHistoryBtn();
    }

    private void onFileMaskChanged() {
        Object item = myFileMaskField.getEditor().getItem();
        if (item != null && !item.equals(myFileMaskField.getSelectedItem())) {
            myFileMaskField.setSelectedItem(item);
        }
        scheduleResultsUpdate();
    }

    private void closeImmediately() {
        if (canBeClosedImmediately() && myBalloon != null && myBalloon.isVisible()) {
            myIsPinned.set(false);
            myBalloon.cancel();
        }
        WSProjectListener.getInstance().getWSProject().unRegisterEventListener(this);
        removeListenerToLastEditor();
    }

    //Some popups shown above may prevent panel closing, first of all we should close them
    private boolean canBeClosedImmediately() {
        boolean state = myIsPinned.get();
        myIsPinned.set(false);
        try {
            //Here we actually close popups
            return myBalloon != null && canBeClosed();
        } finally {
            myIsPinned.set(state);
        }
    }

    private void doOK(boolean promptOnReplace) {
        if (!canBeClosedImmediately()) {
            return;
        }
        FindModel validateModel = myHelper.getModel().clone();
        applyTo(validateModel);

        ValidationInfo validationInfo = getValidationInfo(validateModel);

        if (validationInfo == null) {
            myHelper.getModel().copyFrom(validateModel);
            myHelper.getModel().setPromptOnReplace(promptOnReplace);
            myHelper.doOKAction();
        } else {
            String message = validationInfo.message;
            Messages.showMessageDialog(
                    this,
                    message,
                    CommonBundle.getErrorTitle(),
                    Messages.getErrorIcon()
            );
            return;
        }
        myIsPinned.set(false);
        myBalloon.cancel();
    }
    private void onClickPrev() {
        if(g_historyIndex > 0) {
            g_historyIndex--;
            String str = getHistoryStr();
            updateSearchByText(str);
            updateHistoryBtn();
        }
    };
    private void onClickNext() {
        if(g_historyIndex + 1 < g_Historys.size()) {
            g_historyIndex++;
            String str = getHistoryStr();
            updateSearchByText(str);
            updateHistoryBtn();
        }
    };
    private String getHistoryStr() {
        if(g_historyIndex >= 0 && g_Historys.size() > g_historyIndex) {
            return g_Historys.get(g_historyIndex).m_textBoxText;
        } else {
            return "";
        }
    }
    private void doOK_new(boolean promptOnReplace) {
        List<WSFindTextResult> result = WSTextFinder.getInstance().getLastResult();
        if(result.isEmpty()) {
            return;
        }
        String text = new String();
        for(int i = 0;i < result.size();++i) {
            text += result.get(i).m_strLine + "\n";
        }



        /// try get curr open file language
        Language language = PlainTextLanguage.INSTANCE;
        String suffix = "";
        try {
            PsiFile currPsiFile = WSUtil.getSelectedEditorPsiFile();
            language = currPsiFile.getLanguage();
            suffix = WSUtil.getFileSuffix(currPsiFile.getVirtualFile().getName());
        } catch (Exception e) {

        }
        /// create and navigate to tmp file
        PsiFile psiFile = WSUtil.createPSIFile(text,language,"tmp_" + (s_tmpIndex++) + suffix);
        WSUtil.openTextEditor(psiFile);
        myBalloon.cancel();
    }

    @Nullable
    private String getTitle(@Nullable VirtualFile file) {
        if (file == null) return null;
        String path = VfsUtilCore.getRelativePath(file, myProject.getBaseDir());
        if (path == null) path = file.getPath();
        return "<html><body>&nbsp;&nbsp;&nbsp;" + path.replace(file.getName(), "<b>" + file.getName() + "</b>") + "</body></html>";
    }

    @NotNull
    private static StateRestoringCheckBox createCheckBox(String message) {
        StateRestoringCheckBox checkBox = new StateRestoringCheckBox(FindBundle.message(message));
        checkBox.setFocusable(false);
        return checkBox;
    }

    private void registerCloseAction(JBPopup popup) {
        final AnAction escape = ActionManager.getInstance().getAction("EditorEscape");
        DumbAwareAction.create(e -> closeImmediately())
                .registerCustomShortcutSet(escape == null ? CommonShortcuts.ESCAPE : escape.getShortcutSet(), popup.getContent(), popup);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        ApplicationManager.getApplication().invokeLater(() -> ScrollingUtil.ensureSelectionExists(myResultsPreviewTable), ModalityState.any());
        myScopeSelectionToolbar.updateActionsImmediately();
    }

    @Override
    public void initByModel() {
        FindModel myModel = myHelper.getModel();
        myCbCaseSensitive.setSelected(myModel.isCaseSensitive());
        myCbWholeWordsOnly.setSelected(myModel.isWholeWordsOnly());
        myCbRegularExpressions.setSelected(myModel.isRegularExpressions());

        mySelectedContextName = "halloa";/*FindDialog.getSearchContextName(myModel);*/
        if (myModel.isReplaceState()) {
            myCbPreserveCase.setSelected(myModel.isPreserveCase());
        }


        boolean isThereFileFilter = myModel.getFileFilter() != null && !myModel.getFileFilter().isEmpty();
        try {
            myCbFileFilter.putClientProperty("dontRequestFocus", Boolean.TRUE);
            myCbFileFilter.setSelected(isThereFileFilter);
        } finally {
            myCbFileFilter.putClientProperty("dontRequestFocus", null);
        }
        myFileMaskField.removeAllItems();
        List<String> variants = Arrays.asList(ArrayUtil.reverseArray(FindSettings.getInstance().getRecentFileMasks()));
        for (String variant : variants) {
            myFileMaskField.addItem(variant);
        }
        if (!variants.isEmpty()) {
            myFileMaskField.setSelectedItem(variants.get(0));
        }
        myFileMaskField.setEnabled(isThereFileFilter);
        String toSearch = WSUtil.getWordAtCaret(WSProjectListener.getInstance().getJBProject());  //myModel.getStringToFind();
        FindInProjectSettings findInProjectSettings = FindInProjectSettings.getInstance(myProject);

        if (StringUtil.isEmpty(toSearch)) {
            String[] history = findInProjectSettings.getRecentFindStrings();
            toSearch = history.length > 0 ? history[history.length - 1] : "";
        }

        mySearchComponent.setText(toSearch);
        String toReplace = myModel.getStringToReplace();

        if (StringUtil.isEmpty(toReplace)) {
            String[] history = findInProjectSettings.getRecentReplaceStrings();
            toReplace = history.length > 0 ? history[history.length - 1] : "";
        }
        myReplaceComponent.setText(toReplace);
        updateControls();
        updateScopeDetailsPanel();

        boolean isReplaceState = myHelper.isReplaceState();
        myTitleLabel.setText(myHelper.getTitle());
        myReplaceTextArea.setVisible(isReplaceState);
        myCbPreserveCase.setVisible(isReplaceState);

        if (Registry.is("ide.find.enter.as.ok", false)) {
            myCopyResultHintLabel.setText(KeymapUtil.getKeystrokeText(ENTER));
        } else {
            myCopyResultHintLabel.setText(KeymapUtil.getKeystrokeText(ENTER_WITH_MODIFIERS));
        }
        //myCopyResultButton.setText(FindBundle.message("find.popup.find.button"));
        myCopyResultButton.setText("Copy All Results");
        myCopyResultButton.setEnabled(false);
    }

    private void updateControls() {
        FindModel myModel = myHelper.getModel();
        if (myCbRegularExpressions.isSelected()) {
            myCbWholeWordsOnly.makeUnselectable(false);
        } else {
            myCbWholeWordsOnly.makeSelectable();
        }
        if (myModel.isReplaceState()) {
            if (myCbRegularExpressions.isSelected() || myCbCaseSensitive.isSelected()) {
                myCbPreserveCase.makeUnselectable(false);
            } else {
                myCbPreserveCase.makeSelectable();
            }

            if (myCbPreserveCase.isSelected()) {
                myCbRegularExpressions.makeUnselectable(false);
                myCbCaseSensitive.makeUnselectable(false);
            } else {
                myCbRegularExpressions.makeSelectable();
                myCbCaseSensitive.makeSelectable();
            }
        }
        myReplaceAllButton.setVisible(myHelper.isReplaceState());
        myReplaceSelectedButton.setVisible(myHelper.isReplaceState());
    }

    private void updateScopeDetailsPanel() {
        ((CardLayout) myScopeDetailsPanel.getLayout()).show(myScopeDetailsPanel, "myScope"/*mySelectedScope.name*/);
        Component firstFocusableComponent =
                UIUtil.uiTraverser(myScopeDetailsPanel).bfsTraversal().find(c -> c.isFocusable() && c.isEnabled() && c.isShowing() &&
                        (c instanceof JComboBox ||
                                c instanceof AbstractButton ||
                                c instanceof JTextComponent));
        myScopeDetailsPanel.revalidate();
        myScopeDetailsPanel.repaint();
        if (firstFocusableComponent != null) {
            ApplicationManager.getApplication().invokeLater(
                    () -> IdeFocusManager.getInstance(myProject).requestFocus(firstFocusableComponent, true));
        }
        if (firstFocusableComponent == null && !mySearchComponent.isFocusOwner() && !myReplaceComponent.isFocusOwner()) {
            ApplicationManager.getApplication().invokeLater(
                    () -> IdeFocusManager.getInstance(myProject).requestFocus(mySearchComponent, true));
        }
    }

    @SuppressWarnings("WeakerAccess")
    public void scheduleResultsUpdate() {
        if (myBalloon == null || !myBalloon.isVisible()) return;
        if (mySearchRescheduleOnCancellationsAlarm == null || mySearchRescheduleOnCancellationsAlarm.isDisposed())
            return;
        updateControls();
        mySearchRescheduleOnCancellationsAlarm.cancelAllRequests();
        mySearchRescheduleOnCancellationsAlarm.addRequest(this::findSettingsChanged, 100);
    }

    private void finishPreviousPreviewSearch() {
        if (myResultsPreviewSearchProgress != null && !myResultsPreviewSearchProgress.isCanceled()) {
            myResultsPreviewSearchProgress.cancel();
        }
    }
    private void toggleFollowCursor() {
        if(myFollowCursor.isSelected()) {
            if(!myIsPinned.get()) {
                myPinButton.click();
            }
            addListenerToCurrEditor();
        } else {
            removeListenerToLastEditor();
        }
    };

    private void findSettingsChanged() {
        if (isShowing()) {
            ScrollingUtil.ensureSelectionExists(myResultsPreviewTable);
        }
        final ModalityState state = ModalityState.current();
        finishPreviousPreviewSearch();
        mySearchRescheduleOnCancellationsAlarm.cancelAllRequests();
        applyTo(myHelper.getModel());
        ValidationInfo result = getValidationInfo(myHelper.getModel());

        final ProgressIndicatorBase progressIndicatorWhenSearchStarted = new ProgressIndicatorBase() {
            @Override
            public void stop() {
                super.stop();
                onStop(System.identityHashCode(this));
            }
        };
        myResultsPreviewSearchProgress = progressIndicatorWhenSearchStarted;
        final int hash = System.identityHashCode(myResultsPreviewSearchProgress);

        //memo
        final WSTableModel model = new WSTableModel() {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        model.addColumn("Usages");
        // Use previously shown usage files as hint for faster search and better usage preview performance if pattern length increased
        final LinkedHashSet<VirtualFile> filesToScanInitially = new LinkedHashSet<>();

   /* if (myHelper.myPreviousModel != null && myHelper.myPreviousModel.getStringToFind().length() < myHelper.getModel().getStringToFind().length()) {
      final DefaultTableModel previousModel = (DefaultTableModel)myResultsPreviewTable.getModel();
      for (int i = 0, len = previousModel.getRowCount(); i < len; ++i) {
        final UsageInfo2UsageAdapter usage = (UsageInfo2UsageAdapter)previousModel.getValueAt(i, 0);
        final VirtualFile file = usage.getFile();
        if (file != null) filesToScanInitially.add(file);
      }
    }
*/
        myHelper.myPreviousModel = myHelper.getModel().clone();

        myReplaceAllButton.setEnabled(false);
        myReplaceSelectedButton.setEnabled(false);
        myReplaceSelectedButton.setText(FindBundle.message("find.popup.replace.selected.button", 0));
        myCodePreviewComponent.setVisible(true);//test true;

        mySearchTextArea.setInfoText(null);
        try {
            myResultsPreviewTable.setModel(model);

        } catch (Exception e) {
            FSLog.log.info(e);
        }

        if (result != null) {
            onStop(hash, result.message);
            return;
        }
        GlobalSearchScope scope = GlobalSearchScopeUtil.toGlobalSearchScope(
                WSFindInProjectUtil.getScopeFromModel(myProject, myHelper.myPreviousModel), myProject);

        myResultsPreviewTable.getColumnModel().getColumn(0).setCellRenderer(
                new WSTableCellRenderer(myCbFileFilter.isSelected(), false, scope)); //mariotodo

        final AtomicInteger resultsCount = new AtomicInteger();
        final AtomicInteger resultsFilesCount = new AtomicInteger();
        FindSettings findSettings = FindSettings.getInstance();
        //FindInProjectUtil.setupViewPresentation(myUsageViewPresentation, findSettings.isShowResultsInSeparateView(), findModel);

        ProgressIndicatorUtils.scheduleWithWriteActionPriority(myResultsPreviewSearchProgress, new ReadTask() {
            @Override
            public Continuation performInReadAction(@NotNull ProgressIndicator indicator) {
                final boolean showPanelIfOnlyOneUsage = !findSettings.isSkipResultsWithOneUsage();

                final FindUsagesProcessPresentation processPresentation =
                        FindInProjectUtil.setupProcessPresentation(myProject, showPanelIfOnlyOneUsage, myUsageViewPresentation);
                ThreadLocal<VirtualFile> lastUsageFileRef = new ThreadLocal<>();
                ThreadLocal<Usage> recentUsageRef = new ThreadLocal<>();

/*        FindInProjectUtil.findUsages(findModel, myProject, processPresentation, filesToScanInitially, info -> {
          if(isCancelled()) {
            onStop(hash);
            return false;
          }
          final Usage usage = UsageInfo2UsageAdapter.CONVERTER.fun(info);
          usage.getPresentation().getIcon(); // cache icon

          VirtualFile file = lastUsageFileRef.get();
          VirtualFile usageFile = info.getVirtualFile();
          if (file == null || !file.equals(usageFile)) {
            resultsFilesCount.incrementAndGet();
            lastUsageFileRef.set(usageFile);
          }
          Usage recent = recentUsageRef.get();
          UsageInfo2UsageAdapter recentAdapter =
            recent instanceof UsageInfo2UsageAdapter ? (UsageInfo2UsageAdapter)recent : null;
          UsageInfo2UsageAdapter currentAdapter = usage instanceof UsageInfo2UsageAdapter ? (UsageInfo2UsageAdapter)usage : null;
          final boolean merged = !myHelper.isReplaceState() && currentAdapter != null && recentAdapter != null && recentAdapter.merge(currentAdapter);
          if (!merged) {
            recentUsageRef.set(usage);
          }


          ApplicationManager.getApplication().invokeLater(() -> {
            if (isCancelled()) {
              onStop(hash);
              return;
            }
            if (!merged) {
              model.addRow(new Object[]{usage});
            } else {
              model.fireTableRowsUpdated(model.getRowCount() - 1, model.getRowCount() - 1);
            }
            myCodePreviewComponent.setVisible(true);
            if (model.getRowCount() == 1 && myResultsPreviewTable.getModel() == model) {
              myResultsPreviewTable.setRowSelectionInterval(0, 0);
            }
            int occurrences = resultsCount.get();
            int filesWithOccurrences = resultsFilesCount.get();
            myCodePreviewComponent.setVisible(occurrences > 0);
            myReplaceAllButton.setEnabled(occurrences > 1);
            myReplaceSelectedButton.setEnabled(occurrences > 0);

            StringBuilder stringBuilder = new StringBuilder();
            if (occurrences > 0) {
              stringBuilder.append(Math.min(ShowUsagesAction.getUsagesPageSize(), occurrences));
              boolean foundAllUsages = occurrences < ShowUsagesAction.getUsagesPageSize();
              myUsagesCount = String.valueOf(occurrences);
              if (!foundAllUsages) {
                stringBuilder.append("+");
                myUsagesCount += "+";
              }
              stringBuilder.append(UIBundle.message("message.matches", occurrences));
              stringBuilder.append(" in ");
              stringBuilder.append(filesWithOccurrences);
              myFilesCount = String.valueOf(filesWithOccurrences);
              if (!foundAllUsages) {
                stringBuilder.append("+");
                myFilesCount += "+";
              }
              stringBuilder.append(UIBundle.message("message.files", filesWithOccurrences));
            }
            mySearchTextArea.setInfoText(stringBuilder.toString());
          }, state);

          boolean continueSearch = resultsCount.incrementAndGet() < ShowUsagesAction.getUsagesPageSize();
          if (!continueSearch) {
            onStop(hash);
          }
          return continueSearch;
        });*/

                return new Continuation(() -> {
                    if (!isCancelled()) {
                        if (resultsCount.get() == 0) {
                            showEmptyText(null);
                        }
                    }
                    onStop(hash);
                }, state);
            }

            boolean isCancelled() {
                return progressIndicatorWhenSearchStarted != myResultsPreviewSearchProgress || progressIndicatorWhenSearchStarted.isCanceled();
            }

            @Override
            public void onCanceled(@NotNull ProgressIndicator indicator) {
                if (isShowing() && progressIndicatorWhenSearchStarted == myResultsPreviewSearchProgress) {
                    scheduleResultsUpdate();
                }
            }
        });
    }

    private void showEmptyText(@Nullable String message) {
        StatusText emptyText = myResultsPreviewTable.getEmptyText();
        emptyText.clear();
        emptyText.setText(message != null ? UIBundle.message("message.nothingToShow.with.problem", message)
                : UIBundle.message("message.nothingToShow"));
    /*if (mySelectedScope == FindPopupScopeUIImpl.DIRECTORY && !myHelper.getModel().isWithSubdirectories()) {
      emptyText.appendSecondaryText(FindBundle.message("find.recursively.hint"),
                                                               SimpleTextAttributes.LINK_ATTRIBUTES,
                                    e -> {
                                      myHelper.getModel().setWithSubdirectories(true);
                                      scheduleResultsUpdate();
                                    });
    }*/
    }

    private void onStart(int hash) {
        myLoadingHash = hash;
        myLoadingDecorator.startLoading(false);
        myResultsPreviewTable.getEmptyText().setText("Searching...");
    }


    private void onStop(int hash) {
        onStop(hash, null);
    }

    private void onStop(int hash, String message) {
        if (hash != myLoadingHash) {
            return;
        }
        UIUtil.invokeLaterIfNeeded(() -> {
            showEmptyText(message);
            myLoadingDecorator.stopLoading();
        });
    }

    @Override
    @Nullable
    public String getFileTypeMask() {
        String mask = null;
        if (myCbFileFilter != null && myCbFileFilter.isSelected()) {
            mask = (String) myFileMaskField.getSelectedItem();
        }
        return mask;
    }

    @Nullable("null means OK")
    private ValidationInfo getValidationInfo(@NotNull FindModel model) {
//    ValidationInfo scopeValidationInfo = myScopeUI.validate(model, mySelectedScope);
//    if (scopeValidationInfo != null) {
//      return scopeValidationInfo;
//    }
//
//    if (!myHelper.canSearchThisString()) {
//      return new ValidationInfo(FindBundle.message("find.empty.search.text.error"), mySearchComponent);
//    }
//
//    if (myCbRegularExpressions != null && myCbRegularExpressions.isSelected() && myCbRegularExpressions.isEnabled()) {
//      String toFind = getStringToFind();
//      try {
//        boolean isCaseSensitive = myCbCaseSensitive != null && myCbCaseSensitive.isSelected() && myCbCaseSensitive.isEnabled();
//        Pattern pattern =
//          Pattern.compile(toFind, isCaseSensitive ? Pattern.MULTILINE : Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
//        if (pattern.matcher("").matches() && !toFind.endsWith("$") && !toFind.startsWith("^")) {
//          return new ValidationInfo(FindBundle.message("find.empty.match.regular.expression.error"), mySearchComponent);
//        }
//      }
//      catch (PatternSyntaxException e) {
//        return new ValidationInfo(FindBundle.message("find.invalid.regular.expression.error", toFind, e.getDescription()),
//                                  mySearchComponent);
//      }
//    }
//
//    final String mask = getFileTypeMask();
//
//    if (mask != null) {
//      if (mask.isEmpty()) {
//        return new ValidationInfo(FindBundle.message("find.filter.empty.file.mask.error"), myFileMaskField);
//      }
//
//      if (mask.contains(";")) {
//        return new ValidationInfo("File masks should be comma-separated", myFileMaskField);
//      }
//      else {
//        try {
//          FindInProjectUtil.createFileMaskRegExp(mask);   // verify that the regexp compiles
//        }
//        catch (PatternSyntaxException ex) {
//          return new ValidationInfo(FindBundle.message("find.filter.invalid.file.mask.error", mask), myFileMaskField);
//        }
//      }
//    }
        return null;
    }

    @Override
    @NotNull
    public String getStringToFind() {
        String text = mySearchComponent.getText();
        int len = text.length();
        return text;
    }

    @NotNull
    private String getStringToReplace() {
        return myReplaceComponent.getText();
    }

    private void applyTo(@NotNull FindModel model) {
        model.setCaseSensitive(myCbCaseSensitive.isSelected());

        if (model.isReplaceState()) {
            model.setPreserveCase(myCbPreserveCase.isSelected());
        }

        model.setWholeWordsOnly(myCbWholeWordsOnly.isSelected());

        String selectedSearchContextInUi = mySelectedContextName;
/*    FindModel.SearchContext searchContext = FindDialog.parseSearchContext(selectedSearchContextInUi);

    model.setSearchContext(searchContext);*/

        //model.setRegularExpressions(myCbRegularExpressions.isSelected());

        ///mariotodo
        String strToFind = getStringToFind();
        model.setStringToFind(strToFind);

        myCopyResultButton.setEnabled(!strToFind.isEmpty());

        WSProject pro = WSProjectListener.getInstance().getWSProject();
        FindTextRequest req = new FindTextRequest();

        req.m_reqTimeStamp = new Date().getTime();
        req.m_bConsiderFileName = myConsiderFileName.isSelected();
        req.m_nMaxResult = WSConfig.MAX_RESULT;
        req.setString(getStringToFind().toLowerCase());
        req.m_searchFiles = myCurrFileOnly.isSelected() ?  pro.getCurrFileCopy() : pro.getSolutionFileCopy();

        req.m_finishCallBack = (param) -> {
            ApplicationManager.getApplication().invokeLater(() -> {

                long currTimeStamp = new Date().getTime();
                WSFindTextArgs args = (WSFindTextArgs) param;
                FSLog.log.info(String.format("[%d]find req callback result num = %d,take time = %d(ms)", args.req.m_nTag,args.listResult.size(),currTimeStamp - req.m_reqTimeStamp));

                if (args.listResult.size() > 0) {
                    WSTableModel tableModel = (WSTableModel) myResultsPreviewTable.getModel();
                    Vector<Object> vec = new Vector<Object>(args.listResult);
                    tableModel.addRows(vec);
                    myResultsPreviewTable.setRowSelectionInterval(0, 0);
                }
                myCopyResultButton.setEnabled(args.listResult.size() > 0);
                String resultDesc = "";
                if (args.listResult.size() > 0) {
                    StringBuilder stringBuilder = new StringBuilder();
                    stringBuilder.append(args.listResult.size());
                    if (args.req.m_upToResultCntLimit) {
                        stringBuilder.append("+");
                    }
                    stringBuilder.append(" matches in " + args.req.m_nResultFileCnt);
                    if (args.req.m_upToResultCntLimit) {
                        stringBuilder.append("+");
                    }
                    stringBuilder.append(" files");
                    resultDesc = stringBuilder.toString();
                }

                mySearchTextArea.setInfoText(resultDesc);
                //tableModel.addRow(vec);
                //tableModel.fireTableRowsUpdated(tableModel.getRowCount() - 1, tableModel.getRowCount() - 1);
            });
            ApplicationManager.getApplication().invokeLater(() -> {
                updateFileLineNums();
            });
            return 0;
        };
        WSTextFinder.getInstance().start(req);


        model.setProjectScope(false);
        model.setDirectoryName(null);
        model.setModuleName(null);
        model.setCustomScopeName(null);
        model.setCustomScope(null);
        model.setCustomScope(false);
        //myScopeUI.applyTo(model, mySelectedScope);

        //model.setFindAll(false);

        String mask = getFileTypeMask();
        model.setFileFilter(mask);
    }
    private void updateFileLineNums() {
        WSProject pro = WSProjectListener.getInstance().getWSProject();
            myFileCountHintLabel.setText("Files : " + pro.getFileNums());
            myLineCountHintLabel.setText("Lines : " + pro.getLineNums());
    }
    // 点击跳转
    private void navigateToSelectedUsage(AnActionEvent e) {
        List<WSFindTextResult> usages = getSelectedResults();
        if (usages != null) {
            myBalloon.cancel();
            boolean first = true;
            for (WSFindTextResult usage : usages) {
                if (first) {
                    addJumpHistory(usage);
                    WSUtil.navigateToFile(usage, true);
                } else {
                    //usage.highlightInEditor();
                }
                first = false;
            }
        }
    }

    private List<WSFindTextResult> getSelectedResults() {
        List<WSFindTextResult> result = null;
        int[] rows = myResultsPreviewTable.getSelectedRows();
        for (int i = rows.length - 1; i >= 0; i--) {
            int row = rows[i];
            Object valueAt = myResultsPreviewTable.getModel().getValueAt(row, 0);
            if (result == null) result = new ArrayList<WSFindTextResult>();
            result.add((WSFindTextResult) valueAt);
        }
        return result;
    }

    public static ActionToolbarImpl createToolbar(AnAction... actions) {
        ActionToolbarImpl toolbar = (ActionToolbarImpl) ActionManager.getInstance()
                .createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, new DefaultActionGroup(actions), true);
        toolbar.setForceMinimumSize(true);
        toolbar.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY);
        return toolbar;
    }

    private static void applyFont(JBFont font, Component... components) {
        for (Component component : components) {
            component.setFont(font);
        }
    }

    private class MySwitchContextToggleAction extends ToggleAction implements DumbAware {
        MySwitchContextToggleAction(FindModel.SearchContext context) {
            super(FindDialog.getPresentableName(context));
        }

        @Override
        public boolean isSelected(AnActionEvent e) {
            return Comparing.equal(mySelectedContextName, getTemplatePresentation().getText());
        }

        @Override
        public void setSelected(AnActionEvent e, boolean state) {
            if (state) {
                mySelectedContextName = getTemplatePresentation().getText();
                scheduleResultsUpdate();
            }
        }
    }

    private class MySelectScopeToggleAction extends ToggleAction {
        private final FindPopupScopeUI.ScopeType myScope;

        MySelectScopeToggleAction(FindPopupScopeUI.ScopeType scope) {
            super(scope.text, null, scope.icon);
            getTemplatePresentation().setHoveredIcon(scope.icon);
            getTemplatePresentation().setDisabledIcon(scope.icon);
            myScope = scope;
        }

        @Override
        public boolean displayTextInToolbar() {
            return true;
        }

        @Override
        public boolean isSelected(AnActionEvent e) {
            return false;//mySelectedScope == myScope;
        }

        @Override
        public void setSelected(AnActionEvent e, boolean state) {
            if (state) {
                //mySelectedScope = myScope;
                myScopeSelectionToolbar.updateActionsImmediately();
                updateScopeDetailsPanel();
                scheduleResultsUpdate();
            }
        }
    }

    private class MyShowFilterPopupAction extends DumbAwareAction {
        private final DefaultActionGroup mySwitchContextGroup;

        MyShowFilterPopupAction() {
            super(FindBundle.message("find.popup.show.filter.popup"), null, AllIcons.General.Filter);
            LayeredIcon icon = JBUI.scale(new LayeredIcon(2));
            icon.setIcon(AllIcons.General.Filter, 0);
            icon.setIcon(AllIcons.General.Dropdown, 1, 3, 0);
            getTemplatePresentation().setIcon(icon);

            Shortcut SHORT_CUT = new KeyboardShortcut(KeyStroke.getKeyStroke(70, 640), (KeyStroke)null);
            setShortcutSet(new CustomShortcutSet(SHORT_CUT));
            mySwitchContextGroup = new DefaultActionGroup();
            mySwitchContextGroup.add(new MySwitchContextToggleAction(FindModel.SearchContext.ANY));
            mySwitchContextGroup.add(new MySwitchContextToggleAction(FindModel.SearchContext.IN_COMMENTS));
            mySwitchContextGroup.add(new MySwitchContextToggleAction(FindModel.SearchContext.IN_STRING_LITERALS));
            mySwitchContextGroup.add(new MySwitchContextToggleAction(FindModel.SearchContext.EXCEPT_COMMENTS));
            mySwitchContextGroup.add(new MySwitchContextToggleAction(FindModel.SearchContext.EXCEPT_STRING_LITERALS));
            mySwitchContextGroup.add(new MySwitchContextToggleAction(FindModel.SearchContext.EXCEPT_COMMENTS_AND_STRING_LITERALS));
            mySwitchContextGroup.setPopup(true);
        }

        @Override
        public void actionPerformed(AnActionEvent e) {
            if (PlatformDataKeys.CONTEXT_COMPONENT.getData(e.getDataContext()) == null) return;

            ListPopup listPopup =
                    JBPopupFactory.getInstance().createActionGroupPopup(null, mySwitchContextGroup, e.getDataContext(), false, null, 10);
            listPopup.showUnderneathOf(myFilterContextButton);
        }
    }

    private static class MyLookupElement extends LookupElement {
        private final String myValue;

        MyLookupElement(String value) {
            myValue = value;
        }

        @NotNull
        @Override
        public String getLookupString() {
            return myValue;
        }

        @Nullable
        @Override
        public PsiElement getPsiElement() {
            return null;
        }

        @Override
        public boolean isValid() {
            return true;
        }
    }
    @Override
    public void onFileCacheFinish()  {
        ApplicationManager.getApplication().invokeLater(() -> {
            if(myBalloon.isVisible()) {
                findSettingsChanged();
            }
        });
    }
    public void removeListenerToLastEditor() {
        try {
            if(m_lastCaretListener != null && m_lastCaretModel != null) {
                m_lastCaretModel.removeCaretListener(m_lastCaretListener);
            }
            m_lastCaretListener = null;
            m_lastCaretModel = null;
        } finally {

        }
    }
    public void addListenerToCurrEditor() {
        this.removeListenerToLastEditor();
        Editor editor = FileEditorManager.getInstance(WSProjectListener.getInstance().getJBProject()).getSelectedTextEditor();
        if(editor != null) {
            m_lastCaretListener = new CaretListener() {
                @Override
                public void caretPositionChanged(@NotNull final CaretEvent e) {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        if(myFollowCursor.isSelected()) {
                            String toSearch = WSUtil.getWordAtCaret(WSProjectListener.getInstance().getJBProject());
                            updateSearchByText(toSearch);

                        }
                    });
                }
            };
            m_lastCaretModel = editor.getCaretModel();
            m_lastCaretModel.addCaretListener(m_lastCaretListener);
        }
    };

    public void updateSearchByText(String toSearch) {
        ApplicationManager.getApplication().invokeLater(() -> {
            String oldText = mySearchComponent.getText();
            if(!toSearch.isEmpty() && !oldText.equals(toSearch)) {
                mySearchComponent.setText(toSearch);
                findSettingsChanged();
            }
        });
    }

    private void updateHistoryBtn() {
        ApplicationManager.getApplication().invokeLater(() -> {
            myHistoryPrev.setEnabled(g_historyIndex > 0);
            myHistoryNext.setEnabled(g_historyIndex + 1 < g_Historys.size());
        });
    }
    @Override
    public void onEditorChanged() {
        addListenerToCurrEditor();
    }

    private void addJumpHistory(WSFindTextResult result) {
        g_Historys.add(result);
        g_historyIndex = g_Historys.size() - 1;
        updateHistoryBtn();
    }

}
