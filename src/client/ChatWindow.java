package client;

import client.ChatModel.OnlineUser;

import java.awt.BorderLayout;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.RenderingHints;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
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
import javax.swing.JTextArea;
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
    final JTextField hostField = new JTextField("127.0.0.1");
    final JTextField portField = new JTextField("5487");
    final JTextField nameField = new JTextField();
    final JButton connectButton = new JButton("Connect");
    final JButton viewUsersButton = new JButton("View online users");
    final JButton hostButton = new JButton("Start temp server");
    final JButton themeButton = new JButton("Dark mode");
    final JTextArea chatArea = new JTextArea();
    final JTextField inputField = new JTextField();
    private final JButton imageButton = new JButton("Send image");
    private boolean imagesEnabled;
    private boolean imageBusy;
    final JButton sendButton = new JButton("Send");
    final JButton leaveButton = new JButton("Leave");
    final JButton privateButton = new JButton("Open private chat");
    final JButton createGroupButton = new JButton("Create group");
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
        setSize(760, 450);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        for (String key : THEME_KEYS)
            lightThemeDefaults.put(key, UIManager.get(key));

        // 上方放連線資料；中間顯示訊息，下方讓使用者輸入。
        JPanel connectionPanel = new JPanel(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(2, 3, 2, 3);
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.gridy = 0;
        constraints.gridx = 0;
        connectionPanel.add(new JLabel("Server IP"), constraints);
        constraints.gridx = 1;
        connectionPanel.add(new JLabel("Port"), constraints);
        constraints.gridx = 2;
        connectionPanel.add(new JLabel("Name"), constraints);
        constraints.gridx = 4;
        connectionPanel.add(themeButton, constraints);
        constraints.gridy = 1;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.gridx = 0;
        constraints.weightx = 0;
        hostField.setColumns(10);
        connectionPanel.add(hostField, constraints);
        constraints.gridx = 1;
        constraints.weightx = 0;
        portField.setColumns(5);
        connectionPanel.add(portField, constraints);
        constraints.gridx = 2;
        constraints.weightx = 1;
        nameField.setColumns(4);
        connectionPanel.add(nameField, constraints);
        constraints.gridx = 3;
        constraints.weightx = 0;
        connectionPanel.add(connectButton, constraints);
        constraints.gridx = 4;
        connectionPanel.add(hostButton, constraints);

        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chats.addTab("Lobby", createContentScroll(chatArea));
        chats.addChangeListener(event -> clearUnread(chats.getSelectedComponent()));
        setButtonIcon(sendButton, "/buttons/send.png", "Send");
        setButtonIcon(leaveButton, "/buttons/leave.png", "Leave");
        JPanel inputPanel = new JPanel(new BorderLayout(5, 0));
        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(imageButton, BorderLayout.WEST);
        JPanel buttonsPanel = new JPanel(new GridLayout(1, 2, 5, 0));
        buttonsPanel.add(sendButton);
        buttonsPanel.add(leaveButton);
        JPanel bottomRow = new JPanel(new BorderLayout());
        bottomRow.add(inputPanel, BorderLayout.CENTER);
        bottomRow.add(buttonsPanel, BorderLayout.EAST);
        int buttonWidth = Math.max(
                Math.max(hostButton.getPreferredSize().width, viewUsersButton.getPreferredSize().width),
                Math.max(privateButton.getPreferredSize().width, createGroupButton.getPreferredSize().width));
        hostButton.setPreferredSize(new java.awt.Dimension(buttonWidth, hostButton.getPreferredSize().height));
        themeButton.setPreferredSize(new java.awt.Dimension(buttonWidth, themeButton.getPreferredSize().height));
        int sidebarWidth = buttonWidth;
        buttonsPanel.setPreferredSize(new java.awt.Dimension(sidebarWidth, BUTTON_HEIGHT));
        add(connectionPanel, BorderLayout.NORTH);
        add(chats, BorderLayout.CENTER);
        JPanel usersPanel = new JPanel(new BorderLayout());
        usersPanel.setPreferredSize(new java.awt.Dimension(sidebarWidth, 0));
        JPanel usersHeader = new JPanel(new BorderLayout());
        usersHeader.add(viewUsersButton, BorderLayout.CENTER);
        usersPanel.add(usersHeader, BorderLayout.NORTH);
        // 根據實際 Lobby 分頁尺寸調整右側按鈕，兼顧字型與縮放。
        chats.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                alignChatControls(usersHeader, inputPanel);
            }
        });
        chats.addChangeListener(event -> SwingUtilities.invokeLater(() -> alignChatControls(usersHeader, inputPanel)));
        SwingUtilities.invokeLater(() -> alignChatControls(usersHeader, inputPanel));
        JScrollPane usersScroll = createContentScroll(usersList);
        usersList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        usersList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() != 2 || !SwingUtilities.isLeftMouseButton(event)
                        || !privateButton.isEnabled())
                    return;
                int index = usersList.locationToIndex(event.getPoint());
                if (index >= 0 && usersList.getCellBounds(index, index).contains(event.getPoint())) {
                    controller.openPrivateChat(onlineUsers.get(index));
                }
            }
        });
        // 僅改變顯示文字，名單內仍保留原始暱稱供私訊及群組使用。
        usersList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                if (!model.observing && !model.joining && model.socket != null && value instanceof OnlineUser
                        && ((OnlineUser) value).id.equals(model.activeId)) {
                    setText(value + " (You)");
                }
                return this;
            }
        });
        usersScroll.setPreferredSize(new java.awt.Dimension(140, 0));
        usersPanel.add(usersScroll, BorderLayout.CENTER);
        JPanel userButtons = new JPanel(new GridLayout(5, 1));
        userButtons.add(privateButton);
        userButtons.add(createGroupButton);
        userButtons.add(addMembersButton);
        userButtons.add(groupMembersButton);
        userButtons.add(leaveGroupButton);
        usersPanel.add(userButtons, BorderLayout.SOUTH);
        add(usersPanel, BorderLayout.EAST);
        add(bottomRow, BorderLayout.SOUTH);
        applyTheme();
        setConnected(false);

        connectButton.addActionListener(event -> controller.connectAction());
        viewUsersButton.addActionListener(event -> controller.viewUsersAction());
        hostButton.addActionListener(event -> controller.hostAction());
        sendButton.addActionListener(event -> controller.sendMessage());
        imageButton.addActionListener(event -> controller.sendPicture());
        chats.addChangeListener(event -> refreshImageButton());
        themeButton.addActionListener(event -> toggleTheme());
        privateButton.addActionListener(event -> controller.openSelectedChat());
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

    private void alignChatControls(JPanel header, JPanel inputPanel) {
        if (chats.getWidth() <= 0 || chats.getTabCount() == 0)
            return;
        java.awt.Rectangle lobby = chats.getBoundsAt(0);
        if (lobby == null || lobby.height <= 0)
            return;
        int top = Math.max(0, lobby.y);
        int height = top + Math.max(lobby.height, BUTTON_HEIGHT);
        Component activeChat = chats.getSelectedComponent();
        if (activeChat != null && activeChat.getWidth() > 0) {
            int left = Math.max(0, activeChat.getX());
            int right = Math.max(0, chats.getWidth() - activeChat.getX() - activeChat.getWidth());
            Insets current = inputPanel.getInsets();
            if (current.left != left || current.right != right) {
                inputPanel.setBorder(BorderFactory.createEmptyBorder(0, left, 0, right));
            }
        }
        if (header.getPreferredSize().height != height || header.getInsets().top != top) {
            header.setBorder(BorderFactory.createEmptyBorder(top, 0, 0, 0));
            header.setPreferredSize(new java.awt.Dimension(0, height));
            header.revalidate();
        }
    }

    private void setButtonIcon(JButton button, String path, String description) {
        java.net.URL resource = ChatWindow.class.getResource(path);
        if (resource != null) {
            Image source = new ImageIcon(resource).getImage();
            BufferedImage image = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(source, 0, 0, 24, 24, null);
            if (darkMode || button == sendButton) {
                graphics.setComposite(AlphaComposite.SrcIn);
                graphics.setColor(new Color(228, 230, 235));
                graphics.fillRect(0, 0, 24, 24);
            }
            graphics.dispose();
            button.setIcon(new ImageIcon(image));
            button.setText("");
        }
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
        UIManager.put("TabbedPane.tabInsets", new javax.swing.plaf.InsetsUIResource(10, 12, 10, 12));
        SwingUtilities.updateComponentTreeUI(this);
        // 已隱藏的分頁不在視窗元件樹內，也必須一起更新。
        for (PrivateChat chat : privateChats.values())
            SwingUtilities.updateComponentTreeUI(chat.pane);
        for (JScrollPane pane : groupChats.values())
            SwingUtilities.updateComponentTreeUI(pane);
        setButtonIcon(sendButton, "/buttons/send.png", "Send");
        setButtonIcon(leaveButton, "/buttons/leave.png", "Leave");
        applyControlStyle();
        themeButton.setText(darkMode ? "Light mode" : "Dark mode");
        revalidate();
        repaint();
    }

    private void applyControlStyle() {
        styleMainButton(connectButton, true);
        styleMainButton(sendButton, true);
        for (JButton button : List.of(viewUsersButton, hostButton, themeButton, imageButton, leaveButton,
                privateButton, createGroupButton, addMembersButton, groupMembersButton, leaveGroupButton)) {
            styleMainButton(button, false);
        }
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
        java.awt.Dimension size = button.getPreferredSize();
        button.setPreferredSize(new java.awt.Dimension(size.width, BUTTON_HEIGHT));
    }

    private void styleButton(JButton button, boolean primary) {
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
                Graphics2D shape = (Graphics2D) graphics.create();
                shape.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                shape.setColor(fill);
                shape.fillRoundRect(0, 0, component.getWidth(), component.getHeight(), 8, 8);
                shape.setColor(darkMode ? new Color(0x737D8D) : new Color(0x8793A4));
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
                if (primary && control.isEnabled()) {
                    graphics.setColor(Color.WHITE);
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
            area.setEditable(false);
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

    private static void appendText(JTextPane pane, String text) {
        var document = pane.getStyledDocument();
        try {
            document.insertString(document.getLength(), text + "\n", null);
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
        appendText(pane, picture.sender() + ": " + picture.filename());
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
    }

    PrivateChat privateChat(String peerId, String peerName) {
        PrivateChat chat = privateChats.computeIfAbsent(peerId, id -> {
            JTextPane area = new JTextPane();
            area.setEditable(false);
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
        JPanel header = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 3, 0));
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
        header.add(label);
        header.add(closeButton);
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
        connectButton.setText(model.observing ? "Join chat" : "Connect");
        connectButton.setEnabled(!connected && !model.connecting && !model.joining && !model.startingServer);
        viewUsersButton.setText(model.observing ? "Disconnect" : "View online users");
        viewUsersButton.setEnabled(!connected && !model.connecting && !model.joining && !model.startingServer);
        hostButton.setText(model.temporaryServer == null ? "Start temp server" : "Stop temp server");
        hostButton
                .setEnabled(!model.startingServer
                        && (model.temporaryServer != null || (!connected && !model.observing && !model.connecting)));
        inputField.setEnabled(connected);
        sendButton.setEnabled(connected);
        refreshImageButton();
        privateButton.setEnabled(connected);
        createGroupButton.setEnabled(connected);
        addMembersButton.setEnabled(connected);
        groupMembersButton.setEnabled(connected);
        leaveGroupButton.setEnabled(connected);
        leaveButton.setEnabled(connected);
    }

    void appendMessage(String message) {
        chatArea.setText(message);
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ChatWindow().setVisible(true));
    }
}
