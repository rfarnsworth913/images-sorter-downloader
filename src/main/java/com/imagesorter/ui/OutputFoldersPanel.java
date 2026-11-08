package com.imagesorter.ui;

import com.imagesorter.service.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.event.*;
import static com.imagesorter.ui.ImageFlowTheme.*;

/** Always keeps the default folder first; additional folders are ordered routing rules. */
final class OutputFoldersPanel extends JPanel {
    private final List<FolderRow> rows = new ArrayList<>();
    private final JPanel cards = new JPanel();
    private final TagCatalog catalog;
    private final Runnable changed;
    OutputFoldersPanel(TagCatalog catalog, JTextField defaultFolder, Runnable changed) {
        this.catalog = catalog; this.changed = changed;
        setOpaque(false); setLayout(new BorderLayout());
        JPanel surface = surface(PANEL, 16); surface.setLayout(new BorderLayout(0, 14)); add(surface);
        JPanel heading = new JPanel(new BorderLayout(10, 0)); heading.setOpaque(false);
        JLabel title = new JLabel("Output destinations"); title.setFont(title.getFont().deriveFont(Font.BOLD, 19));
        heading.add(title, BorderLayout.WEST);
        JButton add = button("+ Add folder", true);
        add.addActionListener(event -> { rows.add(new FolderRow(new JTextField(), false)); rebuild(); });
        heading.add(add, BorderLayout.EAST); surface.add(heading, BorderLayout.NORTH);
        cards.setOpaque(false); cards.setLayout(new BoxLayout(cards, BoxLayout.Y_AXIS));
        rows.add(new FolderRow(defaultFolder, true)); rebuild();
        JScrollPane scroll = new JScrollPane(cards); scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setOpaque(false); scroll.setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        surface.add(scroll, BorderLayout.CENTER);
        JLabel note = new JLabel("<html>First matching tagged folder wins (all selected tags).<br>No match uses the default folder. Files are moved.</html>");
        note.setForeground(MUTED); surface.add(note, BorderLayout.SOUTH);
    }
    List<OutputDestination> destinations() {
        List<OutputDestination> result = new ArrayList<>();
        for (FolderRow row : rows) {
            if (row.folder.getText().isBlank()) throw new IllegalArgumentException("Choose an output folder for every destination.");
            result.add(new OutputDestination(Path.of(row.folder.getText().strip()), row.tags == null ? Set.of() : row.tags.selected()));
        }
        OutputDestination.validate(result); return List.copyOf(result);
    }
    List<String> previewFolders() { return rows.stream().map(r -> r.folder.getText().isBlank() ? "Choose output folder" : r.folder.getText().strip()).toList(); }
    private void rebuild() {
        cards.removeAll();
        for (FolderRow row : rows) { cards.add(row.card); cards.add(Box.createVerticalStrut(12)); }
        cards.revalidate(); cards.repaint(); changed.run();
    }
    private final class FolderRow {
        final JTextField folder;
        final TagPicker tags;
        final JPanel card = new JPanel(new BorderLayout()) {
            public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        };
        FolderRow(JTextField folder, boolean fallback) {
            this.folder = folder;
            JPanel painted = surface(CARD, 12); painted.setLayout(new BorderLayout(0, 12));
            card.setOpaque(false); card.add(painted);
            JPanel header = new JPanel(new BorderLayout(8, 0)); header.setOpaque(false);
            JLabel name = new JLabel(fallback ? "Default folder" : "Tagged folder"); name.setFont(name.getFont().deriveFont(Font.BOLD, 16));
            header.add(name, BorderLayout.WEST);
            if (fallback) {
                JLabel badge = new JLabel("Default"); badge.setForeground(ACCENT); header.add(badge, BorderLayout.EAST);
            } else {
                JButton remove = button("Remove", false); remove.setToolTipText("Remove this destination");
                remove.addActionListener(event -> { rows.remove(this); rebuild(); }); header.add(remove, BorderLayout.EAST);
            }
            painted.add(header, BorderLayout.NORTH);
            JPanel body = new JPanel(new BorderLayout(0, 10)); body.setOpaque(false);
            if (!fallback) {
                tags = new TagPicker(catalog, Set.of(), () -> { cards.revalidate(); changed.run(); });
                JPanel match = new JPanel(new BorderLayout(8, 6)); match.setOpaque(false);
                match.add(new JLabel("Match tags (all)"), BorderLayout.NORTH); match.add(tags, BorderLayout.CENTER);
                body.add(match, BorderLayout.NORTH);
            } else {
                tags = null;
                JLabel hint = new JLabel("Images that match no tagged folder."); hint.setForeground(MUTED); body.add(hint, BorderLayout.NORTH);
            }
            JPanel pathRow = new JPanel(new BorderLayout(8, 0)); pathRow.setOpaque(false);
            folder.getAccessibleContext().setAccessibleName(fallback ? "Default output folder" : "Tagged output folder");
            pathRow.add(folder, BorderLayout.CENTER);
            JButton browse = button("Browse", false);
            browse.addActionListener(event -> {
                JFileChooser chooser = new JFileChooser(); chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                if (chooser.showOpenDialog(OutputFoldersPanel.this) == JFileChooser.APPROVE_OPTION) folder.setText(chooser.getSelectedFile().getAbsolutePath());
            }); pathRow.add(browse, BorderLayout.EAST); body.add(pathRow, BorderLayout.SOUTH); painted.add(body, BorderLayout.CENTER);
            folder.getDocument().addDocumentListener(new DocumentListener() {
                public void insertUpdate(DocumentEvent e) { changed.run(); }
                public void removeUpdate(DocumentEvent e) { changed.run(); }
                public void changedUpdate(DocumentEvent e) { changed.run(); }
            });
            card.setAlignmentX(Component.LEFT_ALIGNMENT);
        }
    }
}
