package com.imagesorter.ui;

import java.awt.*;
import javax.swing.*;
import static com.imagesorter.ui.ImageFlowTheme.*;

/** Compact inline token: only its remove button deletes the token. */
final class TokenChip extends JPanel {
    TokenChip(String text, Runnable remove) {
        setLayout(new BorderLayout(6, 0)); setOpaque(false);
        setBorder(BorderFactory.createCompoundBorder(new RoundedBorder(new Color(37, 142, 146), 10), BorderFactory.createEmptyBorder(2, 7, 2, 3)));
        JLabel label = new JLabel(text); label.setForeground(TEXT); label.setFont(label.getFont().deriveFont(Font.BOLD, 12f)); add(label, BorderLayout.CENTER);
        JButton close = new JButton("\u00d7"); close.setUI(new javax.swing.plaf.basic.BasicButtonUI());
        close.setForeground(TEXT); close.setFont(close.getFont().deriveFont(Font.PLAIN, 17f));
        close.setBorder(BorderFactory.createEmptyBorder()); close.setContentAreaFilled(false); close.setOpaque(false);
        close.setPreferredSize(new Dimension(18, 20)); close.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        close.setToolTipText("Remove " + text); close.getAccessibleContext().setAccessibleName("Remove " + text);
        close.addActionListener(e -> remove.run()); add(close, BorderLayout.EAST);
        setAlignmentY(0.75f);
    }
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D)graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(new GradientPaint(0,0,new Color(0,112,117),0,getHeight(),new Color(0,74,81)));
        g.fillRoundRect(1,1,getWidth()-2,getHeight()-2,10,10); g.dispose();
    }
}
