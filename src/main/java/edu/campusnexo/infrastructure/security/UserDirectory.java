package edu.campusnexo.infrastructure.security;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/** People and sensors allowed in, read once from configuration. Secrets are never logged. */
final class UserDirectory {
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9._-]{3,40}");

    private final Map<String, Account> accounts;
    private final List<byte[]> sensorKeys;
    /** Hash checked when the user does not exist, so timing does not reveal valid names. */
    private final String decoyHash = PasswordHasher.hash("decoy".toCharArray());

    UserDirectory(String users, String sensorKeys, String legacyIngestKey) {
        this.accounts = parseUsers(users);
        List<byte[]> keys = new ArrayList<>();
        for (String key : split(sensorKeys)) {
            keys.add(requireStrongKey(key));
        }
        if (legacyIngestKey != null && !legacyIngestKey.isBlank()) {
            keys.add(requireStrongKey(legacyIngestKey.trim()));
        }
        this.sensorKeys = List.copyOf(keys);
    }

    Optional<Account> authenticate(String username, char[] password) {
        Account account = username == null ? null : accounts.get(username);
        if (account == null) {
            PasswordHasher.matches(password, decoyHash);
            return Optional.empty();
        }
        return PasswordHasher.matches(password, account.passwordHash()) ? Optional.of(account) : Optional.empty();
    }

    Optional<Account> find(String username) {
        return Optional.ofNullable(accounts.get(username));
    }

    boolean isSensorKey(String provided) {
        return sensorKeyIndex(provided) >= 0;
    }

    /** Position of the key in APP_SENSOR_KEYS (it names the device without revealing the key), or -1. */
    int sensorKeyIndex(String provided) {
        if (provided == null || provided.isBlank()) {
            return -1;
        }
        byte[] candidate = PasswordHasher.utf8(provided);
        int index = -1;
        for (int i = 0; i < sensorKeys.size(); i++) {
            // Every key is compared, so the time does not tell which one matched.
            if (MessageDigest.isEqual(sensorKeys.get(i), candidate)) {
                index = i;
            }
        }
        return index;
    }

    int userCount() {
        return accounts.size();
    }

    boolean hasAdmin() {
        return accounts.values().stream().anyMatch(account -> account.role() == Role.ADMIN);
    }

    int sensorKeyCount() {
        return sensorKeys.size();
    }

    record Account(String username, Role role, String passwordHash) {
    }

    private static Map<String, Account> parseUsers(String users) {
        Map<String, Account> parsed = new LinkedHashMap<>();
        for (String entry : split(users)) {
            String[] parts = entry.split(":", 3);
            if (parts.length != 3) {
                throw new IllegalStateException("Entrada de APP_USERS mal formada (se espera nombre:ROL:hash)");
            }
            String username = parts[0].trim();
            if (!USERNAME.matcher(username).matches()) {
                throw new IllegalStateException("Nombre de usuario no válido en APP_USERS: " + username);
            }
            Role role;
            try {
                role = Role.valueOf(parts[1].trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException unknown) {
                throw new IllegalStateException("Rol desconocido para " + username + ": " + parts[1]);
            }
            if (role == Role.SENSOR) {
                throw new IllegalStateException("SENSOR no es un rol de persona; usa APP_SENSOR_KEYS para " + username);
            }
            String hash = parts[2].trim();
            if (!PasswordHasher.isWellFormed(hash)) {
                throw new IllegalStateException("El hash de " + username + " no tiene el formato pbkdf2-sha256$...");
            }
            if (parsed.put(username, new Account(username, role, hash)) != null) {
                throw new IllegalStateException("Usuario repetido en APP_USERS: " + username);
            }
        }
        return Map.copyOf(parsed);
    }

    private static byte[] requireStrongKey(String key) {
        if (key.length() < 24) {
            throw new IllegalStateException("Cada clave de sensor debe tener al menos 24 caracteres");
        }
        return PasswordHasher.utf8(key);
    }

    private static List<String> split(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
