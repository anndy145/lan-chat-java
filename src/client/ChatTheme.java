package client;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.InsetsUIResource;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicGraphicsUtils;

/** 管理亮色與深色主題的 UIManager 色票、按鈕樣式與元件邊框。 */
final class ChatTheme {
    static final int BUTTON_HEIGHT = 34;

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

    private final Map<String, Object> lightThemeDefaults = new LinkedHashMap<>();
    private boolean darkMode;

    ChatTheme() {
        for (String key : THEME_KEYS) {
            lightThemeDefaults.put(key, UIManager.get(key));
        }
    }

    boolean isDarkMode() {
        return darkMode;
    }

    void toggle() {
        darkMode = !darkMode;
    }

    void applyUiDefaults() {
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
            UIManager.put("Button.gradient", List.of(0f, 0f, button, button, button));
            for (String key : new String[] { "control", "Panel.background", "OptionPane.background",
                    "ScrollPane.background", "Viewport.background", "TabbedPane.background",
                    "TabbedPane.contentAreaColor", "nimbusBlueGrey" }) {
                UIManager.put(key, background);
            }
            UIManager.put("Button.background", button);
            for (String key : new String[] { "TabbedPane.selected", "TabbedPane.unselectedBackground",
                    "info", "ToolTip.background", "nimbusBase" }) {
                UIManager.put(key, surface);
            }
            for (String key : new String[] { "text", "TextField.background", "TextField.inactiveBackground",
                    "TextArea.background", "TextPane.background", "List.background", "nimbusLightBackground" }) {
                UIManager.put(key, input);
            }
            for (String key : new String[] { "Label.foreground", "Button.foreground", "TextField.foreground",
                    "TextField.caretForeground", "TextArea.foreground", "TextPane.foreground",
                    "TextArea.caretForeground", "TextPane.caretForeground", "List.foreground",
                    "TabbedPane.foreground", "OptionPane.messageForeground", "ToolTip.foreground",
                    "TextField.selectionForeground", "TextArea.selectionForeground", "TextPane.selectionForeground",
                    "List.selectionForeground" }) {
                UIManager.put(key, text);
            }
            for (String key : new String[] { "Button.disabledText", "TextField.inactiveForeground" }) {
                UIManager.put(key, disabled);
            }
            for (String key : new String[] { "TextField.selectionBackground", "TextArea.selectionBackground",
                    "TextPane.selectionBackground", "List.selectionBackground", "Button.select" }) {
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
        UIManager.put("TabbedPane.tabInsets", new InsetsUIResource(4, 8, 4, 8));
    }

    Color mutedColor() {
        return darkMode ? new Color(0xA9B2C3) : new Color(0x566273);
    }

    Color dangerColor() {
        return darkMode ? new Color(0xFFA3A3) : new Color(0xA32832);
    }

    Color focusColor() {
        return darkMode ? new Color(0x8ABBFF) : new Color(0x1446B3);
    }

    Color rosterBackground() {
        return darkMode ? new Color(0x181B22) : new Color(0xF5F7FA);
    }

    Color bodyTextColor() {
        return darkMode ? new Color(0xE4E6EB) : new Color(0x20242C);
    }

    Color senderTextColor() {
        return darkMode ? new Color(0xA5C6FF) : new Color(0x244D9E);
    }

    Color statusColor(boolean busy, boolean live) {
        if (busy) return darkMode ? new Color(0xF2C36B) : new Color(0x8A5800);
        if (live) return darkMode ? new Color(0x73D8A4) : new Color(0x176347);
        return mutedColor();
    }

    AbstractBorder controlBorder(Insets padding) {
        return new ControlBorder(padding);
    }

    void styleMainButton(JButton button, boolean primary, boolean danger, String... alternateLabels) {
        styleButton(button, primary, danger);
        button.setPreferredSize(null);
        int width = button.getPreferredSize().width;
        if (alternateLabels.length > 0) {
            var metrics = button.getFontMetrics(button.getFont());
            int maxWidth = metrics.stringWidth(button.getText());
            for (String label : alternateLabels) {
                maxWidth = Math.max(maxWidth, metrics.stringWidth(label));
            }
            width += maxWidth - metrics.stringWidth(button.getText());
        }
        button.setPreferredSize(new Dimension(width + 8, BUTTON_HEIGHT));
    }

    void styleButton(JButton button, boolean primary, boolean danger) {
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
            protected void paintText(Graphics graphics, AbstractButton control, Rectangle bounds, String text) {
                // BasicButtonUI 會使用元件的前景色；主按鈕使用白字以維持對比。
                if (primary || danger || !control.isEnabled()) {
                    graphics.setColor(!control.isEnabled() ? mutedColor() : danger ? dangerColor() : Color.WHITE);
                    BasicGraphicsUtils.drawStringUnderlineCharAt(graphics, text,
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
}
