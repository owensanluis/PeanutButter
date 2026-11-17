package Controller;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

public final class PasswordUtils {
    public static final int ITERATIONS = 600000;
    private PasswordUtils() {}

    public static String hash(String password) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return "pbkdf2-sha256$" + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt)
            + "$" + Base64.getEncoder().encodeToString(derive(password, salt));
    }

    public static boolean verify(String password, String stored) {
        if (password == null || password.length() > 64 || stored == null) return false;
        try {
            String[] parts = stored.split("\\$", -1);
            if (parts.length != 4 || !parts[0].equals("pbkdf2-sha256")
                    || !parts[1].equals(String.valueOf(ITERATIONS))) return false;
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            if (salt.length != 16 || expected.length != 32) return false;
            return MessageDigest.isEqual(expected, derive(password, salt));
        } catch (IllegalArgumentException ex) { return false; }
    }

    private static byte[] derive(String password, byte[] salt) {
        char[] chars = password.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(chars, salt, ITERATIONS, 256);
        Arrays.fill(chars, '\0');
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception ex) { throw new AppException("Unable to complete the operation. Please try again."); }
        finally { spec.clearPassword(); }
    }
}
