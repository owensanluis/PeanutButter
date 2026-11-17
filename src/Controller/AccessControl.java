package Controller;

/** One allowlist shared by navigation and every protected service operation. */
public final class AccessControl {
    public enum Resource { HOME_CLIENT, HOME_STAFF, HOME_MANAGER, HOME_ADMIN,
        PRODUCTS, EDIT_PRODUCTS, PURCHASE, HISTORY, USERS, LOGS, PASSWORD }
    private AccessControl() {}
    public static boolean allows(int role, Resource resource) {
        if (resource == null || role < 2 || role > 5) return false;
        switch (resource) {
            case HOME_CLIENT: return role == 2;
            case HOME_STAFF: return role == 3;
            case HOME_MANAGER: return role == 4;
            case HOME_ADMIN: return role == 5;
            case PRODUCTS: return role >= 2 && role <= 4;
            case EDIT_PRODUCTS: return role == 3 || role == 4;
            case PURCHASE: return role == 2;
            case HISTORY: return role == 2 || role == 4;
            case USERS: case LOGS: return role == 5;
            case PASSWORD: return true;
            default: return false;
        }
    }
}
