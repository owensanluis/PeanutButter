/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Controller;

import javax.crypto.spec.PBEKeySpec;
import javax.crypto.SecretKeyFactory;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 *
 * @author PC
 */
public class PasswordUtils {
    public static final int ITERATIONS = 65536;
    public static final int KEY_LENGTH = 256;
    
    public static String hash(String simplePassword){
        try{
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            
            byte[] hash = pbkdf2(simplePassword.toCharArray(), salt);
            
            return Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(hash);
        } catch (Exception e){
            throw new RuntimeException("Cannot hash password", e);
        }
    }
    
    public static boolean verify(String simplePassword, String storedValue){
        try{
            String[] parts = storedValue.split(":");
            byte[] salt = Base64.getDecoder().decode(parts[0]);
            byte[] expHash = Base64.getDecoder().decode(parts[1]);
            
            byte[] testHash = pbkdf2(simplePassword.toCharArray(), salt);
            
            return Arrays.equals(testHash, expHash);
        } catch (Exception e) {
            return false;
        }
    }
    
    private static byte[] pbkdf2(char[] password, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH);
        SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2SHA256");
        
        return skf.generateSecret(spec).getEncoded();
    }
}
