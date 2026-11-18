package client;

import client.ChatModel.OnlineUser;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.geom.Arc2D;
import java.util.regex.Pattern;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.plaf.metal.MetalTabbedPaneUI;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.RenderingHints;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.DefaultListModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.BadLocationException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
import javax.swing.JTextField;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.ColorUIResource;
import javax.swing.ListSelectionModel;
import java.util.List;

public class ChatWindow extends JFrame {
    private static final int BUTTON_HEIGHT = 34;
    private static final int TAB_ACTION_SIZE = 28;
    private static final String CONNECT_LABEL = "Connect";
    private static final String JOIN_LABEL = "Join chat";
    final JTextField hostField = new JTextField("127.0.0.1");
    final JTextField portField = new JTextField("5487");
    final JTextField nameField = new JTextField();
    final JButton connectButton = new JButton(CONNECT_LABEL);
    final JButton viewUsersButton = new JButton("Observe only");
    final JButton hostButton = new JButton("Start temp server");
    final JButton themeButton = new JButton();
    final JTextPane chatArea = new JTextPane();
    final JTextField inputField = new JTextField() {
        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (getDocument().getLength() != 0) return;
            Graphics hint = graphics.create();
            hint.setColor(mutedColor());
            var metrics = hint.getFontMetrics(getFont());
            hint.drawString("Type a message...", getInsets().left,
                    (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent());
            hint.dispose();
        }
    };
    private final JButton imageButton = new JButton();
    private boolean imagesEnabled;
    private boolean imageBusy;
    final JButton sendButton = new JButton("Send");
    final JButton leaveButton = new JButton("Leave");
    private final JMenuItem privateMenuItem = new JMenuItem("Open private chat");
    final JButton createGroupButton = new JButton("+");
    final JButton addMembersButton = new JButton("Add members");
    final JButton groupMembersButton = new JButton("Group members");
    final JButton leaveGroupButton = new JButton("Leave group");
    final DefaultListModel<OnlineUser> onlineUsers = new DefaultListModel<>();
    final JList<OnlineUser> usersList = new JList<>(onlineUsers);
    final JTabbedPane chats = new JTabbedPane();
    final Map<String, PrivateChat> privateChats = new LinkedHashMap<>();
    final Map<String, JScrollPane> groupChats = new LinkedHashMap<>();
    final Map<Component, String> tabNames = new LinkedHashMap<>();
    final Map<Component, Integer> unreadCounts = new LinkedHashMap<>();
    final Map<Component, JLabel> tabLabels = new LinkedHashMap<>();
    private static final Pattern TIMED_MESSAGE = Pattern.compile("^\\[(\\d{2}:\\d{2}:\\d{2})] (.*)$", Pattern.DOTALL);
    private final JButton settingsButton = new JButton("Connection settings");
    private final JLabel statusLabel = new JLabel();
    private final JLabel rosterTitle = new JLabel();
    private final JPanel settingsPanel = new JPanel(new BorderLayout(10, 8));
    private final JPanel groupActions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
    private final JPanel rosterPanel = new JPanel(new BorderLayout(0, 10));
    private final JPopupMenu userMenu = new JPopupMenu();
    private static final Object TRANSCRIPT_ROLE = new Object();
    private int displayedLobbyLength;
    private boolean darkMode;
    private final Map<String, Object> lightThemeDefaults = new LinkedHashMap<>();

    private static final String[] THEME_KEYS = {
            "control", "info", "text", "nimbusBase", "nimbusBlueGrey", "nimbusLightBackground",
            "Panel.background", "Label.foreground", "Button.background", "Button.foreground",
            "Button.select", "Button.disabledText", "Button.focus", "Button.gradient", "TextField.background",
            "TextField.foreground", "TextField.inactiveBackground", "TextField.inactiveForeground",
            "TextField.caretForeground", "TextField.selectionBackground", "TextField.selectionForeground",
            "TextArea.background", "TextPane.background", "TextArea.foreground", "TextPane.foreground",
            "TextArea.caretForeground", "TextPane.caretForeground",
            "TextArea.selectionBackground", "TextPane.selectionBackground", "TextArea.selectionForeground",
            "TextPane.selectionForeground", "List.background", "List.foreground",
            "List.selectionBackground", "List.selectionForeground", "TabbedPane.background", "TabbedPane.foreground",
            "TabbedPane.selected", "TabbedPane.contentAreaColor", "TabbedPane.unselectedBackground",
            "TabbedPane.focus", "TabbedPane.light", "TabbedPane.highlight", "TabbedPane.shadow",
            "TabbedPane.darkShadow", "ScrollPane.background", "Viewport.background", "OptionPane.background",
            "OptionPane.messageForeground", "ToolTip.background", "ToolTip.foreground"
    };

    static final class PrivateChat {
        final String name;
        final JTextPane area;
        final JScrollPane pane;

        PrivateChat(String name, JTextPane area, JScrollPane pane) {
            this.name = name;
            this.area = area;
            this.pane = pane;
        }
    }

    // 連線狀態和介面元件只在 Swing 介面執行緒中讀寫。
    private final ChatModel model = new ChatModel();
    private final ChatController controller = new ChatController(this, model);

    public ChatWindow() {
        super("Chat Room");
        setSize(900, 560);
        setMinimumSize(new Dimension(760, 420));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        for (String key : THEME_KEYS)
            lightThemeDefaults.put(key, UIManager.get(key));

        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        setContentPane(root);
        root.add(buildConnectionBar(), BorderLayout.NORTH);
        root.add(buildRoster(), BorderLayout.WEST);
        root.add(buildConversation(), BorderLayout.CENTER);

        applyTheme();
        setConnected(false);

        connectButton.addActionListener(event -> controller.connectAction());
        viewUsersButton.addActionListener(event -> controller.viewUsersAction());
        hostButton.addActionListener(event -> controller.hostAction());
        sendButton.addActionListener(event -> controller.sendMessage());
        imageButton.addActionListener(event -> controller.sendPicture());
        chats.addChangeListener(event -> {
            clearUnread(chats.getSelectedComponent());
            refreshImageButton();
            refreshGroupActions();
        });
        themeButton.addActionListener(event -> toggleTheme());
        privateMenuItem.addActionListener(event -> controller.openSelectedChat());
        createGroupButton.addActionListener(event -> controller.createGroup());
        addMembersButton.addActionListener(event -> controller.addGroupMembers());
        groupMembersButton.addActionListener(event -> controller.showGroupMembers());
        leaveGroupButton.addActionListener(event -> controller.leaveSelectedGroup());
        leaveButton.addActionListener(event -> controller.leaveChat());
        inputField.addActionListener(event -> controller.sendMessage());
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                controller.closeConnection();
                controller.stopTemporaryServer();
            }
        });
    }

    private JPanel buildConnectionBar() {
        JPanel bar = new JPanel(new BorderLayout(0, 10));
        JPanel status = new JPanel(new BorderLayout(10, 0));
        JPanel controls = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
        controls.add(settingsButton);
        controls.add(leaveButton);
        controls.add(themeButton);
        status.add(statusLabel, BorderLayout.CENTER);
        status.add(controls, BorderLayout.EAST);
        bar.add(status, BorderLayout.NORTH);

        JPanel fields = new JPanel(new GridBagLayout());
        GridBagConstraints cell = new GridBagConstraints();
        cell.fill = GridBagConstraints.HORIZONTAL;
        cell.insets = new Insets(2, 4, 2, 4);
        hostField.setColumns(12);
        portField.setColumns(5);
        nameField.setColumns(10);
        JTextField[] inputs = {hostField, portField, nameField};
        String[] labels = {"Server IP", "Port", "Name"};
        for (int index = 0; index < inputs.length; index++) {
            cell.gridx = index;
            cell.weightx = index == 1 ? 0 : 1;
            cell.gridy = 0;
            JLabel label = new JLabel(labels[index]);
            label.setLabelFor(inputs[index]);
            fields.add(label, cell);
            cell.gridy = 1;
            fields.add(inputs[index], cell);
        }
        JPanel connectionActions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
        connectionActions.add(connectButton);
        connectionActions.add(viewUsersButton);
        connectionActions.add(hostButton);
        settingsPanel.setBorder(new ControlBorder(new Insets(8, 8, 8, 8)));
        settingsPanel.add(fields, BorderLayout.CENTER);
        settingsPanel.add(connectionActions, BorderLayout.SOUTH);
        settingsPanel.setVisible(false);
        settingsButton.addActionListener(event -> {
            settingsPanel.setVisible(!settingsPanel.isVisible());
            if (settingsPanel.isVisible() && hostField.isEnabled()) hostField.requestFocusInWindow();
        });
        bar.add(settingsPanel, BorderLayout.CENTER);
        return bar;
    }

    private JPanel buildRoster() {
        rosterPanel.setPreferredSize(new Dimension(174, 0));
        rosterPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        rosterTitle.setFont(rosterTitle.getFont().deriveFont(java.awt.Font.BOLD));
        rosterPanel.add(rosterTitle, BorderLayout.NORTH);
        JLabel hint = new JLabel("Double-click to message");
        hint.setFont(hint.getFont().deriveFont(11f));
        rosterPanel.add(hint, BorderLayout.SOUTH);
        JScrollPane scroll = new JScrollPane(usersList);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        rosterPanel.add(scroll, BorderLayout.CENTER);
        userMenu.add(privateMenuItem);
        usersList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        usersList.setFixedCellHeight(32);
        usersList.addMouseListener(new MouseAdapter() {
            private void showMenu(MouseEvent event) {
                if (!event.isPopupTrigger()) return;
                int index = userAt(event);
                if (index < 0) return;
                usersList.setSelectedIndex(index);
                userMenu.show(usersList, event.getX(), event.getY());
            }

            private int userAt(MouseEvent event) {
                int index = usersList.locationToIndex(event.getPoint());
                return index >= 0 && usersList.getCellBounds(index, index).contains(event.getPoint()) ? index : -1;
            }

            @Override
            public void mousePressed(MouseEvent event) { showMenu(event); }

            @Override
            public void mouseReleased(MouseEvent event) { showMenu(event); }

            @Override
            public void mouseClicked(MouseEvent event) {
                if (!SwingUtilities.isLeftMouseButton(event) || event.getClickCount() != 2
                        || !privateMenuItem.isEnabled()) return;
                int index = userAt(event);
                if (index >= 0) controller.openPrivateChat(onlineUsers.get(index));
            }
        });
        usersList.getInputMap().put(javax.swing.KeyStroke.getKeyStroke("ENTER"), "open-private-chat");
        usersList.getActionMap().put("open-private-chat", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                if (privateMenuItem.isEnabled()) controller.openSelectedChat();
            }
        });
        usersList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                if (!selected) setBackground(rosterPanel.getBackground());
                if (!focus) setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
                if (value instanceof OnlineUser user) {
                    boolean self = !model.observing && !model.joining && user.id.equals(model.activeId);
                    setText("\u25CF  " + user.name + (self ? " (You)" : ""));
                }
                return this;
            }
        });
        return rosterPanel;
    }

    private JPanel buildConversation() {
        configureTranscript(chatArea);
        chats.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        chats.addTab("Lobby", createContentScroll(chatArea));
        describeButton(createGroupButton, "Create group");
        describeButton(imageButton, "Send image");
        inputField.getAccessibleContext().setAccessibleName("Type a message");

        // The tab UI reserves a slot for the create button, including when tabs scroll.
        JLayeredPane tabRegion = new JLayeredPane() {
            @Override
            public void doLayout() {
                chats.setBounds(0, 0, getWidth(), getHeight());
                createGroupButton.setBounds(getWidth() - TAB_ACTION_SIZE, 0, TAB_ACTION_SIZE, TAB_ACTION_SIZE);
            }
        };
        tabRegion.add(chats, JLayeredPane.DEFAULT_LAYER);
        tabRegion.add(createGroupButton, JLayeredPane.PALETTE_LAYER);
        groupActions.add(addMembersButton);
        groupActions.add(groupMembersButton);
        groupActions.add(leaveGroupButton);
        groupActions.setVisible(false);

        JPanel composer = new JPanel(new BorderLayout(8, 0));
        composer.add(imageButton, BorderLayout.WEST);
        composer.add(inputField, BorderLayout.CENTER);
        composer.add(sendButton, BorderLayout.EAST);
        JPanel conversation = new JPanel(new BorderLayout(0, 10));
        conversation.add(groupActions, BorderLayout.NORTH);
        conversation.add(tabRegion, BorderLayout.CENTER);
        conversation.add(composer, BorderLayout.SOUTH);
        return conversation;
    }

    private void refreshGroupActions() {
        groupActions.setVisible(selectedGroupId() != null);
    }

    private static void describeButton(JButton button, String description) {
        button.setToolTipText(description);
        button.getAccessibleContext().setAccessibleName(description);
    }

    // 只變更介面顏色，不改變聊天室資料或連線狀態。
    private void toggleTheme() {
        darkMode = !darkMode;
        applyTheme();
    }

    private void applyTheme() {
        if (darkMode) {
            // 參考 Facebook 深色介面的灰黑層次與藍色強調色。
            ColorUIResource background = new ColorUIResource(Color.BLACK);
            ColorUIResource surface = new ColorUIResource(Color.BLACK);
            ColorUIResource button = new ColorUIResource(58, 59, 60);
            ColorUIResource input = new ColorUIResource(Color.BLACK);
            ColorUIResource text = new ColorUIResource(228, 230, 235);
            ColorUIResource disabled = new ColorUIResource(176, 179, 184);
            ColorUIResource selected = new ColorUIResource(29, 84, 142);
            ColorUIResource focus = new ColorUIResource(45, 136, 255);
            // Metal 預設的淺色漸層會讓白字按鈕看不清楚。
            UIManager.put("Button.gradient", java.util.List.of(0f, 0f, button, button, button));
            for (String key : new String[] { "control", "Panel.background", "OptionPane.background",
                    "ScrollPane.background",
                    "Viewport.background", "TabbedPane.background", "TabbedPane.contentAreaColor", "nimbusBlueGrey" }) {
                UIManager.put(key, background);
            }
            UIManager.put("Button.background", button);
            for (String key : new String[] { "TabbedPane.selected", "TabbedPane.unselectedBackground",
                    "info", "ToolTip.background", "nimbusBase" })
                UIManager.put(key, surface);
            for (String key : new String[] { "text", "TextField.background", "TextField.inactiveBackground",
                    "TextArea.background", "TextPane.background", "List.background", "nimbusLightBackground" })
                UIManager.put(key, input);
            for (String key : new String[] { "Label.foreground", "Button.foreground", "TextField.foreground",
                    "TextField.caretForeground", "TextArea.foreground", "TextPane.foreground",
                    "TextArea.caretForeground", "TextPane.caretForeground", "List.foreground",
                    "TabbedPane.foreground", "OptionPane.messageForeground", "ToolTip.foreground",
                    "TextField.selectionForeground", "TextArea.selectionForeground", "TextPane.selectionForeground",
                    "List.selectionForeground" }) {
                UIManager.put(key, text);
            }
            for (String key : new String[] { "Button.disabledText", "TextField.inactiveForeground" })
                UIManager.put(key, disabled);
            for (String key : new String[] { "TextField.selectionBackground", "TextArea.selectionBackground",
                    "TextPane.selectionBackground",
                    "List.selectionBackground", "Button.select" }) {
                UIManager.put(key, selected);
            }
            UIManager.put("Button.focus", focus);
            UIManager.put("TabbedPane.focus", focus);
            for (String key : new String[] { "TabbedPane.light", "TabbedPane.highlight", "TabbedPane.shadow",
                    "TabbedPane.darkShadow" }) {
                UIManager.put(key, surface);
            }
        } else {
            lightThemeDefaults.forEach(UIManager::put);
            ColorUIResource white = new ColorUIResource(Color.WHITE);
            for (String key : new String[] { "control", "Panel.background", "OptionPane.background",
                    "ScrollPane.background", "Viewport.background", "TabbedPane.background",
                    "TabbedPane.contentAreaColor", "TabbedPane.selected", "TabbedPane.unselectedBackground",
                    "TextField.background", "TextField.inactiveBackground", "TextArea.background",
                    "TextPane.background", "List.background" }) {
                UIManager.put(key, white);
            }
        }
        UIManager.put("TabbedPane.tabInsets", new javax.swing.plaf.InsetsUIResource(4, 8, 4, 8));
        SwingUtilities.updateComponentTreeUI(this);
        // 已隱藏的分頁不在視窗元件樹內，也必須一起更新。
        for (PrivateChat chat : privateChats.values())
            SwingUtilities.updateComponentTreeUI(chat.pane);
        for (JScrollPane pane : groupChats.values())
            SwingUtilities.updateComponentTreeUI(pane);
        applyControlStyle();
        SwingUtilities.updateComponentTreeUI(userMenu);
        chats.setUI(new MetalTabbedPaneUI() {
            @Override
            protected Insets getTabAreaInsets(int placement) {
                Insets original = super.getTabAreaInsets(placement);
                return new Insets(original.top, original.left, original.bottom, original.right + TAB_ACTION_SIZE + 6);
            }

            @Override
            protected Insets getTabInsets(int placement, int index) {
                Insets original = super.getTabInsets(placement, index);
                if (chats.getTabComponentAt(index) == null) return original;
                return new Insets(original.top, original.left, original.bottom, 4);
            }

            @Override
            protected void paintContentBorder(Graphics graphics, int placement, int index) {
                // The transcript's scroll pane supplies the single visible border.
            }
        });
        themeButton.setIcon(new WindowIcon(false));
        imageButton.setIcon(new WindowIcon(true));
        describeButton(themeButton, darkMode ? "Switch to light mode" : "Switch to dark mode");
        rosterPanel.setBackground(darkMode ? new Color(0x181B22) : new Color(0xF5F7FA));
        usersList.setBackground(rosterPanel.getBackground());
        updateTranscriptColors(chatArea);
        for (PrivateChat chat : privateChats.values()) updateTranscriptColors(chat.area);
        for (JScrollPane pane : groupChats.values())
            updateTranscriptColors((JTextPane) pane.getViewport().getView());
        refreshStatus();
        revalidate();
        repaint();
    }

    private void applyControlStyle() {
        styleMainButton(connectButton, true);
        styleMainButton(sendButton, true);
        for (JButton button : List.of(viewUsersButton, hostButton, themeButton, imageButton, leaveButton,
                settingsButton, createGroupButton, addMembersButton, groupMembersButton, leaveGroupButton)) {
            styleMainButton(button, false);
        }
        for (JButton button : List.of(themeButton, imageButton))
            button.setPreferredSize(new Dimension(BUTTON_HEIGHT, BUTTON_HEIGHT));
        createGroupButton.setPreferredSize(new Dimension(TAB_ACTION_SIZE, TAB_ACTION_SIZE));
        // 分頁關閉按鈕維持原本的點擊範圍。
        for (int index = 0; index < chats.getTabCount(); index++) {
            if (chats.getTabComponentAt(index) instanceof JPanel header) {
                for (Component component : header.getComponents()) {
                    if (component instanceof JButton button) {
                        styleButton(button, false);
                    }
                }
            }
        }
        for (JTextField field : List.of(hostField, portField, nameField, inputField)) {
            field.setBorder(new ControlBorder(new Insets(4, 7, 4, 7)));
        }
    }

    private JScrollPane createContentScroll(Component content) {
        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(new ControlBorder(new Insets(4, 4, 4, 4)));
        return scroll;
    }

    private final class ControlBorder extends AbstractBorder {
        private final Insets padding;

        ControlBorder(Insets padding) {
            this.padding = padding;
        }

        @Override
        public Insets getBorderInsets(Component component, Insets insets) {
            insets.set(padding.top, padding.left, padding.bottom, padding.right);
            return insets;
        }

        @Override
        public void paintBorder(Component component, Graphics graphics, int x, int y, int width, int height) {
            boolean focused = component.hasFocus();
            Graphics2D outline = (Graphics2D) graphics.create();
            outline.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            outline.setColor(focused ? focusColor() : darkMode ? new Color(0x626B78) : new Color(0x8793A4));
            outline.drawRoundRect(x, y, width - 1, height - 1, 8, 8);
            if (focused) {
                outline.drawRoundRect(x + 1, y + 1, width - 3, height - 3, 7, 7);
            }
            outline.dispose();
        }
    }

    private Color focusColor() {
        return darkMode ? new Color(0x8ABBFF) : new Color(0x1446B3);
    }

    private void styleMainButton(JButton button, boolean primary) {
        styleButton(button, primary);
        button.setPreferredSize(null);
        int width = button.getPreferredSize().width;
        if (button == connectButton) {
            var metrics = button.getFontMetrics(button.getFont());
            width += Math.max(metrics.stringWidth(CONNECT_LABEL), metrics.stringWidth(JOIN_LABEL))
                    - metrics.stringWidth(button.getText());
        } else if (button == hostButton) {
            var metrics = button.getFontMetrics(button.getFont());
            width += Math.max(metrics.stringWidth("Start temp server"), metrics.stringWidth("Stop temp server"))
                    - metrics.stringWidth(button.getText());
        } else if (button == viewUsersButton) {
            var metrics = button.getFontMetrics(button.getFont());
            width += Math.max(metrics.stringWidth("Observe only"), metrics.stringWidth("Disconnect"))
                    - metrics.stringWidth(button.getText());
        }
        button.setPreferredSize(new Dimension(width + 8, BUTTON_HEIGHT));
    }

    private void styleButton(JButton button, boolean primary) {
        boolean danger = button == leaveButton || button == leaveGroupButton;
        button.setUI(new BasicButtonUI() {
            @Override
            public void paint(Graphics graphics, javax.swing.JComponent component) {
                var state = button.getModel();
                Color fill = darkMode ? Color.BLACK : Color.WHITE;
                if (state.isEnabled()) {
                    if (primary) {
                        fill = darkMode ? new Color(0x245AB8) : new Color(0x2355B9);
                    }
                    if (state.isArmed() && state.isPressed()) {
                        fill = primary ? fill.darker() : darkMode ? new Color(0x303238) : new Color(0xD3DEED);
                    } else if (state.isRollover()) {
                        fill = primary ? fill.brighter() : darkMode ? new Color(0x24272D) : new Color(0xE0E8F4);
                    }
                }
                if (!state.isEnabled()) {
                    fill = darkMode ? new Color(0x24262B) : new Color(0xEFEFEF);
                } else if (danger && state.isRollover()) {
                    fill = darkMode ? new Color(0x412427) : new Color(0xFFE8E8);
                }
                Graphics2D shape = (Graphics2D) graphics.create();
                shape.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                shape.setColor(fill);
                shape.fillRoundRect(0, 0, component.getWidth(), component.getHeight(), 8, 8);
                shape.setColor(danger && state.isEnabled() ? dangerColor()
                        : darkMode ? new Color(0x737D8D) : new Color(0x8793A4));
                shape.drawRoundRect(0, 0, component.getWidth() - 1, component.getHeight() - 1, 8, 8);
                if (button.hasFocus()) {
                    shape.setColor(primary && state.isEnabled() ? Color.WHITE : focusColor());
                    shape.drawRoundRect(2, 2, component.getWidth() - 5, component.getHeight() - 5, 5, 5);
                }
                shape.dispose();
                super.paint(graphics, component);
            }

            @Override
            protected void paintText(Graphics graphics, javax.swing.AbstractButton control,
                    java.awt.Rectangle bounds, String text) {
                // BasicButtonUI 會使用元件的前景色；主按鈕使用白字以維持對比。
                if (primary || danger || !control.isEnabled()) {
                    graphics.setColor(!control.isEnabled() ? mutedColor() : danger ? dangerColor() : Color.WHITE);
                    javax.swing.plaf.basic.BasicGraphicsUtils.drawStringUnderlineCharAt(graphics, text,
                            control.getDisplayedMnemonicIndex(), bounds.x,
                            bounds.y + graphics.getFontMetrics().getAscent());
                } else {
                    super.paintText(graphics, control, bounds, text);
                }
            }
        });
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.setBorderPainted(false);
        button.setRolloverEnabled(true);
    }

    void showMessage(String message) {
        JOptionPane.showMessageDialog(this, message);
    }

    void showMessage(String message, String title) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.INFORMATION_MESSAGE);
    }

    String askGroupName() {
        return JOptionPane.showInputDialog(this, "Group name:");
    }

    String host() {
        return hostField.getText().strip();
    }

    String port() {
        return portField.getText().strip();
    }

    String name() {
        return nameField.getText().strip();
    }

    String message() {
        return inputField.getText();
    }

    void clearInput() {
        inputField.setText("");
    }

    void useLocalHost() {
        hostField.setText("127.0.0.1");
    }

    void focusHost() {
        hostField.requestFocusInWindow();
    }

    void focusPort() {
        portField.requestFocusInWindow();
    }

    void focusName() {
        nameField.requestFocusInWindow();
    }

    List<OnlineUser> selectedUsers() {
        return usersList.getSelectedValuesList();
    }

    boolean hasPrivateChat(String id) {
        return privateChats.containsKey(id);
    }

    String selectedGroupId() {
        return groupChats.entrySet().stream()
                .filter(entry -> entry.getValue() == chats.getSelectedComponent())
                .map(Map.Entry::getKey).findFirst().orElse(null);
    }

    String selectedPrivateId() {
        return privateChats.entrySet().stream()
                .filter(entry -> entry.getValue().pane == chats.getSelectedComponent())
                .map(Map.Entry::getKey).findFirst().orElse(null);
    }

    boolean isLobbySelected() {
        return chats.getSelectedIndex() == 0;
    }

    boolean hasGroup(String id) {
        return groupChats.containsKey(id);
    }

    void selectPrivateChat(OnlineUser user) {
        PrivateChat chat = privateChat(user.id, user.name);
        chats.setSelectedComponent(chat.pane);
        inputField.requestFocusInWindow();
    }

    private JScrollPane groupChat(String id, String name) {
        JScrollPane pane = groupChats.get(id);
        if (pane == null) {
            JTextPane area = new JTextPane();
            configureTranscript(area);
            pane = createContentScroll(area);
            groupChats.put(id, pane);
            addChatTab("Group: " + name, pane);
        } else if (chats.indexOfComponent(pane) < 0) {
            addChatTab(tabNames.get(pane), pane);
        }
        return pane;
    }

    void showGroupMessage(String id, String name, String text) {
        JScrollPane pane = groupChat(id, name);
        appendText((JTextPane) pane.getViewport().getView(), text);
        markUnread(pane);
    }

    void showPrivateMessage(String id, String peer, String text) {
        PrivateChat chat = privateChats.get(id);
        if (chat != null)
            chat = privateChat(id, chat.name);
        else if (!peer.isEmpty())
            chat = privateChat(id, peer);
        if (chat == null) {
            appendMessage(text);
            return;
        }
        appendText(chat.area, text);
        markUnread(chat.pane);
    }

    private Color mutedColor() {
        return darkMode ? new Color(0xA9B2C3) : new Color(0x566273);
    }

    private Color dangerColor() {
        return darkMode ? new Color(0xFFA3A3) : new Color(0xA32832);
    }

    private final class WindowIcon implements Icon {
        private final boolean attachment;

        WindowIcon(boolean attachment) { this.attachment = attachment; }

        @Override public int getIconWidth() { return 18; }
        @Override public int getIconHeight() { return 18; }

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D drawing = (Graphics2D) graphics.create();
            drawing.translate(x, y);
            drawing.setColor(component.isEnabled() ? component.getForeground() : mutedColor());
            drawing.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            drawing.setStroke(new java.awt.BasicStroke(1.5f));
            if (attachment) {
                drawing.drawRoundRect(1, 2, 15, 13, 3, 3);
                drawing.drawOval(4, 5, 2, 2);
                drawing.drawPolyline(new int[]{3, 8, 11, 14}, new int[]{12, 8, 12, 9}, 4);
            } else if (darkMode) {
                drawing.drawOval(5, 5, 8, 8);
                for (int angle = 0; angle < 360; angle += 45) {
                    double radians = Math.toRadians(angle);
                    drawing.drawLine(9 + (int) Math.round(6 * Math.cos(radians)),
                            9 + (int) Math.round(6 * Math.sin(radians)),
                            9 + (int) Math.round(8 * Math.cos(radians)),
                            9 + (int) Math.round(8 * Math.sin(radians)));
                }
            } else {
                drawing.draw(new Arc2D.Double(2, 2, 14, 14, 75, 265, Arc2D.OPEN));
                drawing.draw(new Arc2D.Double(6, 0, 12, 13, 105, 155, Arc2D.OPEN));
            }
            drawing.dispose();
        }
    }

    private void configureTranscript(JTextPane pane) {
        pane.setEditable(false);
        pane.setMargin(new Insets(8, 10, 8, 10));

    }

    private void updateTranscriptColors(JTextPane pane) {
        var document = pane.getStyledDocument();
        for (int offset = 0; offset < document.getLength();) {
            var run = document.getCharacterElement(offset);
            int end = Math.min(document.getLength(), run.getEndOffset());
            if (run.getAttributes().getAttribute(TRANSCRIPT_ROLE) instanceof String role) {
                document.setCharacterAttributes(offset, end - offset, textRole(role), false);
            }
            offset = end;
        }
    }

    private SimpleAttributeSet textRole(String role) {
        var attributes = new SimpleAttributeSet();
        attributes.addAttribute(TRANSCRIPT_ROLE, role);
        if (role.equals("body")) {
            StyleConstants.setForeground(attributes, darkMode ? new Color(0xE4E6EB) : new Color(0x20242C));
        } else if (role.equals("sender")) {
            StyleConstants.setBold(attributes, true);
            StyleConstants.setForeground(attributes, darkMode ? new Color(0xA5C6FF) : new Color(0x244D9E));
        } else {
            StyleConstants.setFontSize(attributes, 11);
            StyleConstants.setForeground(attributes, mutedColor());
        }
        return attributes;
    }

    private void appendText(JTextPane pane, String text) {
        appendText(pane, text, true);
    }

    private void appendText(JTextPane pane, String text, boolean allowNotice) {
        var document = pane.getStyledDocument();
        int start = document.getLength();
        var timed = TIMED_MESSAGE.matcher(text);
        boolean timestampPresent = timed.matches();
        String content = timestampPresent ? timed.group(2) : text;
        int separator = timestampPresent ? content.indexOf(": ") : -1;
        boolean notice = allowNotice && separator < 0 && !text.isEmpty();
        var paragraph = new SimpleAttributeSet();
        StyleConstants.setAlignment(paragraph, notice ? StyleConstants.ALIGN_CENTER : StyleConstants.ALIGN_LEFT);
        StyleConstants.setSpaceAbove(paragraph, notice ? 3 : 5);
        StyleConstants.setSpaceBelow(paragraph, notice ? 3 : 5);
        try {
            if (separator >= 0) {
                document.insertString(document.getLength(), content.substring(0, separator),
                        textRole("sender"));
                document.insertString(document.getLength(), "  " + timed.group(1),
                        textRole("timestamp"));
                document.insertString(document.getLength(), "\n", null);
                var heading = new SimpleAttributeSet(paragraph);
                StyleConstants.setSpaceBelow(heading, 0);
                document.setParagraphAttributes(start, document.getLength() - start - 1, heading, true);
                start = document.getLength();
                StyleConstants.setSpaceAbove(paragraph, 0);
                document.insertString(start, content.substring(separator + 2) + "\n", textRole("body"));
            } else {
                String message = timestampPresent ? timed.group(1) + "  " + content : content;
                document.insertString(start, message + "\n", textRole(notice ? "notice" : "body"));
            }
            document.setParagraphAttributes(start, document.getLength() - start - 1, paragraph, true);
            pane.setCaretPosition(document.getLength());
        } catch (BadLocationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    void enableImages(boolean enabled) {
        imagesEnabled = enabled;
        refreshImageButton();
    }

    void setImageBusy(boolean busy) {
        imageBusy = busy;
        refreshImageButton();
    }

    private void refreshImageButton() {
        imageButton.setEnabled(imagesEnabled && !imageBusy && sendButton.isEnabled()
                && (selectedPrivateId() != null || selectedGroupId() != null));
    }

    Path choosePicture() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("PNG and JPEG images", "png", "jpg", "jpeg"));
        return chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION
                ? chooser.getSelectedFile().toPath()
                : null;
    }

    void showPicture(ChatImages.Picture picture) {
        JScrollPane scroll = picture.group() ? groupChat(picture.conversation(), picture.title())
                : privateChat(picture.conversation(), picture.title()).pane;
        JTextPane pane = (JTextPane) scroll.getViewport().getView();
        appendText(pane, picture.sender() + ": " + picture.filename(), false);
        JButton thumbnail = new JButton(new ImageIcon(picture.thumbnail()));
        thumbnail.setToolTipText(picture.filename());
        thumbnail.getAccessibleContext().setAccessibleName("Preview " + picture.filename());
        thumbnail.addActionListener(event -> previewPicture(picture));
        pane.setCaretPosition(pane.getDocument().getLength());
        pane.insertComponent(thumbnail);
        appendText(pane, "");
        markUnread(scroll);
    }

    private void previewPicture(ChatImages.Picture picture) {
        Thread.ofVirtual().start(() -> {
            try {
                var image = ChatImages.scaled(common.ImageFiles.decode(picture.bytes()), 960);
                SwingUtilities.invokeLater(() -> {
                    if (!isDisplayable())
                        return;
                    Object[] choices = { "Save as…", "Close" };
                    JLabel preview = new JLabel(new ImageIcon(image));
                    JScrollPane scroll = new JScrollPane(preview);
                    scroll.setPreferredSize(new java.awt.Dimension(Math.min(720, image.getWidth() + 20),
                            Math.min(520, image.getHeight() + 20)));
                    int choice = JOptionPane.showOptionDialog(this, scroll, picture.filename(),
                            JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, choices, choices[1]);
                    if (choice == 0)
                        savePicture(picture);
                });
            } catch (IOException exception) {
                SwingUtilities.invokeLater(() -> showMessage(exception.getMessage()));
            }
        });
    }

    private void savePicture(ChatImages.Picture picture) {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File(picture.filename()));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION)
            return;
        Path destination = chooser.getSelectedFile().toPath();
        Thread.ofVirtual().start(() -> {
            try {
                Files.write(destination, picture.bytes(), java.nio.file.StandardOpenOption.CREATE_NEW,
                        java.nio.file.StandardOpenOption.WRITE);
            } catch (IOException exception) {
                SwingUtilities.invokeLater(() -> showMessage("Could not save image: " + exception.getMessage()));
            }
        });
    }

    void showOnlineUsers(List<OnlineUser> users) {
        List<OnlineUser> selected = usersList.getSelectedValuesList();
        onlineUsers.clear();
        for (OnlineUser user : users)
            onlineUsers.addElement(user);
        int[] indexes = selected.stream().mapToInt(onlineUsers::indexOf).filter(index -> index >= 0).toArray();
        usersList.setSelectedIndices(indexes);
        for (Map.Entry<String, PrivateChat> entry : privateChats.entrySet()) {
            boolean online = users.stream().anyMatch(user -> user.id.equals(entry.getKey()));
            JScrollPane pane = entry.getValue().pane;
            tabNames.put(pane, entry.getValue().name + (online ? "" : " (offline)"));
            refreshTabTitle(pane);
        }
        rosterTitle.setText("Online members (" + onlineUsers.size() + ")");
    }

    PrivateChat privateChat(String peerId, String peerName) {
        PrivateChat chat = privateChats.computeIfAbsent(peerId, id -> {
            JTextPane area = new JTextPane();
            configureTranscript(area);
            JScrollPane pane = createContentScroll(area);
            addChatTab(peerName, pane);
            return new PrivateChat(peerName, area, pane);
        });
        if (chats.indexOfComponent(chat.pane) < 0) {
            addChatTab(tabNames.get(chat.pane), chat.pane);
        }
        return chat;
    }

    void clearPrivateChats() {
        while (chats.getTabCount() > 1)
            chats.removeTabAt(1);
        privateChats.clear();
        groupChats.clear();
        tabNames.clear();
        unreadCounts.clear();
        tabLabels.clear();
    }

    void removeGroupChat(String id) {
        JScrollPane pane = groupChats.remove(id);
        if (pane != null) {
            tabNames.remove(pane);
            unreadCounts.remove(pane);
            tabLabels.remove(pane);
            chats.remove(pane);
        }
    }

    void clearGroupChats() {
        for (JScrollPane pane : groupChats.values()) {
            tabNames.remove(pane);
            unreadCounts.remove(pane);
            tabLabels.remove(pane);
            chats.remove(pane);
        }
        groupChats.clear();
    }

    void addChatTab(String name, JScrollPane pane) {
        tabNames.put(pane, name);
        chats.addTab(name, pane);
        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setOpaque(false);
        JLabel label = new JLabel(name);
        JButton closeButton = new JButton("×");
        closeButton.setPreferredSize(new java.awt.Dimension(18, 18));
        closeButton.setMargin(new Insets(0, 0, 0, 0));
        closeButton.setFocusable(false);
        styleButton(closeButton, false);
        closeButton.setToolTipText("Close chat tab");
        closeButton.getAccessibleContext().setAccessibleName("Close chat tab: " + name);
        closeButton.addActionListener(event -> {
            chats.remove(pane);
            tabLabels.remove(pane);
        });
        header.add(label, BorderLayout.CENTER);
        header.add(closeButton, BorderLayout.EAST);
        tabLabels.put(pane, label);
        chats.setTabComponentAt(chats.indexOfComponent(pane), header);
        refreshTabTitle(pane);
    }

    // 未選取的分頁收到訊息時累計；選取該分頁後歸零。
    void markUnread(Component pane) {
        if (pane != chats.getSelectedComponent()) {
            unreadCounts.merge(pane, 1, Integer::sum);
            refreshTabTitle(pane);
        }
    }

    private void clearUnread(Component pane) {
        if (pane != null && unreadCounts.remove(pane) != null) {
            refreshTabTitle(pane);
        }
    }

    void refreshTabTitle(Component pane) {
        int index = chats.indexOfComponent(pane);
        String name = tabNames.get(pane);
        if (index >= 0 && name != null) {
            int count = unreadCounts.getOrDefault(pane, 0);
            String title = count == 0 ? name : name + " (" + count + ")";
            chats.setTitleAt(index, title);
            JLabel label = tabLabels.get(pane);
            if (label != null)
                label.setText(title);
        }
    }

    void setConnected(boolean connected) {
        usersList.repaint();
        hostField.setEnabled(!connected && !model.observing && !model.connecting && !model.startingServer
                && model.temporaryServer == null);
        portField.setEnabled(!connected && !model.observing && !model.connecting && !model.startingServer
                && model.temporaryServer == null);
        nameField.setEnabled(!connected && !model.connecting && !model.joining && !model.startingServer);
        connectButton.setText(model.observing ? JOIN_LABEL : CONNECT_LABEL);
        connectButton.setEnabled(!connected && !model.connecting && !model.joining && !model.startingServer);
        viewUsersButton.setText(model.observing ? "Disconnect" : "Observe only");
        viewUsersButton.setEnabled(!connected && !model.connecting && !model.joining && !model.startingServer);
        hostButton.setText(model.temporaryServer == null ? "Start temp server" : "Stop temp server");
        hostButton
                .setEnabled(!model.startingServer
                        && (model.temporaryServer != null || (!connected && !model.observing && !model.connecting)));
        inputField.setEnabled(connected);
        sendButton.setEnabled(connected);
        refreshImageButton();
        privateMenuItem.setEnabled(connected);
        createGroupButton.setEnabled(connected);
        addMembersButton.setEnabled(connected);
        groupMembersButton.setEnabled(connected);
        leaveGroupButton.setEnabled(connected);
        leaveButton.setEnabled(connected);
        refreshStatus();
    }

    private void refreshStatus() {
        boolean live = model.socket != null;
        boolean busy = model.connecting || model.joining || model.startingServer;
        String state = busy ? "Connecting..." : !live ? "Offline"
                : model.observing ? "Viewing online users" : "Connected";
        String endpoint = live ? "  " + host() + ":" + port() : "";
        String user = live && !model.observing && model.activeName != null ? "  (" + model.activeName + ")" : "";
        statusLabel.setText("\u25CF  " + state + endpoint + user);
        statusLabel.setToolTipText(statusLabel.getText());
        statusLabel.setForeground(busy ? (darkMode ? new Color(0xF2C36B) : new Color(0x8A5800))
                : live ? (darkMode ? new Color(0x73D8A4) : new Color(0x176347)) : mutedColor());
        rosterTitle.setText("Online members (" + onlineUsers.size() + ")");
    }

    void appendMessage(String message) {
        // The controller supplies an append-only snapshot of the Lobby history.
        if (message.length() < displayedLobbyLength) {
            chatArea.setText("");
            displayedLobbyLength = 0;
        }
        if (message.length() > displayedLobbyLength) {
            appendText(chatArea, message.substring(displayedLobbyLength, message.length() - 1));
            displayedLobbyLength = message.length();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ChatWindow().setVisible(true));
    }
}
