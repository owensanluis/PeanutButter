package View;

import Controller.SQLite;
import javax.swing.*;
import java.awt.Component;

public final class PasswordDialog {
    private PasswordDialog() {}
    public static void change(Component parent, SQLite sqlite, String target) {
        JPasswordField current = new JPasswordField();
        JPasswordField next = new JPasswordField();
        JPasswordField confirm = new JPasswordField();
        Object[] fields = {"Your current password (reauthentication):", current,
            "New password: 8-64 characters; upper, lower, digit, symbol; no spaces", next,
            "Confirm new password:", confirm};
        try {
            if (JOptionPane.showConfirmDialog(parent, fields, "Change password for " + target,
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
                sqlite.changePassword(target, new String(current.getPassword()), new String(next.getPassword()), new String(confirm.getPassword()));
                if (!sqlite.isSignedIn()) {
                    java.awt.Window window = parent instanceof java.awt.Window ? (java.awt.Window) parent : SwingUtilities.getWindowAncestor(parent);
                    if (window instanceof Frame) ((Frame) window).loginNav();
                }
                JOptionPane.showMessageDialog(parent, "Password changed. Sign in again if you changed your own password.");
            }
        } finally { current.setText(""); next.setText(""); confirm.setText(""); }
    }
}
