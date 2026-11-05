package com.imagesorter;

import com.imagesorter.ui.MainWindow;
import javax.swing.SwingUtilities;

public final class App {
    private App() { }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            com.imagesorter.ui.ImageFlowTheme.install();
            new MainWindow().setVisible(true);
        });
    }
}
