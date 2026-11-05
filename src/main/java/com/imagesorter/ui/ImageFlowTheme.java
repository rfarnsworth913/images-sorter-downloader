package com.imagesorter.ui;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.plaf.basic.BasicButtonUI;

/** Shared colors, spacing, and painted controls for the desktop workspace. */
public final class ImageFlowTheme {
    public static final Color BACKGROUND = new Color(24, 29, 34);
    public static final Color PANEL = new Color(33, 41, 47);
    public static final Color CARD = new Color(42, 51, 58);
    public static final Color INPUT = new Color(25, 32, 37);
    public static final Color BORDER = new Color(65, 78, 87);
    public static final Color TEXT = new Color(240, 243, 246);
    public static final Color MUTED = new Color(167, 181, 190);
    public static final Color ACCENT = new Color(18, 199, 199);
    public static final Color SUCCESS = new Color(113, 226, 141);
    public static final Color ERROR = new Color(255, 111, 111);
    private ImageFlowTheme() { }

    public static void install() {
        try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); }
        catch (Exception e) { throw new IllegalStateException(e); }
        Font font = new Font("Segoe UI", Font.PLAIN, 14);
        for (Object key : java.util.Collections.list(UIManager.getDefaults().keys())) {
            if (key.toString().endsWith(".font")) UIManager.put(key, new javax.swing.plaf.FontUIResource(font));
        }
        for (String key : new String[]{"Panel.background", "OptionPane.background", "Viewport.background", "ScrollPane.background"}) UIManager.put(key, PANEL);
        for (String key : new String[]{"Label.foreground", "OptionPane.messageForeground", "CheckBox.foreground", "TextField.foreground", "Tree.textForeground", "Table.foreground", "Button.foreground"}) UIManager.put(key, TEXT);
        UIManager.put("TextField.background", INPUT);
        UIManager.put("TextField.caretForeground", ACCENT);
        UIManager.put("TextField.selectionBackground", new Color(10, 104, 110));
        UIManager.put("TextField.border", BorderFactory.createCompoundBorder(new RoundedBorder(BORDER, 8), BorderFactory.createEmptyBorder(7, 10, 7, 10)));
        UIManager.put("Tree.background", INPUT);
        UIManager.put("Tree.selectionBackground", new Color(12, 89, 95));
        UIManager.put("Tree.selectionForeground", TEXT);
        UIManager.put("Table.background", PANEL);
        UIManager.put("Table.gridColor", BORDER);
        UIManager.put("Table.selectionBackground", new Color(12, 89, 95));
        UIManager.put("Table.selectionForeground", TEXT);
        UIManager.put("TableHeader.background", INPUT);
        UIManager.put("TableHeader.foreground", MUTED);
        UIManager.put("ProgressBar.foreground", ACCENT);
        UIManager.put("ProgressBar.background", BORDER);
        UIManager.put("ProgressBar.border", BorderFactory.createEmptyBorder());
        UIManager.put("ScrollPane.border", new RoundedBorder(BORDER, 8));
        UIManager.put("Separator.foreground", BORDER);
        UIManager.put("Separator.background", PANEL);
        UIManager.put("TableHeader.cellBorder", BorderFactory.createEmptyBorder(5, 10, 5, 10));
        UIManager.put("ScrollBar.thumb", BORDER);
        UIManager.put("ScrollBar.track", INPUT);
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("ScrollBarUI", DarkScrollBarUI.class.getName());
        UIManager.put("ToolTip.background", CARD);
        UIManager.put("ToolTip.foreground", TEXT);
    }

    private static Shape outline(double x, double y, double width, double height, boolean tab, int radius) {
        if (!tab) return new java.awt.geom.RoundRectangle2D.Double(x, y, width, height, radius, radius);
        java.awt.geom.Path2D shape = new java.awt.geom.Path2D.Double();
        double r = radius / 2.0;
        shape.moveTo(x, y + height); shape.lineTo(x, y + r);
        shape.quadTo(x, y, x + r, y); shape.lineTo(x + width - r, y);
        shape.quadTo(x + width, y, x + width, y + r);
        shape.lineTo(x + width, y + height); shape.closePath();
        return shape;
    }

    public static JPanel surface(Color color, int padding) {
        JPanel panel = new JPanel() {
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(color);
                g.fill(outline(1, 1, getWidth()-2, getHeight()-2, false, 12));
                g.dispose();
            }
        };
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createCompoundBorder(new RoundedBorder(BORDER, 12),
                BorderFactory.createEmptyBorder(padding-2, padding-2, padding-2, padding-2)));
        return panel;
    }

    public static JButton button(String text, boolean primary) {
        JButton button = new JButton(text);
        button.putClientProperty("primary", primary);
        button.setOpaque(false); button.setContentAreaFilled(false);
        button.setHorizontalAlignment(SwingConstants.CENTER); button.setVerticalAlignment(SwingConstants.CENTER);
        button.setBorder(BorderFactory.createCompoundBorder(new ButtonBorder(), BorderFactory.createEmptyBorder(8, 16, 8, 16)));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setForeground(TEXT); button.setIconTextGap(10);
        button.setFont(button.getFont().deriveFont(Font.BOLD));
        button.setUI(new BasicButtonUI() {
            public Dimension getPreferredSize(JComponent component) {
                Dimension size = super.getPreferredSize(component);
                size.height = Math.max(38, size.height); return size;
            }
            public void paint(Graphics graphics, JComponent component) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                boolean emphasized = Boolean.TRUE.equals(button.getClientProperty("primary"));
                boolean tab = Boolean.TRUE.equals(button.getClientProperty("tab"));
                Color top = emphasized ? new Color(0, 112, 119) : CARD;
                Color bottom = emphasized ? new Color(0, 75, 82) : PANEL;
                if (button.getModel().isRollover()) top = top.brighter();
                if (button.getModel().isPressed()) top = bottom;
                if (!button.isEnabled()) { top = PANEL; bottom = INPUT; }
                g.setPaint(new GradientPaint(0, 0, top, 0, component.getHeight(), bottom));
                g.fill(outline(1, 1, component.getWidth()-2, component.getHeight()-2, tab, 8));
                // Lay out the complete icon/text group in a symmetric content rectangle.
                Insets insets = button.getInsets();
                Rectangle view = new Rectangle(insets.left, insets.top,
                        component.getWidth()-insets.left-insets.right, component.getHeight()-insets.top-insets.bottom);
                Rectangle iconBounds = new Rectangle(), textBounds = new Rectangle();
                g.setFont(button.getFont()); FontMetrics metrics = g.getFontMetrics();
                String label = SwingUtilities.layoutCompoundLabel(component, metrics, button.getText(), button.getIcon(),
                        SwingConstants.CENTER, SwingConstants.CENTER, SwingConstants.CENTER, SwingConstants.RIGHT,
                        view, iconBounds, textBounds, button.getIconTextGap());
                if (button.getIcon() != null) button.getIcon().paintIcon(button, g, iconBounds.x, iconBounds.y);
                g.setColor(button.isEnabled() ? button.getForeground() : MUTED);
                // Font ascent/descent includes invisible space above the actual letters.
                // Center the ink bounds so the visible label and icon share a centerline.
                java.awt.geom.Rectangle2D ink = button.getFont()
                        .createGlyphVector(g.getFontRenderContext(), label).getVisualBounds();
                float baseline = (float)(view.getCenterY() - ink.getCenterY());
                g.drawString(label, textBounds.x, baseline);
                g.dispose();
            }
        });
        return button;
    }

    public static JButton tab(String text, boolean selected, boolean downloader) {
        JButton tab = button(text, selected);
        tab.putClientProperty("tab", true);
        tab.setPreferredSize(new Dimension(downloader ? 172 : 132, 42));
        tab.setIcon(new Icon() {
            public int getIconWidth() { return 20; } public int getIconHeight() { return 20; }
            public void paintIcon(Component c, Graphics graphics, int x, int y) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(TEXT); g.setStroke(new BasicStroke(1.5f));
                if (downloader) {
                    g.drawLine(x+10,y+2,x+10,y+13); g.drawLine(x+5,y+9,x+10,y+14);
                    g.drawLine(x+15,y+9,x+10,y+14); g.drawLine(x+3,y+18,x+17,y+18);
                } else {
                    g.drawOval(x+7,y+1,6,6); g.drawLine(x+10,y+7,x+10,y+11);
                    g.drawLine(x+4,y+11,x+16,y+11); g.drawLine(x+4,y+11,x+4,y+14); g.drawLine(x+16,y+11,x+16,y+14);
                    g.drawRoundRect(x+1,y+14,6,5,2,2); g.drawRoundRect(x+13,y+14,6,5,2,2);
                }
                g.dispose();
            }
        });
        return tab;
    }

    private static final class ButtonBorder extends AbstractBorder {
        public Insets getBorderInsets(Component c) { return new Insets(1, 1, 1, 1); }
        public Insets getBorderInsets(Component c, Insets insets) { insets.set(1,1,1,1); return insets; }
        public void paintBorder(Component c, Graphics graphics, int x, int y, int w, int h) {
            JComponent component = (JComponent)c;
            Graphics2D g = (Graphics2D)graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setStroke(new BasicStroke(1f));
            g.setColor(Boolean.TRUE.equals(component.getClientProperty("primary")) || c.hasFocus() ? ACCENT : BORDER);
            g.draw(outline(x+1, y+1, w-2, h-2, Boolean.TRUE.equals(component.getClientProperty("tab")), 8));
            g.dispose();
        }
    }

    public static final class DarkScrollBarUI extends javax.swing.plaf.basic.BasicScrollBarUI {
        public static javax.swing.plaf.ComponentUI createUI(JComponent c) { return new DarkScrollBarUI(); }
        protected void configureScrollBarColors() { thumbColor = BORDER; trackColor = INPUT; }
        protected JButton createDecreaseButton(int orientation) { return emptyButton(); }
        protected JButton createIncreaseButton(int orientation) { return emptyButton(); }
        private JButton emptyButton() { JButton b = new JButton(); b.setPreferredSize(new Dimension(0, 0)); return b; }
        protected void paintTrack(Graphics g, JComponent c, Rectangle r) { g.setColor(INPUT); g.fillRect(r.x, r.y, r.width, r.height); }
        protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            g.setColor(BORDER); g.fillRoundRect(r.x+2, r.y+2, Math.max(2,r.width-4), Math.max(2,r.height-4), 6, 6);
        }
    }

    public static final class RoundedBorder extends AbstractBorder {
        private final Color color; private final int radius;
        public RoundedBorder(Color color, int radius) { this.color = color; this.radius = radius; }
        public Insets getBorderInsets(Component c) { return new Insets(2, 2, 2, 2); }
        public Insets getBorderInsets(Component c, Insets insets) { insets.set(2, 2, 2, 2); return insets; }
        public void paintBorder(Component c, Graphics graphics, int x, int y, int w, int h) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setStroke(new BasicStroke(1f));
            g.setColor(c.hasFocus() ? ACCENT : color);
            g.draw(outline(x+1, y+1, w-2, h-2, false, radius)); g.dispose();
        }
    }
}
