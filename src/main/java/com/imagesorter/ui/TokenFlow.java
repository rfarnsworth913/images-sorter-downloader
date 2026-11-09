package com.imagesorter.ui;

import java.awt.*;
import javax.swing.*;

/** Natural-width tokens wrap instead of stretching into a grid. */
final class TokenFlow extends JPanel {
    TokenFlow() { super(new FlowLayout(FlowLayout.LEFT, 5, 3)); setOpaque(false); }
    public Dimension getPreferredSize() {
        int width = getWidth() > 0 ? getWidth() : getParent() != null && getParent().getWidth() > 0 ? getParent().getWidth()-36 : 360;
        int x = 5, rowHeight = 0, height = 3;
        for (Component child : getComponents()) {
            Dimension size = child.getPreferredSize();
            if (x > 5 && x + size.width + 5 > width) { height += rowHeight + 3; x = 5; rowHeight = 0; }
            x += size.width + 5; rowHeight = Math.max(rowHeight, size.height);
        }
        return new Dimension(80, height + rowHeight + 3);
    }
    public void setBounds(int x, int y, int w, int h) { boolean resize = w != getWidth(); super.setBounds(x,y,w,h); if (resize) revalidate(); }
}
