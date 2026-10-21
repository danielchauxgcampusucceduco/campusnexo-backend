package edu.campusnexo.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * PBKDF2-HMAC-SHA256 password hashes ({@code pbkdf2-sha256$iterations$salt$hash}, Base64), from the JDK.
 * {@code tools/auth/hash_password.py} produces the same format.
 */
public final class PasswordHasher {
    static final String PREFIX = "pbkdf2-sha256";
    static final int DEFAULT_ITERATIONS = 210_000;
    private static final int KEY_BITS = 256;

    private PasswordHasher() {
    }

    public static String hash(char[] password) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        byte[] hash = derive(password, salt, DEFAULT_ITERATIONS);
        Base64.Encoder base64 = Base64.getEncoder();
        return PREFIX + "$" + DEFAULT_ITERATIONS + "$" + base64.encodeToString(salt) + "$" + base64.encodeToString(hash);
    }

    static boolean isWellFormed(String encoded) {
        String[] parts = encoded.split("\\$");
        return parts.length == 4 && parts[0].equals(PREFIX) && parts[1].matches("\\d{4,7}");
    }

    static boolean matches(char[] password, String encoded) {
        if (!isWellFormed(encoded)) {
            return false;
        }
        String[] parts = encoded.split("\\$");
        Base64.Decoder base64 = Base64.getDecoder();
        byte[] expected = base64.decode(parts[3]);
        byte[] actual = derive(password, base64.decode(parts[2]), Integer.parseInt(parts[1]));
        return MessageDigest.isEqual(expected, actual);
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_BITS);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException impossible) {
            throw new IllegalStateException("PBKDF2WithHmacSHA256 no está disponible en esta JVM", impossible);
        }
    }

    /** {@code java -cp campusnexo.jar ... PasswordHasher <password>} prints a hash for {@code APP_USERS}. */
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Uso: PasswordHasher <contraseña>");
            System.exit(2);
        }
        System.out.println(hash(args[0].toCharArray()));
    }

    static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
