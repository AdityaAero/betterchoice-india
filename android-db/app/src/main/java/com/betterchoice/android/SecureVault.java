package com.betterchoice.android;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Encrypted, atomic, no-backup storage; no plaintext fallback if Keystore fails. */
final class SecureVault implements AccountStore.Storage {
    private static final String ALIAS = "betterchoice_account_v2";
    private final AtomicFile file;
    SecureVault(Context context) { file = new AtomicFile(new File(context.getNoBackupFilesDir(), "account-v2.bin")); }
    private SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        if (!ks.containsAlias(ALIAS)) {
            KeyGenerator generator = KeyGenerator.getInstance("AES", "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build());
            return generator.generateKey();
        }
        return (SecretKey) ks.getKey(ALIAS, null);
    }
    @Override public String read() throws Exception {
        if (!file.getBaseFile().exists()) return "";
        if (file.getBaseFile().length() > 65536) throw new IllegalStateException("Invalid encrypted storage");
        byte[] bytes = file.readFully();
        if (bytes.length < 29 || bytes.length > 65536) throw new IllegalStateException("Invalid encrypted storage");
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        if (buffer.get() != 1) throw new IllegalStateException("Invalid storage version");
        byte[] iv = new byte[12]; buffer.get(iv); byte[] data = new byte[buffer.remaining()]; buffer.get(data);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
        cipher.updateAAD(ALIAS.getBytes(StandardCharsets.UTF_8));
        return new String(cipher.doFinal(data), StandardCharsets.UTF_8);
    }
    @Override public void write(String value) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key());
        cipher.updateAAD(ALIAS.getBytes(StandardCharsets.UTF_8));
        byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        byte[] bytes = ByteBuffer.allocate(13 + encrypted.length).put((byte) 1).put(cipher.getIV()).put(encrypted).array();
        FileOutputStream stream = null;
        try { stream = file.startWrite(); stream.write(bytes); file.finishWrite(stream); }
        catch (Exception error) { if (stream != null) file.failWrite(stream); throw error; }
    }
    @Override public void clear() {
        file.delete();
        // Also destroy the key so any undeleted ciphertext cannot restore the session.
        try { KeyStore ks = KeyStore.getInstance("AndroidKeyStore"); ks.load(null); ks.deleteEntry(ALIAS); }
        catch (Exception unavailable) { /* No plaintext fallback. */ }
    }
}
