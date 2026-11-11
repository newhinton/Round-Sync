package io.github.x0b.safdav;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;
import android.util.Base64;
import io.github.x0b.safdav.file.SafConstants;

import java.io.IOException;
import java.security.SecureRandom;

/**
 * Saf Emulation Server
 */
public class SafAccessProvider {

    private static final String PREF_KEY_SAF_USER = "io.github.x0b.safdav.safDavUser";
    private static final String PREF_KEY_SAF_PASS = "io.github.x0b.safdav.safDavPass";

    private static SafDAVServer davServer;
    private static SafDirectServer directServer;
    private static String user;
    private static String password;

    public static SafDAVServer getServer(Context context) throws IOException {
        if(null == davServer) {
            initAuth(context);
            davServer = new SafDAVServer(SafConstants.SAF_REMOTE_PORT, user, password, context);
            davServer.start();
        }
        return davServer;
    }

    private static final String KEYSTORE_ALIAS = "cloudbridge_safdav_aes_key";
    private static final String ENC_PREFIX = "ks_gcm_v1:";

    @TargetApi(android.os.Build.VERSION_CODES.M)
    private static javax.crypto.SecretKey getOrCreateKeystoreKey() throws Exception {
        java.security.KeyStore ks = java.security.KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (!ks.containsAlias(KEYSTORE_ALIAS)) {
            javax.crypto.KeyGenerator kg = javax.crypto.KeyGenerator.getInstance(
                    android.security.keystore.KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            kg.init(new android.security.keystore.KeyGenParameterSpec.Builder(
                    KEYSTORE_ALIAS,
                    android.security.keystore.KeyProperties.PURPOSE_ENCRYPT | android.security.keystore.KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build());
            return kg.generateKey();
        }
        return ((java.security.KeyStore.SecretKeyEntry) ks.getEntry(KEYSTORE_ALIAS, null)).getSecretKey();
    }

    @TargetApi(android.os.Build.VERSION_CODES.M)
    private static String encryptSecret(String plain) throws Exception {
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, getOrCreateKeystoreKey());
        byte[] iv = cipher.getIV();
        byte[] ct = cipher.doFinal(plain.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return ENC_PREFIX + Base64.encodeToString(iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(ct, Base64.NO_WRAP);
    }

    @TargetApi(android.os.Build.VERSION_CODES.M)
    private static String decryptSecret(String stored) throws Exception {
        String payload = stored.substring(ENC_PREFIX.length());
        String[] parts = payload.split(":", 2);
        byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
        byte[] ct = Base64.decode(parts[1], Base64.NO_WRAP);
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, getOrCreateKeystoreKey(), new javax.crypto.spec.GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(ct), java.nio.charset.StandardCharsets.UTF_8);
    }

    @SuppressLint("ApplySharedPref")
    private static void initAuth(Context context){
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        if(preferences.contains(PREF_KEY_SAF_PASS) && preferences.contains((PREF_KEY_SAF_USER))){
            String storedPass = preferences.getString(PREF_KEY_SAF_PASS, "");
            user = preferences.getString(PREF_KEY_SAF_USER, "dav");
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                try {
                    if (storedPass.startsWith(ENC_PREFIX)) {
                        password = decryptSecret(storedPass);
                    } else {
                        password = storedPass;
                        preferences.edit().putString(PREF_KEY_SAF_PASS, encryptSecret(password)).commit();
                    }
                    return;
                } catch (Exception ignored) {
                    // Fall through to regenerate if Keystore key was invalidated
                }
            } else {
                password = storedPass;
                return;
            }
        }
        SecureRandom random = new SecureRandom();
        byte[] values = new byte[16];
        random.nextBytes(values);
        password = Base64.encodeToString(values, Base64.NO_WRAP);
        user = "dav";
        String valueToStore = password;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            try {
                valueToStore = encryptSecret(password);
            } catch (Exception ignored) {
            }
        }
        preferences.edit()
                .putString(PREF_KEY_SAF_PASS, valueToStore)
                .putString(PREF_KEY_SAF_USER, user)
                .commit();
    }

    public static SafDirectServer getDirectServer(Context context) {
        if(null == directServer) {
            directServer = new SafDirectServer(context);
        }
        return directServer;
    }

    public static String getUser(Context context) {
        initAuth(context);
        return user;
    }

    public static String getPassword(Context context) {
        initAuth(context);
        return password;
    }
}
