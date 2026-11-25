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
import javax.swing.JTextArea;
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
    final JTextArea inputField = new JTextArea(2, 0) {
        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (getDocument().getLength() != 0) return;
            Graphics hint = graphics.create();
            hint.setColor(theme.mutedColor());
            var metrics = hint.getFontMetrics(getFont());
            hint.drawString("Type a message...", getInsets().left,
                    getInsets().top + metrics.getAscent());
            hint.dispose();
        }
    };
    private final JButton imageButton = new JButton();
    private final JButton fileButton = new JButton();
    private final JLabel transferLabel = new JLabel();
    private boolean imagesEnabled;
    private boolean lobbyImagesEnabled;
    private boolean filesEnabled;
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
    private final ChatTheme theme = new ChatTheme();

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
        fileButton.addActionListener(event -> controller.sendFile());
        inputField.setTransferHandler(new AttachmentTransferHandler(inputField.getTransferHandler(), this, controller));
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
        inputField.getInputMap().put(javax.swing.KeyStroke.getKeyStroke("ENTER"), "send-message");
        inputField.getInputMap().put(javax.swing.KeyStroke.getKeyStroke("shift ENTER"),
                javax.swing.text.DefaultEditorKit.insertBreakAction);
        inputField.getActionMap().put("send-message", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                if (sendButton.isEnabled()) controller.sendMessage();
            }
        });
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
        settingsPanel.setBorder(theme.controlBorder(new Insets(8, 8, 8, 8)));
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
        describeButton(fileButton, "Send file (up to 20 MiB)");
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
        JPanel attachments = new JPanel(new java.awt.GridLayout(1, 2, 6, 0));
        attachments.add(imageButton);
        attachments.add(fileButton);
        composer.add(attachments, BorderLayout.WEST);
        inputField.setLineWrap(true);
        inputField.setWrapStyleWord(true);
        inputField.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
        inputField.setToolTipText("Enter to send; Shift+Enter for a new line; paste or drop files to send them");
        JScrollPane inputScroll = new JScrollPane(inputField);
        inputScroll.setBorder(theme.controlBorder(new Insets(1, 1, 1, 1)));
        composer.add(inputScroll, BorderLayout.CENTER);
        composer.add(sendButton, BorderLayout.EAST);
        JPanel conversation = new JPanel(new BorderLayout(0, 10));
        conversation.add(groupActions, BorderLayout.NORTH);
        conversation.add(tabRegion, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout(0, 4));
        transferLabel.setFont(transferLabel.getFont().deriveFont(11f));
        transferLabel.setVisible(false);
        bottom.add(transferLabel, BorderLayout.NORTH);
        bottom.add(composer, BorderLayout.CENTER);
        conversation.add(bottom, BorderLayout.SOUTH);
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
        theme.toggle();
        applyTheme();
    }

    private void applyTheme() {
        theme.applyUiDefaults();
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
        themeButton.setIcon(ChatIcons.themeToggle(theme));
        imageButton.setIcon(ChatIcons.image(theme));
        fileButton.setIcon(ChatIcons.document(theme));
        transferLabel.setForeground(theme.mutedColor());
        describeButton(themeButton, theme.isDarkMode() ? "Switch to light mode" : "Switch to dark mode");
        rosterPanel.setBackground(theme.rosterBackground());
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
        theme.styleMainButton(connectButton, true, false, CONNECT_LABEL, JOIN_LABEL);
        theme.styleMainButton(sendButton, true, false);
        theme.styleMainButton(hostButton, false, false, "Start temp server", "Stop temp server");
        theme.styleMainButton(viewUsersButton, false, false, "Observe only", "Disconnect");
        for (JButton button : List.of(themeButton, imageButton, fileButton,
                settingsButton, createGroupButton, addMembersButton, groupMembersButton)) {
            theme.styleMainButton(button, false, false);
        }
        for (JButton button : List.of(leaveButton, leaveGroupButton)) {
            theme.styleMainButton(button, false, true);
        }
        for (JButton button : List.of(themeButton, imageButton, fileButton))
            button.setPreferredSize(new Dimension(BUTTON_HEIGHT, BUTTON_HEIGHT));
        createGroupButton.setPreferredSize(new Dimension(TAB_ACTION_SIZE, TAB_ACTION_SIZE));
        // 分頁關閉按鈕維持原本的點擊範圍。
        for (int index = 0; index < chats.getTabCount(); index++) {
            if (chats.getTabComponentAt(index) instanceof JPanel header) {
                for (Component component : header.getComponents()) {
                    if (component instanceof JButton button) {
                        theme.styleButton(button, false, false);
                    }
                }
            }
        }
        for (JTextField field : List.of(hostField, portField, nameField)) {
            field.setBorder(theme.controlBorder(new Insets(4, 7, 4, 7)));
        }
    }

    private JScrollPane createContentScroll(Component content) {
        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(theme.controlBorder(new Insets(4, 4, 4, 4)));
        return scroll;
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


    private void configureTranscript(JTextPane pane) {
        pane.setEditable(false);
        pane.setMargin(new Insets(8, 10, 8, 10));
        // 將檔案拖曳到聊天內容區也能傳送；保留原本的文字選取與複製功能。
        pane.setTransferHandler(new AttachmentTransferHandler(pane.getTransferHandler(), this, controller));

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
        if (role.equals("body") || role.equals("braille")) {
            if (role.equals("braille")) StyleConstants.setFontFamily(attributes, java.awt.Font.MONOSPACED);
            StyleConstants.setForeground(attributes, theme.bodyTextColor());
        } else if (role.equals("sender")) {
            StyleConstants.setBold(attributes, true);
            StyleConstants.setForeground(attributes, theme.senderTextColor());
        } else {
            StyleConstants.setFontSize(attributes, 11);
            StyleConstants.setForeground(attributes, theme.mutedColor());
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
                String body = content.substring(separator + 2);
                boolean braille = body.codePoints().anyMatch(codePoint -> codePoint >= 0x2800 && codePoint <= 0x28FF);
                document.insertString(start, body + "\n", textRole(braille ? "braille" : "body"));
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
        if (!enabled) lobbyImagesEnabled = false;
        refreshImageButton();
    }

    void enableLobbyImages(boolean enabled) {
        lobbyImagesEnabled = enabled;
        refreshImageButton();
    }

    void enableFiles(boolean enabled) {
        filesEnabled = enabled;
        refreshImageButton();
    }

    /** 顯示目前的上傳進度；傳入 null 時隱藏。 */
    void setTransferStatus(String status) {
        transferLabel.setText(status == null ? "" : status);
        transferLabel.setVisible(status != null);
    }

    private boolean chatSelected() {
        return isLobbySelected() || selectedPrivateId() != null || selectedGroupId() != null;
    }

    boolean imagesAllowedHere() {
        return imagesEnabled && sendButton.isEnabled() && chatSelected()
                && (!isLobbySelected() || lobbyImagesEnabled);
    }

    boolean filesAllowed() {
        return filesEnabled && sendButton.isEnabled() && chatSelected();
    }

    String selectedChatTitle() {
        String name = tabNames.get(chats.getSelectedComponent());
        return name != null ? name : "Lobby";
    }

    private void refreshImageButton() {
        imageButton.setEnabled(imagesAllowedHere());
        fileButton.setEnabled(filesAllowed());
    }

    Path choosePicture() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("PNG and JPEG images", "png", "jpg", "jpeg"));
        return chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION
                ? chooser.getSelectedFile().toPath()
                : null;
    }

    List<Path> chooseFiles() {
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        chooser.setDialogTitle("Send files (up to 20 MiB each)");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return List.of();
        return java.util.Arrays.stream(chooser.getSelectedFiles()).map(java.io.File::toPath).toList();
    }

    boolean confirmFiles(List<Path> files, String title) {
        StringBuilder text = new StringBuilder("Send " + (files.size() == 1 ? "this file" : files.size() + " files")
                + " to " + title + "?\n");
        files.stream().limit(5).forEach(path -> text.append("\n  ").append(path.getFileName()));
        if (files.size() > 5) text.append("\n  ... and ").append(files.size() - 5).append(" more");
        return JOptionPane.showConfirmDialog(this, text.toString(), "Send files",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.OK_OPTION;
    }

    // 貼上的截圖可能含有敏感內容，送出前先顯示預覽讓使用者確認。
    boolean confirmPastedImage(java.awt.Image image, String title) {
        ImageIcon original = new ImageIcon(image);
        int width = original.getIconWidth();
        int height = original.getIconHeight();
        if (width <= 0 || height <= 0) {
            showMessage("The clipboard image could not be read.");
            return false;
        }
        double scale = Math.min(1.0, 320.0 / Math.max(width, height));
        ImageIcon preview = new ImageIcon(image.getScaledInstance(Math.max(1, (int) (width * scale)),
                Math.max(1, (int) (height * scale)), java.awt.Image.SCALE_SMOOTH));
        JLabel label = new JLabel("Send this image to " + title + "?", preview, JLabel.CENTER);
        label.setVerticalTextPosition(JLabel.BOTTOM);
        label.setHorizontalTextPosition(JLabel.CENTER);
        return JOptionPane.showConfirmDialog(this, label, "Send pasted image",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION;
    }

    private JScrollPane conversationScroll(common.TransferWire.Destination destination, String conversation, String title) {
        return switch (destination) {
            case LOBBY -> (JScrollPane) chats.getComponentAt(0);
            case GROUP -> groupChat(conversation, title);
            case PRIVATE -> privateChat(conversation, title).pane;
        };
    }

    void showPicture(ChatImages.Picture picture) {
        JScrollPane scroll = conversationScroll(picture.destination(), picture.conversation(), picture.title());
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

    void showAttachment(ChatFiles.Attachment file) {
        JScrollPane scroll = conversationScroll(file.destination(), file.conversation(), file.title());
        JTextPane pane = (JTextPane) scroll.getViewport().getView();
        appendText(pane, file.sender() + " shared a file", false);
        JButton button = new JButton(file.filename() + "   " + ChatFiles.size(file.bytes().length) + "   Save...",
                ChatIcons.document(theme));
        button.setIconTextGap(8);
        button.setToolTipText("Save " + file.filename());
        button.getAccessibleContext().setAccessibleName("Save file " + file.filename());
        button.addActionListener(event -> saveBytes(file.filename(), file.bytes(), "file"));
        pane.setCaretPosition(pane.getDocument().getLength());
        pane.insertComponent(button);
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
                        saveBytes(picture.filename(), picture.bytes(), "image");
                });
            } catch (IOException exception) {
                SwingUtilities.invokeLater(() -> showMessage(exception.getMessage()));
            }
        });
    }

    private void saveBytes(String filename, byte[] bytes, String kind) {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File(filename));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION)
            return;
        Path destination = chooser.getSelectedFile().toPath();
        boolean replace = Files.exists(destination);
        if (replace && JOptionPane.showConfirmDialog(this, destination.getFileName() + " already exists. Replace it?",
                "Confirm save", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION)
            return;
        Thread.ofVirtual().start(() -> {
            try {
                if (replace) {
                    Files.write(destination, bytes, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                            java.nio.file.StandardOpenOption.WRITE);
                } else {
                    Files.write(destination, bytes, java.nio.file.StandardOpenOption.CREATE_NEW,
                            java.nio.file.StandardOpenOption.WRITE);
                }
            } catch (IOException exception) {
                SwingUtilities.invokeLater(() -> showMessage("Could not save " + kind + ": " + exception.getMessage()));
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
        theme.styleButton(closeButton, false, false);
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
        statusLabel.setForeground(theme.statusColor(busy, live));
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
