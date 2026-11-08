package com.imagesorter.ui;

import com.imagesorter.service.TagCatalog;
import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import static com.imagesorter.ui.ImageFlowTheme.*;

/** Dropdown of known tags with removable selections; no free-text rule tags. */
final class TagPicker extends JPanel {
    private final Set<String> selected = new LinkedHashSet<>();
    private final JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4)) {
        public Dimension getPreferredSize() {
            int available = getWidth();
            if (available <= 0) available = TagPicker.this.getWidth();
            if (available <= 0) available = 360;
            int x = 6, rowHeight = 0, height = 4;
            for (Component chip : getComponents()) {
                Dimension size = chip.getPreferredSize();
                if (x > 6 && x + size.width + 6 > available) {
                    height += rowHeight + 4; x = 6; rowHeight = 0;
                }
                x += size.width + 6; rowHeight = Math.max(rowHeight, size.height);
            }
            return new Dimension(1, height + rowHeight + 4);
        }
        public void setBounds(int x, int y, int width, int height) {
            boolean resized = width != getWidth();
            super.setBounds(x, y, width, height);
            if (resized) revalidate();
        }
    };
    private final Runnable changed;
    TagPicker(TagCatalog catalog, Collection<String> initial, Runnable changed) {
        this.changed = changed;
        setOpaque(false); setLayout(new BorderLayout(0, 8)); chips.setOpaque(false);
        List<String> known = catalog.entries().stream().map(TagCatalog.Entry::tag).distinct().toList();
        known.stream().filter(initial::contains).forEach(selected::add);
        JComboBox<String> dropdown = new JComboBox<>(); dropdown.addItem("Select a tag...");
        known.forEach(dropdown::addItem);
        dropdown.setUI(new javax.swing.plaf.basic.BasicComboBoxUI() {
            public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean focus) {
                g.setColor(INPUT); g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
            }
            public void paintCurrentValue(Graphics g, Rectangle bounds, boolean focus) {
                JLabel label = new JLabel(String.valueOf(comboBox.getSelectedItem()));
                label.setFont(comboBox.getFont()); label.setForeground(TEXT); label.setBackground(INPUT); label.setOpaque(true);
                label.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
                currentValuePane.paintComponent(g, label, comboBox, bounds.x, bounds.y, bounds.width, bounds.height);
            }
            protected JButton createArrowButton() {
                JButton arrow = new JButton() {
                    protected void paintComponent(Graphics graphics) {
                        Graphics2D g = (Graphics2D)graphics.create();
                        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g.setColor(isEnabled() ? TEXT : MUTED);
                        int x = getWidth()/2, y = getHeight()/2;
                        g.setStroke(new BasicStroke(1.5f));
                        g.drawLine(x-4, y-2, x, y+2); g.drawLine(x, y+2, x+4, y-2); g.dispose();
                    }
                };
                arrow.setOpaque(false); arrow.setContentAreaFilled(false);
                arrow.setBorder(BorderFactory.createEmptyBorder()); arrow.setPreferredSize(new Dimension(28, 28));
                return arrow;
            }
        });
        dropdown.setRenderer(new DefaultListCellRenderer() {
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                setBackground(selected ? new Color(12, 89, 95) : INPUT); setForeground(TEXT);
                setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8)); return this;
            }
        });
        dropdown.setBorder(new RoundedBorder(BORDER, 8));
        dropdown.setMaximumRowCount(12); dropdown.setBackground(INPUT); dropdown.setForeground(TEXT);
        dropdown.setToolTipText("Choose one or more tags from data/tags.csv");
        dropdown.addActionListener(event -> {
            if (dropdown.getSelectedIndex() <= 0) return;
            selected.add((String)dropdown.getSelectedItem()); dropdown.setSelectedIndex(0); refresh();
        });
        add(dropdown, BorderLayout.NORTH); add(chips, BorderLayout.CENTER); refresh();
    }
    Set<String> selected() { return Set.copyOf(selected); }
    private void refresh() {
        chips.removeAll();
        for (String tag : new ArrayList<>(selected)) {
            JButton chip = button(tag + "  \u00d7", false); chip.setForeground(ACCENT);
            chip.setFont(chip.getFont().deriveFont(Font.PLAIN, 12f));
            chip.setBorder(BorderFactory.createCompoundBorder(new RoundedBorder(BORDER, 8),
                    BorderFactory.createEmptyBorder(4, 9, 4, 9)));
            chip.setPreferredSize(new Dimension(chip.getFontMetrics(chip.getFont()).stringWidth(chip.getText()) + 22, 26));
            chip.setToolTipText("Remove " + tag);
            chip.addActionListener(event -> { selected.remove(tag); refresh(); }); chips.add(chip);
        }
        chips.setVisible(!selected.isEmpty()); revalidate(); repaint(); changed.run();
    }
}
