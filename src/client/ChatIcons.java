package client;

import java.awt.BasicStroke;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import javax.swing.Icon;

/** 視窗工具列與檔案卡片使用的向量圖示。 */
final class ChatIcons {
    private ChatIcons() {}

    static Icon image(ChatTheme theme) {
        return new VectorIcon(theme, Kind.IMAGE);
    }

    static Icon document(ChatTheme theme) {
        return new VectorIcon(theme, Kind.DOCUMENT);
    }

    static Icon themeToggle(ChatTheme theme) {
        return new VectorIcon(theme, Kind.THEME);
    }

    private enum Kind { IMAGE, DOCUMENT, THEME }

    private static final class VectorIcon implements Icon {
        private final ChatTheme theme;
        private final Kind kind;

        VectorIcon(ChatTheme theme, Kind kind) {
            this.theme = theme;
            this.kind = kind;
        }

        @Override public int getIconWidth() { return 18; }
        @Override public int getIconHeight() { return 18; }

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D drawing = (Graphics2D) graphics.create();
            drawing.translate(x, y);
            drawing.setColor(component.isEnabled() ? component.getForeground() : theme.mutedColor());
            drawing.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            drawing.setStroke(new BasicStroke(1.5f));
            switch (kind) {
                case IMAGE -> {
                    drawing.drawRoundRect(1, 2, 15, 13, 3, 3);
                    drawing.drawOval(4, 5, 2, 2);
                    drawing.drawPolyline(new int[]{3, 8, 11, 14}, new int[]{12, 8, 12, 9}, 4);
                }
                case DOCUMENT -> {
                    drawing.drawPolygon(new int[]{3, 11, 15, 15, 3}, new int[]{1, 1, 5, 17, 17}, 5);
                    drawing.drawPolyline(new int[]{11, 11, 15}, new int[]{1, 5, 5}, 3);
                    drawing.drawLine(6, 9, 12, 9);
                    drawing.drawLine(6, 12, 12, 12);
                }
                case THEME -> {
                    if (theme.isDarkMode()) {
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
                }
            }
            drawing.dispose();
        }
    }
}
