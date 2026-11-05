package com.imagesorter.ui;

import com.imagesorter.service.*;
import java.awt.*;
import java.nio.file.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;
import javax.swing.tree.*;
import static com.imagesorter.ui.ImageFlowTheme.*;

public final class MainWindow extends JFrame {
    private final JLabel status = new JLabel("Ready to organize your images");
    private final JLabel counters = new JLabel("0 completed     /     0 skipped     /     0 errors");
    private final JProgressBar progress = new JProgressBar(0, 100);
    private final JLabel percent = new JLabel("0%");
    private final JButton sort = button("Start sorting", true);
    private final JButton download = button("Download images", true);
    private final JButton previewButton = button("Preview changes", false);
    private final DefaultTableModel activity = new DefaultTableModel(new String[]{"Time", "Image / activity", "Destination / details", "Status"}, 0) {
        public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable activityTable = new JTable(activity);
    private final JTextField source = new JTextField();
    private final JTextField destination = new JTextField();
    private final JTextField pattern = new JTextField("{{series}}\\{{character}}\\{{artist}}\\{{filename}}");
    private final JTextField endpoint = new JTextField("http://127.0.0.1:8188");
    private final JTextField workflow = new JTextField();
    private final JTextField outputNode = new JTextField();
    private final JTree previewTree = new JTree();
    private final JLabel previewNote = muted("Illustrative tags; original extensions are preserved.");
    private final Map<String, String> samples = Map.of("artist", "Artist name", "album", "Album", "character", "Character", "series", "Series", "content", "Portrait");
    private final JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
    private final CardLayout actionLayout = new CardLayout();
    private final JPanel primaryAction = new JPanel(actionLayout);
    private List<Path> urlLists = List.of();
    private int completed, skipped, errors;

    public MainWindow() {
        super("ImageFlow");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setBackground(BACKGROUND);
        root.setBorder(BorderFactory.createEmptyBorder(0, 18, 18, 18));
        CardLayout pages = new CardLayout();
        JPanel workspace = new JPanel(pages); workspace.setOpaque(false);
        workspace.add(sortPanel(), "Sorter"); workspace.add(downloadPanel(), "Downloader");
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 0));
        header.setBackground(BACKGROUND);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER),
                BorderFactory.createEmptyBorder(6, 0, 0, 0)));
        JLabel brand = title("ImageFlow", 24); brand.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 30));
        brand.setIcon(new Icon() {
            public int getIconWidth() { return 34; } public int getIconHeight() { return 34; }
            public void paintIcon(Component c, Graphics graphics, int x, int y) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(ACCENT); g.setStroke(new BasicStroke(2)); g.drawRoundRect(x+2,y+3,27,27,5,5);
                g.drawOval(x+8,y+9,4,4); g.drawPolyline(new int[]{x+5,x+12,x+18,x+26}, new int[]{y+26,y+18,y+23,y+14},4); g.dispose();
            }
        }); brand.setIconTextGap(10);
        header.add(brand);
        JButton sorterTab = tab("Sorter", true, false), downloaderTab = tab("Downloader", false, true);
        header.add(sorterTab); header.add(downloaderTab);
        JLabel mode = muted("LOCAL IMAGE WORKSPACE"); mode.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 0)); header.add(mode);
        sorterTab.addActionListener(event -> { pages.show(workspace, "Sorter"); actionLayout.show(primaryAction, "Sorter"); previewButton.setVisible(true); selectTab(sorterTab, downloaderTab); });
        downloaderTab.addActionListener(event -> { pages.show(workspace, "Downloader"); actionLayout.show(primaryAction, "Downloader"); previewButton.setVisible(false); selectTab(downloaderTab, sorterTab); });
        root.add(header, BorderLayout.NORTH); root.add(workspace, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout(0, 18)); bottom.setOpaque(false);
        bottom.add(activityPanel(), BorderLayout.CENTER);
        actions.setOpaque(false); primaryAction.setOpaque(false);
        primaryAction.add(sort, "Sorter"); primaryAction.add(download, "Downloader");
        actions.add(previewButton); actions.add(primaryAction);
        bottom.add(actions, BorderLayout.SOUTH); root.add(bottom, BorderLayout.SOUTH);
        previewButton.addActionListener(event -> {
            try { JOptionPane.showMessageDialog(this, new JLabel(previewPath()), "Sample output path", JOptionPane.INFORMATION_MESSAGE); }
            catch (IllegalArgumentException failure) { showError(failure); }
        });
        setContentPane(root);
        setMinimumSize(new Dimension(1050, 820));
        Rectangle usable = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setSize(Math.min(1380, usable.width), Math.min(1140, usable.height)); setLocationRelativeTo(null);
    }

    private void selectTab(JButton selected, JButton other) {
        selected.putClientProperty("primary", true); other.putClientProperty("primary", false);
        selected.getAccessibleContext().setAccessibleDescription("Selected tab");
        other.getAccessibleContext().setAccessibleDescription("Inactive tab");
        selected.repaint(); other.repaint();
    }

    private JPanel sortPanel() {
        JPanel page = new JPanel(new BorderLayout(0, 14)); page.setOpaque(false);
        JPanel top = new JPanel(new BorderLayout(0, 14)); top.setOpaque(false);
        top.add(pageHeading("Image sorter", "Organize your images with tags and a custom folder structure."), BorderLayout.NORTH);
        JPanel sourceBar = surface(PANEL, 12); sourceBar.setLayout(new BorderLayout(18, 0));
        sourceBar.add(folder("Source folder", source), BorderLayout.CENTER);
        sourceBar.add(muted("Immediate image files only"), BorderLayout.EAST); top.add(sourceBar, BorderLayout.SOUTH);
        page.add(top, BorderLayout.NORTH);
        JPanel columns = new JPanel(new GridLayout(1, 2, 14, 0)); columns.setOpaque(false);
        columns.add(destinationPanel()); columns.add(patternPanel());
        columns.setPreferredSize(new Dimension(980, 510));
        JScrollPane columnScroll = new JScrollPane(columns);
        columnScroll.setBorder(BorderFactory.createEmptyBorder());
        columnScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        columnScroll.getVerticalScrollBar().setUnitIncrement(20);
        page.add(columnScroll, BorderLayout.CENTER);
        sort.addActionListener(event -> startSort());
        listen(pattern, this::refreshPreview); listen(destination, this::refreshPreview); refreshPreview();
        return page;
    }

    private JPanel destinationPanel() {
        JPanel panel = surface(PANEL, 16); panel.setLayout(new BorderLayout(0, 16));
        panel.add(title("Output destination", 19), BorderLayout.NORTH);
        JPanel body = vertical();
        JPanel card = surface(CARD, 14); card.setLayout(new BorderLayout(0, 12));
        JPanel cardHead = new JPanel(new BorderLayout()); cardHead.setOpaque(false);
        cardHead.add(title("01    Image collection", 17), BorderLayout.WEST);
        JLabel badge = new JLabel("  ALL IMAGES  "); badge.setForeground(ACCENT); badge.setFont(badge.getFont().deriveFont(Font.BOLD, 11));
        cardHead.add(badge, BorderLayout.EAST); card.add(cardHead, BorderLayout.NORTH);
        JPanel cardBody = vertical();
        addRow(cardBody, muted("Every image is organized into this destination."));
        addRow(cardBody, folder("Output folder", destination)); card.add(cardBody, BorderLayout.CENTER);
        addRow(body, card);
        addRow(body, muted("Files are moved after classification and duplicate review."));
        addRow(body, new JSeparator());
        addRow(body, title("Image recognition", 17));
        addRow(body, row("ComfyUI URL", endpoint));
        JPanel workflowRow = row("API workflow", workflow);
        JButton browse = button("Browse", false); browse.addActionListener(event -> chooseFile(workflow));
        workflowRow.add(browse, BorderLayout.EAST); addRow(body, workflowRow);
        addRow(body, row("Text node ID", outputNode));
        addRow(body, muted("Use a local vision workflow to identify tags for each image."));
        addRow(body, muted("Missing or invalid tags open manual review."));
        panel.add(body, BorderLayout.CENTER); return panel;
    }

    private JPanel patternPanel() {
        JPanel panel = surface(PANEL, 16); panel.setLayout(new BorderLayout(0, 8));
        JPanel settings = vertical();
        addRow(settings, title("Folder & filename pattern", 17)); addRow(settings, pattern);
        addRow(settings, muted("Use \\ to create folders. The source extension is added automatically."));
        addRow(settings, new JSeparator()); addRow(settings, title("Tag library", 16));
        JPanel chips = new JPanel(new GridLayout(2, 4, 6, 6)); chips.setOpaque(false);
        for (String tag : List.of("series", "character", "artist", "content", "album", "filename", "###")) {
            JButton chip = button("{{" + tag + "}}", false); chip.setForeground(ACCENT);
            chip.setFont(chip.getFont().deriveFont(11f)); chip.setPreferredSize(new Dimension(tag.length()*7 + 30, 28));
            chip.addActionListener(event -> pattern.replaceSelection("{{" + tag + "}}")); chips.add(chip);
        }
        chips.setPreferredSize(new Dimension(420, 64)); addRow(settings, chips);
        addRow(settings, muted("Click a tag to insert it at the cursor."));
        addRow(settings, new JSeparator()); addRow(settings, title("Live preview", 16));
        panel.add(settings, BorderLayout.NORTH);
        DefaultTreeCellRenderer treeRenderer = new DefaultTreeCellRenderer();
        treeRenderer.setBackgroundNonSelectionColor(INPUT);
        treeRenderer.setBackgroundSelectionColor(new Color(12, 89, 95));
        treeRenderer.setTextNonSelectionColor(TEXT); treeRenderer.setTextSelectionColor(TEXT);
        previewTree.setCellRenderer(treeRenderer);
        previewTree.setRowHeight(24); previewTree.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JScrollPane treeScroll = new JScrollPane(previewTree); treeScroll.setPreferredSize(new Dimension(400, 120));
        panel.add(treeScroll, BorderLayout.CENTER);
        JPanel notes = vertical(); addRow(notes, previewNote);
        JPanel policies = new JPanel(new GridLayout(1, 2, 12, 0)); policies.setOpaque(false);
        policies.add(muted("Operation   ·   Move files")); policies.add(muted("Duplicates   ·   Ask each time"));
        addRow(notes, policies); panel.add(notes, BorderLayout.SOUTH); return panel;
    }

    private JPanel downloadPanel() {
        JPanel page = new JPanel(new BorderLayout(0, 16)); page.setOpaque(false);
        page.add(pageHeading("Image downloader", "Download direct image links from your URL lists."), BorderLayout.NORTH);
        JPanel panel = surface(PANEL, 18); panel.setLayout(new BorderLayout());
        JPanel fields = vertical(); addRow(fields, title("Download sources", 19));
        JTextField lists = new JTextField(); lists.setEditable(false);
        JPanel files = row("URL list files", lists); JButton choose = button("Choose files", false);
        choose.addActionListener(event -> {
            JFileChooser picker = new JFileChooser(); picker.setMultiSelectionEnabled(true);
            if (picker.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                urlLists = Arrays.stream(picker.getSelectedFiles()).map(file -> file.toPath()).toList();
                lists.setText(String.join("; ", urlLists.stream().map(Path::toString).toList()));
            }
        });
        files.add(choose, BorderLayout.EAST); addRow(fields, files);
        JTextField output = new JTextField(); addRow(fields, folder("Output folder", output));
        addRow(fields, muted("One HTTP(S) image URL per line. Blank lines and # comments are ignored."));
        addRow(fields, muted("Unique filenames  ·  25 MB per image  ·  Direct image links only"));
        panel.add(fields, BorderLayout.NORTH); page.add(panel, BorderLayout.CENTER);
        download.addActionListener(event -> {
            try {
                Path to = path(output); List<Path> inputs = List.copyOf(urlLists);
                if (inputs.isEmpty()) throw new IllegalArgumentException("Choose at least one URL list file.");
                runTask("Downloading", logger -> new ImageDownloadService().download(inputs, to, logger));
            } catch (IllegalArgumentException failure) { showError(failure); }
        });
        return page;
    }

    private JPanel activityPanel() {
        JPanel panel = surface(PANEL, 16); panel.setLayout(new BorderLayout(0, 12));
        JPanel heading = new JPanel(new BorderLayout()); heading.setOpaque(false);
        heading.add(title("Processing activity", 18), BorderLayout.WEST); counters.setForeground(MUTED); heading.add(counters, BorderLayout.EAST);
        JPanel top = new JPanel(new BorderLayout(0, 12)); top.setOpaque(false); top.add(heading, BorderLayout.NORTH);
        JPanel state = new JPanel(new BorderLayout(16, 0)); state.setOpaque(false);
        status.setPreferredSize(new Dimension(290, 22)); state.add(status, BorderLayout.WEST);
        progress.setPreferredSize(new Dimension(320, 10)); JPanel bar = new JPanel(new BorderLayout()); bar.setOpaque(false);
        bar.setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 0)); bar.add(progress); state.add(bar, BorderLayout.CENTER);
        percent.setForeground(ACCENT); state.add(percent, BorderLayout.EAST); top.add(state, BorderLayout.SOUTH); panel.add(top, BorderLayout.NORTH);
        activityTable.setRowHeight(29); activityTable.setShowVerticalLines(false); activityTable.setIntercellSpacing(new Dimension(0, 1));
        activityTable.setFillsViewportHeight(true); activityTable.getTableHeader().setReorderingAllowed(false);
        activityTable.getColumnModel().getColumn(0).setPreferredWidth(90);
        activityTable.getColumnModel().getColumn(1).setPreferredWidth(260);
        activityTable.getColumnModel().getColumn(2).setPreferredWidth(620);
        activityTable.getColumnModel().getColumn(3).setPreferredWidth(110);
        activityTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus, int row, int col) {
                super.getTableCellRendererComponent(table, value, selected, focus, row, col);
                setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
                if (!selected) { setBackground(row % 2 == 0 ? PANEL : new Color(29, 37, 43)); setForeground(col == 3 ? statusColor(value.toString()) : TEXT); }
                setToolTipText(value == null ? null : value.toString()); return this;
            }
        });
        JScrollPane scroll = new JScrollPane(activityTable); scroll.setPreferredSize(new Dimension(900, 112)); panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private void startSort() {
        try {
            Path from = path(source), to = path(destination); String template = pattern.getText();
            SortPattern.resolve(template, samples, "sample.jpg", 1);
            String url = endpoint.getText().strip(), node = outputNode.getText().strip(); Path workflowFile = path(workflow);
            runTask("Sorting", logger -> {
                ImageSortService.Tagger tagger;
                try { tagger = new ComfyTagger(url, workflowFile, node); }
                catch (Exception failure) { logger.accept("ComfyUI unavailable: " + failure.getMessage()); tagger = image -> { throw new java.io.IOException(failure.getMessage()); }; }
                return new ImageSortService().sort(from, to, template, tagger, new ImageSortService.Interaction() {
                    public Map<String, String> manualTags(Path image, Map<String, String> suggested, String reason) throws Exception { return onUi(() -> reviewTags(image, suggested, reason, template)); }
                    public ImageSortService.Decision conflict(Path incoming, Path existing, boolean duplicate) throws Exception { return onUi(() -> compareImages(incoming, existing, duplicate)); }
                }, logger, (done, total) -> SwingUtilities.invokeLater(() -> {
                    progress.setIndeterminate(false); progress.setMaximum(Math.max(1, total)); progress.setValue(done);
                    percent.setText((total == 0 ? 100 : (int)(100L * done / total)) + "%");
                    status.setText("Processing " + done + " of " + total + " images");
                }));
            });
        } catch (IllegalArgumentException failure) { showError(failure); }
    }

    private String previewPath() {
        return (destination.getText().isBlank() ? "Output folder" : destination.getText().strip()) + java.io.File.separator
                + SortPattern.resolve(pattern.getText(), samples, "sample.jpg", 1);
    }
    private void refreshPreview() {
        try {
            Path relative = SortPattern.resolve(pattern.getText(), samples, "sample.jpg", 1);
            DefaultMutableTreeNode root = new DefaultMutableTreeNode(destination.getText().isBlank() ? "Output folder" : destination.getText().strip());
            DefaultMutableTreeNode parent = root;
            for (Path segment : relative) { DefaultMutableTreeNode child = new DefaultMutableTreeNode(segment.toString()); parent.add(child); parent = child; }
            previewTree.setModel(new DefaultTreeModel(root)); for (int i=0; i<previewTree.getRowCount(); i++) previewTree.expandRow(i);
            previewNote.setText("Sample tags · sample.jpg · extension preserved"); previewNote.setForeground(MUTED); previewButton.setEnabled(true);
        } catch (IllegalArgumentException e) { previewTree.setModel(new DefaultTreeModel(new DefaultMutableTreeNode("Invalid pattern"))); previewNote.setText("Invalid pattern. Hover for details."); previewNote.setToolTipText(e.getMessage()); previewNote.setForeground(ImageFlowTheme.ERROR); previewButton.setEnabled(false); }
    }
    private static void listen(JTextField field, Runnable action) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { action.run(); } public void removeUpdate(DocumentEvent e) { action.run(); } public void changedUpdate(DocumentEvent e) { action.run(); }
        });
    }

    private <T> T onUi(java.util.concurrent.Callable<T> action) throws Exception {
        java.util.concurrent.FutureTask<T> task = new java.util.concurrent.FutureTask<>(action);
        SwingUtilities.invokeAndWait(task);
        return task.get();
    }

    private JComponent imagePreview(Path file) {
        JLabel label = new JLabel(file.getFileName().toString(), SwingConstants.CENTER);
        try {
            java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(file.toFile());
            if (image == null) label.setText("Preview unavailable: " + file.getFileName());
            else {
                double scale = Math.min(1, Math.min(320.0 / image.getWidth(), 320.0 / image.getHeight()));
                label.setIcon(new ImageIcon(image.getScaledInstance(Math.max(1, (int)(image.getWidth() * scale)),
                        Math.max(1, (int)(image.getHeight() * scale)), java.awt.Image.SCALE_SMOOTH)));
                label.setHorizontalTextPosition(SwingConstants.CENTER);
                label.setVerticalTextPosition(SwingConstants.BOTTOM);
            }
        } catch (java.io.IOException e) { label.setText("Preview unavailable: " + file.getFileName()); }
        label.setToolTipText(file.toString());
        return new JScrollPane(label);
    }

    private Map<String, String> reviewTags(Path image, Map<String, String> suggested, String reason, String pattern) {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        JPanel fieldsPanel = new JPanel(new GridLayout(0, 1, 8, 8));
        Map<String, JTextField> fields = new LinkedHashMap<>();
        for (String tag : ImageSortService.TAGS) {
            JTextField field = new JTextField(suggested.getOrDefault(tag, ""), 22);
            fields.put(tag, field); fieldsPanel.add(row(tag, field));
        }
        fieldsPanel.add(new JLabel("Multiple characters: enter group. Unknown values: enter Unknown."));
        panel.add(fieldsPanel, BorderLayout.WEST);
        panel.add(imagePreview(image), BorderLayout.EAST);
        panel.setToolTipText(reason);
        while (JOptionPane.showOptionDialog(this, panel, "Manual tags: " + image.getFileName(),
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null,
                new String[]{"Use tags", "Skip image"}, "Use tags") == 0) {
            Map<String, String> tags = new LinkedHashMap<>();
            fields.forEach((key, field) -> tags.put(key, field.getText().strip()));
            try {
                for (String key : List.of("series", "character", "content", "artist"))
                    if (tags.get(key).isBlank()) throw new IllegalArgumentException("Fill in " + key + " (or enter Unknown).");
                SortPattern.resolve(pattern, tags, image.getFileName().toString(), 1);
                return tags;
            } catch (IllegalArgumentException e) { JOptionPane.showMessageDialog(this, e.getMessage()); }
        }
        return null;
    }

    private ImageSortService.Decision compareImages(Path incoming, Path existing, boolean duplicate) {
        JPanel panel = new JPanel(new GridLayout(1, 2, 12, 0));
        JPanel left = new JPanel(new BorderLayout()); left.add(new JLabel("Incoming"), BorderLayout.NORTH);
        left.add(imagePreview(incoming), BorderLayout.CENTER);
        JPanel right = new JPanel(new BorderLayout()); right.add(new JLabel("Existing"), BorderLayout.NORTH);
        right.add(imagePreview(existing), BorderLayout.CENTER);
        panel.add(left); panel.add(right);
        int choice = JOptionPane.showOptionDialog(this, panel, duplicate ? "Duplicate image" : "Filename conflict",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null,
                new String[]{"Keep both", "Replace existing", "Skip incoming"}, "Skip incoming");
        return choice == 0 ? ImageSortService.Decision.KEEP_BOTH : choice == 1 ? ImageSortService.Decision.REPLACE : ImageSortService.Decision.SKIP;
    }

    private static JLabel title(String text, int size) {
        JLabel label = new JLabel(text); label.setFont(label.getFont().deriveFont(Font.BOLD, (float)size)); label.setForeground(TEXT); return label;
    }
    private static JLabel muted(String text) { JLabel label = new JLabel(text); label.setForeground(MUTED); return label; }
    private static JPanel vertical() { JPanel panel = new JPanel(); panel.setOpaque(false); panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS)); return panel; }
    private static void addRow(JPanel panel, JComponent component) {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, component.getPreferredSize().height)); panel.add(component); panel.add(Box.createVerticalStrut(10));
    }
    private JPanel pageHeading(String heading, String subtitle) {
        JPanel panel = new JPanel(new BorderLayout(0, 3)); panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(0, 4, 2, 4));
        panel.add(title(heading, 30), BorderLayout.NORTH); panel.add(muted(subtitle), BorderLayout.SOUTH); return panel;
    }
    private JPanel row(String name, JTextField field) {
        JPanel panel = new JPanel(new BorderLayout(10, 0)); panel.setOpaque(false);
        JLabel label = new JLabel(name); label.setPreferredSize(new Dimension(106, 30)); label.setLabelFor(field);
        panel.add(label, BorderLayout.WEST); panel.add(field, BorderLayout.CENTER); return panel;
    }
    private JPanel folder(String name, JTextField field) {
        JPanel panel = row(name, field); JButton browse = button("Browse", false);
        browse.addActionListener(event -> {
            JFileChooser picker = new JFileChooser(); picker.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (picker.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) field.setText(picker.getSelectedFile().getAbsolutePath());
        }); panel.add(browse, BorderLayout.EAST); return panel;
    }
    private void chooseFile(JTextField field) {
        JFileChooser picker = new JFileChooser();
        if (picker.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) field.setText(picker.getSelectedFile().getAbsolutePath());
    }
    private Path path(JTextField field) {
        if (field.getText().isBlank()) throw new IllegalArgumentException("Choose the required folders and workflow file first.");
        return Path.of(field.getText().strip());
    }
    private static Color statusColor(String value) {
        return switch (value) { case "Moved", "Replaced", "Downloaded", "Complete" -> SUCCESS; case "Error", "Failed" -> ImageFlowTheme.ERROR; default -> MUTED; };
    }
    private void record(String message) {
        String state = "Info", details = "", image = message;
        int colon = message.indexOf(": ");
        if (colon > 0) {
            String prefix = message.substring(0, colon);
            if (List.of("Moved", "Replaced", "Skipped", "Failed", "Error", "Downloaded", "Saved", "Identifying").contains(prefix)) {
                state = prefix.equals("Saved") ? "Downloaded" : prefix; image = message.substring(colon + 2);
                int arrow = image.indexOf(" -> ");
                if (arrow >= 0) { details = image.substring(arrow + 4); image = image.substring(0, arrow); }
            }
        }
        if (state.equals("Moved") || state.equals("Replaced") || state.equals("Downloaded")) completed++;
        if (state.equals("Skipped")) skipped++;
        if (state.equals("Error") || state.equals("Failed")) errors++;
        counters.setText(completed + " completed     /     " + skipped + " skipped     /     " + errors + " errors");
        activity.insertRow(0, new Object[]{LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")), image, details, state});
        if (activity.getRowCount() > 1000) activity.removeRow(1000);
    }
    private void runTask(String name, Task task) {
        sort.setEnabled(false); download.setEnabled(false); previewButton.setEnabled(false);
        completed = skipped = errors = 0; counters.setText("0 completed     /     0 skipped     /     0 errors");
        progress.setValue(0); progress.setIndeterminate(true); percent.setText("Working"); status.setText(name + "...");
        new SwingWorker<Integer, String>() {
            protected Integer doInBackground() throws Exception { return task.run(this::publish); }
            protected void process(List<String> messages) { messages.forEach(MainWindow.this::record); }
            protected void done() {
                try {
                    int count = get(); completed = count;
                    counters.setText(completed + " completed     /     " + skipped + " skipped     /     " + errors + " errors");
                    status.setText(name + " finished · " + count + " processed");
                    progress.setValue(progress.getMaximum()); percent.setText("100%"); record(name + " finished: " + count + " image(s) processed.");
                } catch (InterruptedException failure) { Thread.currentThread().interrupt(); status.setText("Interrupted"); }
                catch (ExecutionException failure) { status.setText("Operation failed"); percent.setText("Failed"); showError(failure.getCause()); }
                finally { progress.setIndeterminate(false); sort.setEnabled(true); download.setEnabled(true); refreshPreview(); }
            }
        }.execute();
    }
    private void showError(Throwable failure) {
        record("Error: " + failure.getMessage());
        JOptionPane.showMessageDialog(this, failure.getMessage(), "Unable to continue", JOptionPane.ERROR_MESSAGE);
    }
    @FunctionalInterface private interface Task { int run(Consumer<String> log) throws Exception; }
}

