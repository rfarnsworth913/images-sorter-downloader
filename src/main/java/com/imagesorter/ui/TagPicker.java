package com.imagesorter.ui;

import com.imagesorter.service.TagCatalog;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.event.*;
import static com.imagesorter.ui.ImageFlowTheme.*;

/** Inline removable tags with a searchable, creatable dropdown. */
final class TagPicker extends JPanel {
    private final Set<String> selected = new LinkedHashSet<>();
    private final TokenFlow tokens = new TokenFlow();
    private final JTextField input = new JTextField();
    private final TagSuggestions suggestions;
    private final Runnable changed;
    TagPicker(TagCatalog catalog, Collection<String> initial, Runnable changed) {
        this.changed = changed; selected.addAll(initial);
        setLayout(new BorderLayout(2,0)); setBackground(INPUT);
        setBorder(new RoundedBorder(BORDER, 10) {
            public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
                Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                new RoundedBorder(focus != null && SwingUtilities.isDescendingFrom(focus, TagPicker.this) ? ACCENT : BORDER, 10).paintBorder(c,g,x,y,w,h);
            }
        });
        input.setBorder(BorderFactory.createEmptyBorder(3,3,3,3)); input.setOpaque(false);
        input.setToolTipText("Choose an existing tag or type a new one"); input.getAccessibleContext().setAccessibleName("Search or create tags");
        suggestions = new TagSuggestions(this, input,
                () -> catalog.knownTags().stream().filter(t -> selected.stream().noneMatch(s -> s.equalsIgnoreCase(t))).toList(),
                input::getText, text -> TagCatalog.validCustom(text) && catalog.knownTags().stream().noneMatch(t -> t.equalsIgnoreCase(text)), choice -> {
                    try {
                        String tag = choice.create() ? catalog.create(choice.tag()) : choice.tag();
                        if (selected.stream().noneMatch(t -> t.equalsIgnoreCase(tag))) selected.add(tag);
                        input.setText(""); rebuild();
                    } catch (Exception failure) { JOptionPane.showMessageDialog(this, failure.getMessage(), "Unable to create tag", JOptionPane.ERROR_MESSAGE); }
                });
        JButton arrow = dropdownButton(); arrow.addActionListener(e -> suggestions.toggle());
        add(tokens, BorderLayout.CENTER); add(arrow, BorderLayout.EAST);
        input.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { resizeInput(); } public void removeUpdate(DocumentEvent e) { resizeInput(); } public void changedUpdate(DocumentEvent e) { }
        });
        input.getInputMap().put(KeyStroke.getKeyStroke("BACK_SPACE"), "tag-backspace");
        Action delete = input.getActionMap().get("delete-previous");
        input.getActionMap().put("tag-backspace", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (input.getText().isEmpty() && !selected.isEmpty()) { selected.remove(new ArrayList<>(selected).get(selected.size()-1)); rebuild(); }
                else if (delete != null) delete.actionPerformed(e);
            }
        });
        rebuild();
    }
    static JButton dropdownButton() {
        JButton arrow = new JButton() {
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D)graphics.create(); g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(TEXT); g.setStroke(new BasicStroke(1.8f)); int x=getWidth()/2,y=getHeight()/2;
                g.drawLine(x-5,y-2,x,y+3); g.drawLine(x,y+3,x+5,y-2); g.dispose();
            }
        };
        arrow.setPreferredSize(new Dimension(30,32)); arrow.setBorder(BorderFactory.createEmptyBorder());
        arrow.setContentAreaFilled(false); arrow.setOpaque(false); arrow.setFocusable(false);
        arrow.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); arrow.setToolTipText("Open tag suggestions");
        arrow.getAccessibleContext().setAccessibleName("Open tag suggestions"); return arrow;
    }
    Set<String> selected() { return Collections.unmodifiableSet(new LinkedHashSet<>(selected)); }
    private void resizeInput() {
        input.setPreferredSize(new Dimension(Math.min(200, Math.max(86, input.getFontMetrics(input.getFont()).stringWidth(input.getText())+20)), 28));
        tokens.revalidate(); revalidate();
    }
    private void rebuild() {
        tokens.removeAll();
        for (String tag : new ArrayList<>(selected)) tokens.add(new TokenChip(tag, () -> {
            selected.remove(tag); suggestions.hide(); rebuild(); input.requestFocusInWindow();
        }));
        tokens.add(input); resizeInput(); revalidate(); repaint(); changed.run();
    }
}
