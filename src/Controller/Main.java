package Controller;

import View.Frame;
import java.awt.*;
import javax.swing.*;

public class Main {
    public SQLite sqlite;
    private Frame frame;
    public static void main(String[] args) { SwingUtilities.invokeLater(() -> new Main().init()); }
    public void init() {
        try {
            sqlite = new SQLite();
            // Catch unexpected event-handler failures without exposing Java/SQLite internals.
            Toolkit.getDefaultToolkit().getSystemEventQueue().push(new EventQueue() {
                @Override protected void dispatchEvent(AWTEvent event) {
                    try { super.dispatchEvent(event); }
                    catch (Throwable error) {
                        if (frame != null) frame.safeFailure(error);
                    }
                }
            });
            frame = new Frame();
            frame.init(this);
        } catch (Throwable error) {
            if (sqlite != null) sqlite.logout();
            if (frame != null) frame.dispose();
            if (!GraphicsEnvironment.isHeadless()) JOptionPane.showMessageDialog(null, "Unable to start the application. Please contact your administrator.");
        }
    }
}
