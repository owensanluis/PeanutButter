package Controller;

import Model.*;
import java.sql.*;
import java.nio.file.*;
import java.util.ArrayList;
import static Controller.AccessControl.Resource.*;

/** All application data access passes through this service. No view receives a connection. */
public class SQLite {
    private final String driverURL;
    private int sessionId;
    private int sessionVersion;
    private String sessionName = "anonymous";
    private String previousUse = "No previous account use.";
    private final String dummyHash = PasswordUtils.hash("DummyPassword!09");
    public static final String AUTH_FAILURE = "Invalid username and/or password";

    public SQLite() { this(databasePath()); }
    public SQLite(Path path) { driverURL = "jdbc:sqlite:" + path.toAbsolutePath(); initialize(); }

    private static Path databasePath() {
        try {
            Path location = Paths.get(SQLite.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            // Packaged JAR uses the database beside it; NetBeans uses the project working directory.
            if (Files.isRegularFile(location)) return location.getParent().resolve("database.db");
            return Paths.get("database.db");
        } catch (Exception ex) { throw new AppException("Unable to start the application."); }
    }

    private interface Work<T> { T run(Connection c) throws Exception; }
    private synchronized <T> T transaction(Work<T> work) {
        try (Connection c = DriverManager.getConnection(driverURL)) {
            try (PreparedStatement p = c.prepareStatement("PRAGMA secure_delete=ON")) { p.execute(); }
            c.setAutoCommit(false);
            try {
                T value = work.run(c);
                c.commit();
                return value;
            } catch (AppException ex) {
                // Expected rejections are raised before data writes; retain their audit entries.
                c.commit();
                throw ex;
            } catch (Exception ex) {
                c.rollback();
                logout();
                throw new AppException("Unable to complete the operation. Please sign in again.");
            }
        } catch (AppException ex) { throw ex; }
        catch (Exception ex) {
            logout();
            throw new AppException("Unable to complete the operation. Please sign in again.");
        }
    }

    private static PreparedStatement statement(Connection c, String sql, Object... args) throws SQLException {
        PreparedStatement p = c.prepareStatement(sql);
        for (int i = 0; i < args.length; i++) p.setObject(i + 1, args[i]);
        return p;
    }
    private static int update(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement p = statement(c, sql, args)) { return p.executeUpdate(); }
    }
    private static String now() { return new Timestamp(System.currentTimeMillis()).toString(); }
    private static void audit(Connection c, String event, String actor, String detail) throws SQLException {
        update(c, "INSERT INTO logs(event,username,desc,timestamp) VALUES(?,?,?,?)", event, actor, detail, now());
    }
    private void reject(Connection c, String field) throws SQLException {
        audit(c, "VALIDATION_FAILURE", sessionName, "Rejected " + field);
        throw new AppException("Invalid input. Check the displayed format and limits.");
    }
    private static boolean usernameOK(String s) { return s != null && s.matches("[A-Za-z0-9]{3,32}"); }
    private void username(Connection c, String s) throws SQLException { if (!usernameOK(s)) reject(c, "username (3-32 letters or digits)"); }
    private void productName(Connection c, String s) throws SQLException {
        if (s == null || !s.matches("[A-Za-z0-9](?:[A-Za-z0-9 ]{0,62}[A-Za-z0-9])?")) reject(c, "product name (1-64 letters, digits, internal spaces)");
    }
    private void password(Connection c, String s, String confirmation) throws SQLException {
        if (s == null || s.length() < 8 || s.length() > 64 || !s.matches("[!-~]+")
                || !s.matches(".*[A-Z].*") || !s.matches(".*[a-z].*") || !s.matches(".*[0-9].*")
                || !s.matches(".*[^A-Za-z0-9].*") || !s.equals(confirmation)) reject(c, "password or confirmation (8-64, upper, lower, digit, symbol, no spaces)");
    }
    private int quantity(Connection c, String s, int minimum) throws SQLException {
        if (s == null || !s.matches("[0-9]{1,6}")) reject(c, "quantity (up to 6 digits)");
        int n = Integer.parseInt(s);
        if (n < minimum || n > 100000) reject(c, "quantity (" + minimum + "-100000)");
        return n;
    }
    private double price(Connection c, String s) throws SQLException {
        if (s == null || !s.matches("[0-9]{1,7}(\\.[0-9]{1,2})?")) reject(c, "price (0.01-1000000.00, at most 2 decimals)");
        double value = Double.parseDouble(s);
        if (value < 0.01 || value > 1000000) reject(c, "price range");
        return value;
    }
    private User lookup(Connection c, String name) throws SQLException {
        try (PreparedStatement p = statement(c, "SELECT * FROM users WHERE username=?", name); ResultSet r = p.executeQuery()) {
            if (!r.next()) return null;
            return new User(r.getInt("id"), r.getString("username"), r.getString("password"), r.getInt("role"), r.getInt("locked"));
        }
    }
    private int require(Connection c, AccessControl.Resource resource) throws SQLException {
        int role = 0;
        try (PreparedStatement p = statement(c, "SELECT role,locked,session_version FROM users WHERE id=?", sessionId); ResultSet r = p.executeQuery()) {
            if (r.next() && r.getInt("locked") == 0 && r.getInt("session_version") == sessionVersion) role = r.getInt("role");
        }
        if (!AccessControl.allows(role, resource)) {
            audit(c, "ACCESS_VIOLATION", sessionName, "Denied resource " + resource);
            if (role < 2 || role > 5) logout();
            throw new AppException("Access denied.");
        }
        return role;
    }
    public void authorize(AccessControl.Resource resource) { transaction(c -> { require(c, resource); return null; }); }
    public boolean isSignedIn() { return sessionId != 0; }
    public String getSessionName() { return sessionName; }
    public String getPreviousUse() { authorize(PASSWORD); return previousUse; }
    public void logout() { sessionId = 0; sessionVersion = 0; sessionName = "anonymous"; }
    public int currentRole() { return transaction(c -> require(c, PASSWORD)); }

    private void initialize() {
        transaction(c -> {
            update(c, "CREATE TABLE IF NOT EXISTS users(id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT NOT NULL UNIQUE, password TEXT NOT NULL, role INTEGER NOT NULL DEFAULT 2 CHECK(role BETWEEN 1 AND 5), locked INTEGER NOT NULL DEFAULT 0)");
            update(c, "CREATE TABLE IF NOT EXISTS product(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL UNIQUE,stock INTEGER NOT NULL DEFAULT 0 CHECK(stock BETWEEN 0 AND 100000),price REAL NOT NULL CHECK(price>0 AND price<=1000000))");
            update(c, "CREATE TABLE IF NOT EXISTS history(id INTEGER PRIMARY KEY AUTOINCREMENT,username TEXT NOT NULL,name TEXT NOT NULL,stock INTEGER NOT NULL,timestamp TEXT NOT NULL)");
            update(c, "CREATE TABLE IF NOT EXISTS logs(id INTEGER PRIMARY KEY AUTOINCREMENT,event TEXT NOT NULL,username TEXT NOT NULL,desc TEXT NOT NULL,timestamp TEXT NOT NULL)");
            // Fixed schema identifiers only. Independently inspect each column for idempotent upgrades.
            addColumn(c, "users", "failed_attempts", "INTEGER NOT NULL DEFAULT 0");
            addColumn(c, "users", "last_attempt", "TEXT");
            addColumn(c, "users", "last_status", "TEXT");
            addColumn(c, "users", "session_version", "INTEGER NOT NULL DEFAULT 0");
            addColumn(c, "users", "previous_role", "INTEGER NOT NULL DEFAULT 2");
            addColumn(c, "history", "unit_price", "REAL");
            if (addColumn(c, "history", "user_id", "INTEGER")) {
                update(c, "UPDATE history SET user_id=(SELECT id FROM users WHERE users.username=history.username)");
            }
            // Existing purchases get the available legacy price once; future purchases store an immutable snapshot.
            update(c, "UPDATE history SET unit_price=(SELECT price FROM product WHERE product.name=history.name) WHERE unit_price IS NULL");
            ArrayList<Integer> legacyIds = new ArrayList<>();
            try (PreparedStatement p = statement(c, "SELECT id,password FROM users"); ResultSet r = p.executeQuery()) {
                while (r.next()) if (!r.getString("password").startsWith("pbkdf2-sha256$600000$")) legacyIds.add(r.getInt("id"));
            }
            // Never retain plaintext or silently authorize old weak credentials. Admin must reset these accounts.
            for (int id : legacyIds) {
                update(c, "UPDATE users SET password=?,previous_role=CASE WHEN role BETWEEN 2 AND 5 THEN role ELSE 2 END,role=1,locked=1,session_version=session_version+1 WHERE id=?",
                    PasswordUtils.hash(java.util.UUID.randomUUID().toString()), id);
                audit(c, "MIGRATION", "system", "Legacy credential invalidated for user ID " + id);
            }
            return null;
        });
    }
    private static boolean addColumn(Connection c, String table, String column, String type) throws SQLException {
        boolean found = false;
        try (PreparedStatement p = statement(c, "PRAGMA table_info(" + table + ")"); ResultSet r = p.executeQuery()) {
            while (r.next()) if (column.equals(r.getString("name"))) found = true;
        }
        if (!found) update(c, "ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
        return !found;
    }

    public boolean login(String name, String pass) {
        logout();
        try { return transaction(c -> authenticate(c, name, pass, true)); }
        catch (AppException ex) { logout(); return false; }
    }
    private boolean authenticate(Connection c, String name, String pass, boolean establish) throws SQLException {
        boolean inputOK = usernameOK(name) && pass != null && pass.length() >= 1 && pass.length() <= 64;
        if (!inputOK) audit(c, "VALIDATION_FAILURE", "anonymous", "Rejected authentication input");
        User u = usernameOK(name) ? lookup(c, name) : null;
        boolean matched = PasswordUtils.verify(inputOK ? pass : "Invalid!01", u == null ? dummyHash : u.getPassword());
        boolean ok = inputOK && matched && u != null && u.getRole() >= 2 && u.getRole() <= 5 && u.getLocked() == 0;
        String actor = usernameOK(name) ? name : "anonymous";
        if (u != null) {
            String priorTime = null, priorStatus = null;
            try (PreparedStatement p = statement(c, "SELECT last_attempt,last_status FROM users WHERE id=?", u.getId()); ResultSet r = p.executeQuery()) {
                if (r.next()) { priorTime = r.getString(1); priorStatus = r.getString(2); }
            }
            if (ok) {
                update(c, "UPDATE users SET failed_attempts=0,last_attempt=?,last_status=? WHERE id=?", now(), "SUCCESS", u.getId());
                if (establish) {
                    previousUse = priorTime == null ? "No previous account use." : "Previous account use: " + priorTime + " - " + priorStatus;
                    sessionId = u.getId(); sessionName = u.getUsername();
                    try (PreparedStatement p = statement(c, "SELECT session_version FROM users WHERE id=?", sessionId); ResultSet r = p.executeQuery()) { r.next(); sessionVersion = r.getInt(1); }
                }
            } else {
                update(c, "UPDATE users SET failed_attempts=MIN(failed_attempts+1,5),previous_role=CASE WHEN role BETWEEN 2 AND 5 THEN role ELSE previous_role END,role=CASE WHEN failed_attempts+1>=5 THEN 1 ELSE role END,locked=CASE WHEN failed_attempts+1>=5 THEN 1 ELSE locked END,last_attempt=?,last_status=? WHERE id=?", now(), "FAILURE", u.getId());
            }
        }
        audit(c, ok ? "AUTH_SUCCESS" : "AUTH_FAILURE", actor, establish ? "Login attempt" : "Critical operation reauthentication");
        if (!ok && !establish) logout();
        return ok;
    }
    public void register(String name, String pass, String confirm) {
        transaction(c -> {
            username(c, name); password(c, pass, confirm);
            if (lookup(c, name) != null) {
                audit(c, "VALIDATION_FAILURE", "anonymous", "Registration rejected");
                throw new AppException("Registration could not be completed.");
            }
            update(c, "INSERT INTO users(username,password,role) VALUES(?,?,?)", name, PasswordUtils.hash(pass), 2);
            audit(c, "REGISTER", name, "Client account created");
            return null;
        });
    }

    public ArrayList<Product> getProduct() {
        return transaction(c -> {
            require(c, PRODUCTS);
            ArrayList<Product> result = new ArrayList<>();
            try (PreparedStatement p = statement(c, "SELECT id,name,stock,price FROM product ORDER BY id"); ResultSet r = p.executeQuery()) {
                while (r.next()) result.add(new Product(r.getInt(1), r.getString(2), r.getInt(3), r.getFloat(4)));
            }
            return result;
        });
    }
    public void saveProduct(String oldName, String name, String stock, String amount) {
        transaction(c -> {
            require(c, EDIT_PRODUCTS); productName(c, name);
            if (oldName != null) productName(c, oldName);
            int q = quantity(c, stock, 0); double value = price(c, amount);
            try (PreparedStatement p = statement(c, "SELECT id FROM product WHERE name=? AND (? IS NULL OR name<>?)", name, oldName, oldName); ResultSet r = p.executeQuery()) {
                if (r.next()) reject(c, "duplicate product name");
            }
            int changed;
            if (oldName == null) changed = update(c, "INSERT INTO product(name,stock,price) VALUES(?,?,?)", name, q, value);
            else changed = update(c, "UPDATE product SET name=?,stock=?,price=? WHERE name=?", name, q, value, oldName);
            if (changed != 1) reject(c, "product selection");
            audit(c, "PRODUCT_CHANGE", sessionName, "Product saved: " + name); return null;
        });
    }
    public void removeProduct(String name) {
        transaction(c -> { require(c, EDIT_PRODUCTS); productName(c, name);
            if (update(c, "DELETE FROM product WHERE name=?", name) != 1) reject(c, "product selection");
            audit(c, "PRODUCT_CHANGE", sessionName, "Product deleted: " + name); return null;
        });
    }
    public void purchase(String name, String amount) {
        transaction(c -> {
            require(c, PURCHASE); productName(c, name); int q = quantity(c, amount, 1);
            double value;
            try (PreparedStatement p = statement(c, "SELECT price FROM product WHERE name=?", name); ResultSet r = p.executeQuery()) {
                if (!r.next()) { reject(c, "product selection"); return null; }
                value = r.getDouble(1);
            }
            if (update(c, "UPDATE product SET stock=stock-? WHERE name=? AND stock>=?", q, name, q) != 1) reject(c, "insufficient stock");
            update(c, "INSERT INTO history(username,name,stock,timestamp,unit_price,user_id) VALUES(?,?,?,?,?,?)", sessionName, name, q, now(), value, sessionId);
            audit(c, "PURCHASE", sessionName, "Purchased " + q + " of " + name); return null;
        });
    }
    public ArrayList<History> getHistory() { return searchHistory(""); }
    public ArrayList<History> searchHistory(String term) {
        return transaction(c -> {
            int role = require(c, HISTORY);
            if (term == null || !term.matches("[A-Za-z0-9 ]{0,64}")) reject(c, "history search (0-64 letters, digits, spaces)");
            ArrayList<History> result = new ArrayList<>();
            try (PreparedStatement p = statement(c, "SELECT * FROM history WHERE (?=4 OR user_id=?) AND (instr(username,?)>0 OR instr(name,?)>0) ORDER BY id DESC", role, sessionId, term, term); ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    History h = new History(r.getInt("id"), r.getString("username"), r.getString("name"), r.getInt("stock"), r.getString("timestamp"));
                    double value = r.getDouble("unit_price"); h.setUnitPrice(r.wasNull() ? null : value); result.add(h);
                }
            }
            return result;
        });
    }
    public ArrayList<User> getUsers() {
        return transaction(c -> {
            require(c, USERS); ArrayList<User> result = new ArrayList<>();
            try (PreparedStatement p = statement(c, "SELECT id,username,role,locked FROM users ORDER BY id"); ResultSet r = p.executeQuery()) {
                while (r.next()) result.add(new User(r.getInt(1), r.getString(2), "********", r.getInt(3), r.getInt(4)));
            }
            return result;
        });
    }
    private void mutableUser(Connection c, String name) throws SQLException {
        username(c, name);
        if (sessionName.equals(name)) throw new AppException("Use Change My Password for your account. Another administrator must change your role or delete your account.");
        if (lookup(c, name) == null) reject(c, "user selection");
    }
    public void setRole(String name, String roleText) {
        transaction(c -> {
            require(c, USERS); mutableUser(c, name);
            if (roleText == null || !roleText.matches("[1-5]")) reject(c, "role (1-5)");
            int role = Integer.parseInt(roleText);
            update(c, "UPDATE users SET previous_role=CASE WHEN role BETWEEN 2 AND 5 THEN role ELSE previous_role END,role=?,locked=?,failed_attempts=0,session_version=session_version+1 WHERE username=?", role, role == 1 ? 1 : 0, name);
            audit(c, "USER_CHANGE", sessionName, "Assigned role " + role + " to " + name); return null;
        });
    }
    public void toggleLock(String name) {
        transaction(c -> {
            require(c, USERS); mutableUser(c, name);
            update(c, "UPDATE users SET previous_role=CASE WHEN role BETWEEN 2 AND 5 THEN role ELSE previous_role END,role=CASE WHEN role=1 THEN previous_role ELSE 1 END,locked=CASE WHEN role=1 THEN 0 ELSE 1 END,failed_attempts=0,session_version=session_version+1 WHERE username=?", name);
            audit(c, "USER_CHANGE", sessionName, "Changed lock state for " + name); return null;
        });
    }
    public void removeUser(String name) {
        transaction(c -> { require(c, USERS); mutableUser(c, name);
            update(c, "DELETE FROM users WHERE username=?", name);
            audit(c, "USER_CHANGE", sessionName, "Deleted user " + name); return null;
        });
    }
    public void changePassword(String name, String currentPassword, String next, String confirm) {
        transaction(c -> {
            require(c, PASSWORD); username(c, name);
            if (!sessionName.equals(name)) require(c, USERS);
            if (lookup(c, name) == null) reject(c, "user selection");
            password(c, next, confirm);
            if (!authenticate(c, sessionName, currentPassword, false)) throw new AppException(AUTH_FAILURE);
            String actor = sessionName;
            update(c, "UPDATE users SET password=?,session_version=session_version+1 WHERE username=?", PasswordUtils.hash(next), name);
            audit(c, "PASSWORD_CHANGE", actor, "Changed password for " + name);
            if (actor.equals(name)) logout();
            return null;
        });
    }
    public ArrayList<Logs> getLogs() {
        return transaction(c -> {
            require(c, LOGS); ArrayList<Logs> result = new ArrayList<>();
            try (PreparedStatement p = statement(c, "SELECT * FROM logs ORDER BY id DESC"); ResultSet r = p.executeQuery()) {
                while (r.next()) result.add(new Logs(r.getInt("id"), r.getString("event"), r.getString("username"), r.getString("desc"), r.getString("timestamp")));
            }
            return result;
        });
    }
    public void clearLogs() {
        transaction(c -> { require(c, LOGS); update(c, "DELETE FROM logs"); audit(c, "LOG_CLEAR", sessionName, "Administrator cleared security logs"); return null; });
    }
}
