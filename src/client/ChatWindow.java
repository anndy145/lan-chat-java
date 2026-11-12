package client;

import common.ChatProtocol;

import java.awt.BorderLayout;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Component;
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
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
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
import javax.swing.JTextField;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;
import javax.swing.ListSelectionModel;
import java.util.List;
import java.util.stream.Collectors;
import server.Server;

public class ChatWindow extends JFrame {
    private final JTextField hostField = new JTextField("127.0.0.1");
    private final JTextField portField = new JTextField("5487");
    private final JTextField nameField = new JTextField();
    private final JButton connectButton = new JButton("Connect");
    private final JButton viewUsersButton = new JButton("View online users");
    private final JButton hostButton = new JButton("Start temporary server");
    private final JButton themeButton = new JButton("Dark mode");
    private final JTextArea chatArea = new JTextArea();
    private final JTextField inputField = new JTextField();
    private final JButton sendButton = new JButton("Send");
    private final JButton leaveButton = new JButton("Leave");
    private final JButton privateButton = new JButton("Open private chat");
    private final JButton createGroupButton = new JButton("Create group");
    private final JButton addMembersButton = new JButton("Add members");
    private final DefaultListModel<OnlineUser> onlineUsers = new DefaultListModel<>();
    private final JList<OnlineUser> usersList = new JList<>(onlineUsers);
    private final JTabbedPane chats = new JTabbedPane();
    private final Map<String, PrivateChat> privateChats = new LinkedHashMap<>();
    private final Map<String, JScrollPane> groupChats = new LinkedHashMap<>();
    private final Map<Component, String> tabNames = new LinkedHashMap<>();
    private final Map<Component, Integer> unreadCounts = new LinkedHashMap<>();
    private final Map<Component, JLabel> tabLabels = new LinkedHashMap<>();
    private String activeName;
    private String activeId;
    private boolean darkMode;
    private final Map<String, Object> lightThemeDefaults = new LinkedHashMap<>();

    private static final String[] THEME_KEYS = {
            "control", "info", "text", "nimbusBase", "nimbusBlueGrey", "nimbusLightBackground",
            "Panel.background", "Label.foreground", "Button.background", "Button.foreground",
            "Button.select", "Button.disabledText", "Button.focus", "Button.gradient", "TextField.background",
            "TextField.foreground", "TextField.inactiveBackground", "TextField.inactiveForeground",
            "TextField.caretForeground", "TextField.selectionBackground", "TextField.selectionForeground",
            "TextArea.background", "TextArea.foreground", "TextArea.caretForeground",
            "TextArea.selectionBackground", "TextArea.selectionForeground", "List.background", "List.foreground",
            "List.selectionBackground", "List.selectionForeground", "TabbedPane.background", "TabbedPane.foreground",
            "TabbedPane.selected", "TabbedPane.contentAreaColor", "TabbedPane.unselectedBackground",
            "TabbedPane.focus", "TabbedPane.light", "TabbedPane.highlight", "TabbedPane.shadow",
            "TabbedPane.darkShadow", "ScrollPane.background", "Viewport.background", "OptionPane.background",
            "OptionPane.messageForeground", "ToolTip.background", "ToolTip.foreground"
    };

    private static final class OnlineUser {
        final String id;
        final String name;

        OnlineUser(String id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof OnlineUser && id.equals(((OnlineUser) other).id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }
    }

    private static final class PrivateChat {
        final String name;
        final JTextArea area;
        final JScrollPane pane;

        PrivateChat(String name, JTextArea area, JScrollPane pane) {
            this.name = name;
            this.area = area;
            this.pane = pane;
        }
    }

    // 連線狀態和介面元件只在 Swing 介面執行緒中讀寫。
    private Socket socket;
    private PrintWriter output;
    private boolean connecting;
    private boolean observing;
    private boolean joining;
    private long connectionGeneration;
    private Server temporaryServer;
    private boolean startingServer;
    private long serverGeneration;

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
        chats.addTab("Lobby", new JScrollPane(chatArea));
        chats.addChangeListener(event -> clearUnread(chats.getSelectedComponent()));
        setButtonIcon(sendButton, "/buttons/send.png", "Send");
        setButtonIcon(leaveButton, "/buttons/leave.png", "Leave");
        JPanel inputPanel = new JPanel(new BorderLayout(5, 0));
        inputPanel.add(inputField, BorderLayout.CENTER);
        JPanel buttonsPanel = new JPanel();
        buttonsPanel.add(sendButton);
        buttonsPanel.add(leaveButton);
        inputPanel.add(buttonsPanel, BorderLayout.EAST);
        int buttonWidth = Math.max(
                Math.max(hostButton.getPreferredSize().width, viewUsersButton.getPreferredSize().width),
                Math.max(privateButton.getPreferredSize().width, createGroupButton.getPreferredSize().width));
        hostButton.setPreferredSize(new java.awt.Dimension(buttonWidth, hostButton.getPreferredSize().height));
        themeButton.setPreferredSize(new java.awt.Dimension(buttonWidth, themeButton.getPreferredSize().height));
        int sidebarWidth = buttonWidth;
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
                alignUsersButton(usersHeader);
            }
        });
        chats.addChangeListener(event -> SwingUtilities.invokeLater(() -> alignUsersButton(usersHeader)));
        SwingUtilities.invokeLater(() -> alignUsersButton(usersHeader));
        JScrollPane usersScroll = new JScrollPane(usersList);
        usersList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        usersList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() != 2 || !SwingUtilities.isLeftMouseButton(event)
                        || !privateButton.isEnabled())
                    return;
                int index = usersList.locationToIndex(event.getPoint());
                if (index >= 0 && usersList.getCellBounds(index, index).contains(event.getPoint())) {
                    openPrivateChat(onlineUsers.get(index));
                }
            }
        });
        // 僅改變顯示文字，名單內仍保留原始暱稱供私訊及群組使用。
        usersList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                if (!observing && !joining && socket != null && value instanceof OnlineUser
                        && ((OnlineUser) value).id.equals(activeId)) {
                    setText(value + " (You)");
                }
                return this;
            }
        });
        usersScroll.setPreferredSize(new java.awt.Dimension(140, 0));
        usersPanel.add(usersScroll, BorderLayout.CENTER);
        JPanel userButtons = new JPanel(new GridLayout(3, 1));
        userButtons.add(privateButton);
        userButtons.add(createGroupButton);
        userButtons.add(addMembersButton);
        usersPanel.add(userButtons, BorderLayout.SOUTH);
        add(usersPanel, BorderLayout.EAST);
        add(inputPanel, BorderLayout.SOUTH);
        setConnected(false);

        connectButton.addActionListener(event -> {
            if (observing && output != null) {
                String name = nameField.getText().strip();
                if (name.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "Please enter a name.");
                    return;
                }
                if (name.codePointCount(0, name.length()) > ChatProtocol.MAX_NAME_LENGTH) {
                    JOptionPane.showMessageDialog(this, "Name must be at most 32 characters.");
                    return;
                }
                output.println(ChatProtocol.JOIN + ChatProtocol.encode(name));
                joining = true;
                setConnected(false);
            } else {
                connect(false);
            }
        });
        viewUsersButton.addActionListener(event -> {
            if (observing) {
                closeConnection();
                appendMessage("Disconnected.");
            } else {
                connect(true);
            }
        });
        hostButton.addActionListener(event -> {
            if (temporaryServer != null)
                stopTemporaryServer();
            else
                startTemporaryServer();
        });
        sendButton.addActionListener(event -> sendMessage());
        themeButton.addActionListener(event -> toggleTheme());
        privateButton.addActionListener(event -> openSelectedChat());
        createGroupButton.addActionListener(event -> createGroup());
        addMembersButton.addActionListener(event -> addGroupMembers());
        leaveButton.addActionListener(event -> leaveChat());
        inputField.addActionListener(event -> sendMessage());
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                closeConnection();
                stopTemporaryServer();
            }
        });
    }

    private void alignUsersButton(JPanel header) {
        if (chats.getWidth() <= 0 || chats.getTabCount() == 0)
            return;
        java.awt.Rectangle lobby = chats.getBoundsAt(0);
        if (lobby == null || lobby.height <= 0)
            return;
        int top = Math.max(0, lobby.y);
        int height = top + lobby.height;
        if (header.getPreferredSize().height != height || header.getInsets().top != top) {
            header.setBorder(BorderFactory.createEmptyBorder(top, 0, 0, 0));
            header.setPreferredSize(new java.awt.Dimension(0, height));
            header.revalidate();
        }
    }

    private void startTemporaryServer() {
        int port;
        try {
            port = Integer.parseInt(portField.getText().strip());
            if (port < 1 || port > 65535)
                throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            JOptionPane.showMessageDialog(this, "Port must be a number from 1 to 65535.");
            return;
        }
        startingServer = true;
        long generation = ++serverGeneration;
        setConnected(false);
        Thread worker = new Thread(() -> {
            Server server = new Server(port);
            try {
                server.bind();
                String addresses = Server.lanAddresses();
                SwingUtilities.invokeLater(() -> {
                    if (!isDisplayable() || generation != serverGeneration) {
                        server.close();
                        return;
                    }
                    temporaryServer = server;
                    startingServer = false;
                    hostField.setText("127.0.0.1");
                    setConnected(false);
                    appendMessage("Temporary server started on port " + port + ". LAN IP: " + addresses);
                });
                server.serve();
                SwingUtilities.invokeLater(() -> {
                    if (generation == serverGeneration && temporaryServer == server) {
                        temporaryServer = null;
                        setConnected(socket != null && !observing);
                        appendMessage("Temporary server stopped.");
                    }
                });
            } catch (IOException exception) {
                SwingUtilities.invokeLater(() -> {
                    if (generation != serverGeneration || !isDisplayable())
                        return;
                    temporaryServer = null;
                    startingServer = false;
                    setConnected(socket != null && !observing);
                    appendMessage("Temporary server failed: " + exception.getMessage());
                });
            } finally {
                server.close();
            }
        }, "temporary-server");
        worker.setDaemon(true);
        worker.start();
    }

    private void stopTemporaryServer() {
        serverGeneration++;
        startingServer = false;
        Server server = temporaryServer;
        temporaryServer = null;
        if (server != null) {
            server.close();
            appendMessage("Temporary server stopped.");
        }
        setConnected(socket != null && !observing);
    }

    // 載入按鈕圖示；若找不到圖片，保留按鈕原本的文字。
    private void setButtonIcon(JButton button, String path, String description) {
        java.net.URL resource = ChatWindow.class.getResource(path);
        if (resource != null) {
            Image source = new ImageIcon(resource).getImage();
            BufferedImage image = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(source, 0, 0, 24, 24, null);
            if (darkMode) {
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
        if (darkMode) {
            // 參考 Facebook 深色介面的灰黑層次與藍色強調色。
            ColorUIResource background = new ColorUIResource(24, 25, 26);
            ColorUIResource surface = new ColorUIResource(36, 37, 38);
            ColorUIResource button = new ColorUIResource(58, 59, 60);
            ColorUIResource input = new ColorUIResource(36, 37, 38);
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
                    "TextArea.background", "List.background", "nimbusLightBackground" })
                UIManager.put(key, input);
            for (String key : new String[] { "Label.foreground", "Button.foreground", "TextField.foreground",
                    "TextField.caretForeground", "TextArea.foreground", "TextArea.caretForeground", "List.foreground",
                    "TabbedPane.foreground", "OptionPane.messageForeground", "ToolTip.foreground",
                    "TextField.selectionForeground", "TextArea.selectionForeground", "List.selectionForeground" }) {
                UIManager.put(key, text);
            }
            for (String key : new String[] { "Button.disabledText", "TextField.inactiveForeground" })
                UIManager.put(key, disabled);
            for (String key : new String[] { "TextField.selectionBackground", "TextArea.selectionBackground",
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
        }
        SwingUtilities.updateComponentTreeUI(this);
        // 已隱藏的分頁不在視窗元件樹內，也必須一起更新。
        for (PrivateChat chat : privateChats.values())
            SwingUtilities.updateComponentTreeUI(chat.pane);
        for (JScrollPane pane : groupChats.values())
            SwingUtilities.updateComponentTreeUI(pane);
        setButtonIcon(sendButton, "/buttons/send.png", "Send");
        setButtonIcon(leaveButton, "/buttons/leave.png", "Leave");
        themeButton.setText(darkMode ? "Light mode" : "Dark mode");
        revalidate();
        repaint();
    }

    private void connect(boolean observeOnly) {
        String host = hostField.getText().strip();
        String name = nameField.getText().strip();
        if (host.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a server IP or hostname.");
            hostField.requestFocusInWindow();
            return;
        }
        int port;
        try {
            port = Integer.parseInt(portField.getText().strip());
        } catch (NumberFormatException exception) {
            JOptionPane.showMessageDialog(this, "Port must be a number from 1 to 65535.");
            portField.requestFocusInWindow();
            return;
        }
        if (port < 1 || port > 65535) {
            JOptionPane.showMessageDialog(this, "Port must be between 1 and 65535.");
            portField.requestFocusInWindow();
            return;
        }
        if (!observeOnly && name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a name.");
            nameField.requestFocusInWindow();
            return;
        }

        if (!observeOnly && name.codePointCount(0, name.length()) > ChatProtocol.MAX_NAME_LENGTH) {
            JOptionPane.showMessageDialog(this, "Name must be at most 32 characters.");
            nameField.requestFocusInWindow();
            return;
        }

        connecting = true;
        clearPrivateChats();
        activeName = null;
        activeId = null;
        long generation = ++connectionGeneration;
        setConnected(false);
        appendMessage("Connecting...");

        // 網路連線可能需要等待，放在背景執行緒以免視窗卡住。
        Thread connectionThread = new Thread(() -> {
            Socket newSocket = new Socket();
            try {
                ChatConnection.receive(newSocket, host, port, observeOnly, name, writer -> {
                // Swing 元件只能在介面執行緒更新。
                SwingUtilities.invokeLater(() -> {
                    if (!isDisplayable() || generation != connectionGeneration) {
                        closeSocket(newSocket);
                        return;
                    }
                    socket = newSocket;
                    output = writer;
                    connecting = false;
                    observing = true;
                    joining = !observeOnly;
                    setConnected(false);
                    appendMessage(observeOnly ? "Connected. You can view online users." : "Joining chat...");
                });
                }, received -> {
                    SwingUtilities.invokeLater(() -> {
                        if (socket == newSocket) {
                            if (received.startsWith(ChatProtocol.JOINED)) {
                                activeId = received.substring(8);
                                joining = false;
                                observing = false;
                                if (activeName != null && !activeName.equals(nameField.getText().strip())) {
                                    clearPrivateChats();
                                }
                                activeName = nameField.getText().strip();
                                setConnected(true);
                                appendMessage("You joined the chat.");
                            } else if (received.equals(ChatProtocol.ERROR_NAME_LENGTH)) {
                                joining = false;
                                observing = true;
                                setConnected(false);
                                JOptionPane.showMessageDialog(this, "Name must be at most 32 characters.");
                            } else if (received.equals(ChatProtocol.ERROR_MESSAGE_LENGTH)) {
                                JOptionPane.showMessageDialog(this, "Message must be at most 2000 characters.");
                            } else if (received.equals(ChatProtocol.ERROR_NAME)) {
                                joining = false;
                                observing = true;
                                setConnected(false);
                                appendMessage("That name is already in use. Choose another name.");
                            } else if (received.equals(ChatProtocol.ERROR_VERSION) || received.equals("@joined")) {
                                appendMessage("Incompatible server version. Update both server and client.");
                                closeConnection();
                            } else if (received.startsWith(ChatProtocol.USERS)) {
                                updateOnlineUsers(received.substring(7));
                            } else if (received.startsWith(ChatProtocol.PRIVATE_MESSAGE) && !observing) {
                                receivePrivate(received);
                            } else if (received.startsWith(ChatProtocol.GROUP_MESSAGE) && !observing) {
                                receiveGroup(received);
                            } else if (received.startsWith(ChatProtocol.GROUP_ERROR)) {
                                try {
                                    JOptionPane.showMessageDialog(this, new String(Base64.getDecoder().decode(
                                            received.substring(13)), StandardCharsets.UTF_8));
                                } catch (IllegalArgumentException exception) {
                                    appendMessage("Received an invalid notification.");
                                }
                            } else if (received.startsWith(ChatProtocol.NOTICE)) {
                                try {
                                    appendMessage(new String(Base64.getDecoder().decode(received.substring(8)),
                                            StandardCharsets.UTF_8));
                                } catch (IllegalArgumentException exception) {
                                    appendMessage("Received an invalid notification.");
                                }
                            } else if (!observing) {
                                appendMessage(received);
                            }
                        }
                    });
                });
                SwingUtilities.invokeLater(() -> {
                    if (generation == connectionGeneration)
                        disconnected(newSocket, "Disconnected from the server.");
                });
            } catch (IOException exception) {
                SwingUtilities.invokeLater(() -> {
                    if (generation == connectionGeneration)
                        disconnected(newSocket, "Connection closed or failed: " + exception.getMessage());
                });
            } finally {
                closeSocket(newSocket);
            }
        }, "chat-connection");
        connectionThread.setDaemon(true);
        connectionThread.start();
    }

    private void sendMessage() {
        String message = inputField.getText();
        if (output == null || message.isBlank()) {
            return;
        }
        if (message.codePointCount(0, message.length()) > ChatProtocol.MAX_MESSAGE_LENGTH) {
            JOptionPane.showMessageDialog(this, "Message must be at most 2000 characters.");
            return;
        }
        // 伺服器會把訊息廣播回來，因此此處不重複加到聊天區。
        inputField.setText("");
        if ("/quit".equalsIgnoreCase(message.strip())) {
            leaveChat();
        } else if (chats.getSelectedIndex() == 0) {
            output.println(ChatProtocol.SAY + encode(message));
        } else if (groupChats.containsValue(chats.getSelectedComponent())) {
            String groupId = groupChats.entrySet().stream()
                    .filter(entry -> entry.getValue() == chats.getSelectedComponent())
                    .map(Map.Entry::getKey).findFirst().orElseThrow();
            output.println(ChatProtocol.GROUP_SEND + groupId + ":" + encode(message));
        } else {
            String targetId = privateChats.entrySet().stream()
                    .filter(entry -> entry.getValue().pane == chats.getSelectedComponent())
                    .map(Map.Entry::getKey).findFirst().orElseThrow();
            output.println(ChatProtocol.PRIVATE + targetId + ":" + encode(message));
        }
    }

    private void openSelectedChat() {
        List<OnlineUser> selected = usersList.getSelectedValuesList();
        if (selected.size() != 1) {
            JOptionPane.showMessageDialog(this, "Select exactly one online user.");
            return;
        }
        openPrivateChat(selected.get(0));
    }

    private void openPrivateChat(OnlineUser target) {
        if (target.id.equals(activeId)) {
            JOptionPane.showMessageDialog(this, "Select another user.");
            return;
        }
        PrivateChat chat = privateChat(target.id, target.name);
        chats.setSelectedComponent(chat.pane);
        inputField.requestFocusInWindow();
    }

    private void createGroup() {
        List<String> members = usersList.getSelectedValuesList().stream()
                .filter(user -> !user.id.equals(activeId)).map(user -> user.id).collect(Collectors.toList());
        if (members.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Select at least one other online user. Hold Ctrl to select more.");
            return;
        }
        String name = JOptionPane.showInputDialog(this, "Group name:");
        if (name == null)
            return;
        name = name.strip();
        if (name.isEmpty() || name.length() > 80 || name.contains("\n") || name.contains("\r")) {
            JOptionPane.showMessageDialog(this, "Enter a group name of up to 80 characters.");
            return;
        }
        output.println(ChatProtocol.GROUP_CREATE + encode(name) + ":"
                + String.join(",", members));
    }

    private void addGroupMembers() {
        String groupId = groupChats.entrySet().stream()
                .filter(entry -> entry.getValue() == chats.getSelectedComponent())
                .map(Map.Entry::getKey).findFirst().orElse(null);
        if (groupId == null) {
            JOptionPane.showMessageDialog(this, "Select a group tab first.");
            return;
        }
        List<String> members = usersList.getSelectedValuesList().stream()
                .filter(user -> !user.id.equals(activeId)).map(user -> user.id).collect(Collectors.toList());
        if (members.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Select at least one other online user. Hold Ctrl to select more.");
            return;
        }
        output.println(ChatProtocol.GROUP_ADD + groupId + ":" + String.join(",", members));
    }

    private static String encode(String text) {
        return ChatProtocol.encode(text);
    }

    private void receiveGroup(String message) {
        String[] parts = message.substring(7).split(":", 3);
        if (parts.length != 3)
            return;
        try {
            String name = ChatProtocol.decode(parts[1]);
            String text = ChatProtocol.decode(parts[2]);
            JScrollPane pane = groupChats.get(parts[0]);
            if (pane == null) {
                JTextArea area = new JTextArea();
                area.setEditable(false);
                area.setLineWrap(true);
                area.setWrapStyleWord(true);
                pane = new JScrollPane(area);
                groupChats.put(parts[0], pane);
                addChatTab("Group: " + name, pane);
            } else if (chats.indexOfComponent(pane) < 0) {
                addChatTab(tabNames.get(pane), pane);
            }
            JTextArea area = (JTextArea) pane.getViewport().getView();
            area.append(text + "\n");
            area.setCaretPosition(area.getDocument().getLength());
            markUnread(pane);
        } catch (IllegalArgumentException exception) {
            appendMessage("Received an invalid group message.");
        }
    }

    // 每位對象有獨立的文字區；收到私訊時也會自動建立分頁。
    private PrivateChat privateChat(String peerId, String peerName) {
        PrivateChat chat = privateChats.computeIfAbsent(peerId, id -> {
            JTextArea area = new JTextArea();
            area.setEditable(false);
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            JScrollPane pane = new JScrollPane(area);
            addChatTab(peerName, pane);
            return new PrivateChat(peerName, area, pane);
        });
        if (chats.indexOfComponent(chat.pane) < 0) {
            addChatTab(tabNames.get(chat.pane), chat.pane);
        }
        return chat;
    }

    private void receivePrivate(String message) {
        String[] parts = message.substring(4).split(":", 3);
        if (parts.length != 3)
            return;
        try {
            String peer = ChatProtocol.decode(parts[1]);
            String text = ChatProtocol.decode(parts[2]);
            PrivateChat chat = privateChats.get(parts[0]);
            if (chat != null)
                chat = privateChat(parts[0], chat.name);
            else if (!peer.isEmpty())
                chat = privateChat(parts[0], peer);
            if (chat == null) {
                appendMessage(text);
                return;
            }
            chat.area.append(text + "\n");
            chat.area.setCaretPosition(chat.area.getDocument().getLength());
            markUnread(chat.pane);
        } catch (IllegalArgumentException exception) {
            appendMessage("Received an invalid private message.");
        }
    }

    // 離開聊天但保留連線，以繼續接收線上名單。
    private void leaveChat() {
        if (output == null) {
            return;
        }
        observing = true;
        activeId = null;
        output.println(ChatProtocol.LEAVE);
        clearGroupChats();
        setConnected(false);
        inputField.setText("");
        appendMessage("You left the chat.");
    }

    private void disconnected(Socket closedSocket, String message) {
        if (socket == closedSocket || (socket == null && connecting)) {
            socket = null;
            output = null;
            connecting = false;
            joining = false;
            observing = false;
            activeId = null;
            onlineUsers.clear();
            setConnected(false);
            if (isDisplayable()) {
                appendMessage(message);
            }
        }
    }

    private void closeConnection() {
        connectionGeneration++;
        Socket oldSocket = socket;
        socket = null;
        output = null;
        connecting = false;
        joining = false;
        observing = false;
        activeId = null;
        onlineUsers.clear();
        setConnected(false);
        closeSocket(oldSocket);
    }

    private void clearPrivateChats() {
        while (chats.getTabCount() > 1)
            chats.removeTabAt(1);
        privateChats.clear();
        groupChats.clear();
        tabNames.clear();
        unreadCounts.clear();
        tabLabels.clear();
    }

    private void clearGroupChats() {
        for (JScrollPane pane : groupChats.values()) {
            tabNames.remove(pane);
            unreadCounts.remove(pane);
            tabLabels.remove(pane);
            chats.remove(pane);
        }
        groupChats.clear();
    }

    private void addChatTab(String name, JScrollPane pane) {
        tabNames.put(pane, name);
        chats.addTab(name, pane);
        JPanel header = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 3, 0));
        header.setOpaque(false);
        JLabel label = new JLabel(name);
        JButton closeButton = new JButton("×");
        closeButton.setPreferredSize(new java.awt.Dimension(18, 18));
        closeButton.setMargin(new Insets(0, 0, 0, 0));
        closeButton.setFocusable(false);
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
    private void markUnread(Component pane) {
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

    private void refreshTabTitle(Component pane) {
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

    private static void closeSocket(Socket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void setConnected(boolean connected) {
        usersList.repaint();
        hostField.setEnabled(!connected && !observing && !connecting && !startingServer && temporaryServer == null);
        portField.setEnabled(!connected && !observing && !connecting && !startingServer && temporaryServer == null);
        nameField.setEnabled(!connected && !connecting && !joining && !startingServer);
        connectButton.setText(observing ? "Join chat" : "Connect");
        connectButton.setEnabled(!connected && !connecting && !joining && !startingServer);
        viewUsersButton.setText(observing ? "Disconnect" : "View online users");
        viewUsersButton.setEnabled(!connected && !connecting && !joining && !startingServer);
        hostButton.setText(temporaryServer == null ? "Start temporary server" : "Stop temporary server");
        hostButton
                .setEnabled(!startingServer && (temporaryServer != null || (!connected && !observing && !connecting)));
        inputField.setEnabled(connected);
        sendButton.setEnabled(connected);
        privateButton.setEnabled(connected);
        createGroupButton.setEnabled(connected);
        addMembersButton.setEnabled(connected);
        leaveButton.setEnabled(connected);
    }

    private void appendMessage(String message) {
        chatArea.append(message + "\n");
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    // 伺服器每次傳送完整名單，以最新名單取代舊內容。
    private void updateOnlineUsers(String encodedNames) {
        List<OnlineUser> selected = usersList.getSelectedValuesList();
        onlineUsers.clear();
        if (!encodedNames.isEmpty()) {
            for (String encoded : encodedNames.split(",")) {
                try {
                    String[] parts = encoded.split("=", 2);
                    if (parts.length != 2)
                        throw new IllegalArgumentException();
                    java.util.UUID.fromString(parts[0]);
                    onlineUsers.addElement(new OnlineUser(parts[0],
                            ChatProtocol.decode(parts[1])));
                } catch (IllegalArgumentException exception) {
                    appendMessage("Received an invalid online user list.");
                    onlineUsers.clear();
                    return;
                }
            }
        }
        int[] indexes = selected.stream().mapToInt(onlineUsers::indexOf).filter(index -> index >= 0).toArray();
        usersList.setSelectedIndices(indexes);
        for (Map.Entry<String, PrivateChat> entry : privateChats.entrySet()) {
            boolean online = java.util.Collections.list(onlineUsers.elements()).stream()
                    .anyMatch(user -> user.id.equals(entry.getKey()));
            JScrollPane pane = entry.getValue().pane;
            tabNames.put(pane, entry.getValue().name + (online ? "" : " (offline)"));
            refreshTabTitle(pane);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ChatWindow().setVisible(true));
    }
}
