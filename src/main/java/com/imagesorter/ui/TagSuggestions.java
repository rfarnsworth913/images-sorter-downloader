package com.imagesorter.ui;

import com.imagesorter.service.TagCatalog;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.function.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.JTextComponent;
import static com.imagesorter.ui.ImageFlowTheme.*;

/** Non-focus-stealing suggestions. Keyboard and mouse activate the same highlighted row. */
final class TagSuggestions {
    record Choice(String tag, boolean create) { }
    private final JComponent anchor;
    private final JTextComponent input;
    private final Supplier<List<String>> known;
    private final Supplier<String> query;
    private final Predicate<String> canCreate;
    private final Consumer<Choice> activate;
    private final DefaultListModel<Choice> model = new DefaultListModel<>();
    private final JList<Choice> list = new JList<>(model);
    private final JPanel content = surface(PANEL, 8);
    private final JLabel heading = new JLabel("EXISTING TAGS");
    private Popup popup;
    private boolean changing;
    private final AWTEventListener outside = this::outsideEvent;
    private void outsideEvent(AWTEvent event) {
        if (event instanceof MouseEvent mouse && mouse.getID() == MouseEvent.MOUSE_PRESSED
                && mouse.getSource() instanceof Component c && !SwingUtilities.isDescendingFrom(c, anchor)
                && !SwingUtilities.isDescendingFrom(c, content)) hide();
        if (event instanceof WindowEvent window && window.getID() == WindowEvent.WINDOW_DEACTIVATED) hide();
    }
    TagSuggestions(JComponent anchor, JTextComponent input, Supplier<List<String>> known, Supplier<String> query,
                   Predicate<String> canCreate, Consumer<Choice> activate) {
        this.anchor = anchor; this.input = input; this.known = known; this.query = query; this.canCreate = canCreate; this.activate = activate;
        content.setLayout(new BorderLayout(0, 6)); heading.setForeground(MUTED);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 11f)); content.add(heading, BorderLayout.NORTH);
        list.setFocusable(false); list.setBackground(PANEL); list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer((items, value, index, selected, focus) -> {
            JPanel row = new JPanel(new BorderLayout(12, 0)); row.setBackground(selected ? new Color(0, 86, 92) : PANEL);
            row.setBorder(BorderFactory.createCompoundBorder(new RoundedBorder(selected ? ACCENT : PANEL, 7),
                    BorderFactory.createEmptyBorder(8, 10, 8, 10)));
            JLabel label = new JLabel(value.create() ? "+ Create \u201c" + value.tag() + "\u201d" : value.tag());
            label.setForeground(value.create() ? ACCENT : TEXT); row.add(label, BorderLayout.CENTER);
            if (selected) { JLabel hint = new JLabel("Enter"); hint.setForeground(TEXT); hint.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 2)); row.add(hint, BorderLayout.EAST); }
            if (value.create() && index > 0) row.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1,0,0,0,BORDER), row.getBorder()));
            return row;
        });
        JScrollPane scroll = new JScrollPane(list); scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER); content.add(scroll, BorderLayout.CENTER);
        list.addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseMoved(MouseEvent event) { int index = list.locationToIndex(event.getPoint()); if (index >= 0 && list.getCellBounds(index,index).contains(event.getPoint())) list.setSelectedIndex(index); }
        });
        list.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent event) {
                int index = list.locationToIndex(event.getPoint());
                if (index >= 0 && list.getCellBounds(index,index).contains(event.getPoint())) { list.setSelectedIndex(index); choose(); }
            }
        });
        input.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { update(); } public void removeUpdate(DocumentEvent e) { update(); } public void changedUpdate(DocumentEvent e) { }
            private void update() { if (!changing) SwingUtilities.invokeLater(() -> { if (input.hasFocus() && !changing) { if (!query.get().isBlank() || popup != null) show(); } }); }
        });
        bind("DOWN", () -> move(1)); bind("UP", () -> move(-1));
        bind("ENTER", () -> { if (popup != null) choose(); else if (!query.get().isBlank()) show(); });
        bind("ESCAPE", this::hide);
        input.addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent event) { anchor.repaint(); }
            public void focusLost(FocusEvent event) { anchor.repaint(); SwingUtilities.invokeLater(() -> { if (!input.hasFocus()) hide(); }); }
        });
        anchor.addHierarchyListener(event -> { if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && !anchor.isShowing()) hide(); });
        anchor.addComponentListener(new ComponentAdapter() { public void componentMoved(ComponentEvent e) { hide(); } public void componentResized(ComponentEvent e) { hide(); } });
    }
    private void bind(String key, Runnable action) {
        String id = "suggestions-" + key;
        input.getInputMap().put(KeyStroke.getKeyStroke(key), id);
        input.getActionMap().put(id, new AbstractAction() { public void actionPerformed(ActionEvent e) { action.run(); } });
    }
    void edit(Runnable mutation) { changing = true; try { mutation.run(); } finally { changing = false; } hide(); }
    void toggle() { input.requestFocusInWindow(); if (popup != null) hide(); else show(); }
    void refreshChoices() {
        Choice previous = list.getSelectedValue(); String text = query.get().strip();
        model.clear(); List<String> all = known.get();
        for (String tag : all) if (tag.toLowerCase(Locale.ROOT).contains(text.toLowerCase(Locale.ROOT))) model.addElement(new Choice(tag, false));
        if (!text.isEmpty() && canCreate.test(text)) model.addElement(new Choice(text, true));
        heading.setText(model.isEmpty() ? "NO MATCHING TAGS" : "EXISTING TAGS");
        int index = previous == null ? -1 : model.indexOf(previous);
        if (!model.isEmpty()) list.setSelectedIndex(index < 0 ? 0 : index);
    }
    void show() {
        refreshChoices();
        if (!anchor.isShowing()) return;
        hidePopup();
        int width = Math.max(260, anchor.getWidth()); int height = 38 + Math.max(1, Math.min(7, model.size())) * 40;
        content.setPreferredSize(new Dimension(width, height));
        Point point = anchor.getLocationOnScreen();
        Rectangle screen = anchor.getGraphicsConfiguration().getBounds();
        int y = point.y + anchor.getHeight() + 4;
        if (y + height > screen.y + screen.height) y = Math.max(screen.y, point.y - height - 4);
        int x = Math.max(screen.x, Math.min(point.x, screen.x + screen.width - width));
        popup = PopupFactory.getSharedInstance().getPopup(anchor, content, x, y); popup.show();
        Toolkit.getDefaultToolkit().addAWTEventListener(outside, AWTEvent.MOUSE_EVENT_MASK | AWTEvent.WINDOW_EVENT_MASK);
    }
    private void move(int direction) {
        if (popup == null) { show(); if (direction < 0 && !model.isEmpty()) list.setSelectedIndex(model.size()-1); return; }
        list.setSelectedIndex(Math.max(0, Math.min(model.size()-1, list.getSelectedIndex()+direction)));
        if (list.getSelectedIndex() >= 0) list.ensureIndexIsVisible(list.getSelectedIndex());
    }
    void choose() {
        Choice choice = list.getSelectedValue(); if (choice == null) return;
        hide(); changing = true;
        try { activate.accept(choice); } finally { changing = false; }
        input.requestFocusInWindow();
    }
    void hide() { hidePopup(); }
    private void hidePopup() {
        if (popup != null) { popup.hide(); popup = null; Toolkit.getDefaultToolkit().removeAWTEventListener(outside); }
    }
}
