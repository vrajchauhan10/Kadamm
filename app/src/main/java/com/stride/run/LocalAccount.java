package com.stride.run;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * A local account kept on this phone only. The password is never stored: only a salted PBKDF2 hash of it.
 * There is one account per install; signing up again replaces it.
 */
final class LocalAccount {
    private static final String PREFS="kadamm_account";
    private static final int ITERATIONS=120000;
    private static final Pattern EMAIL=Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$");
    private LocalAccount() {}

    private static SharedPreferences p(Context c){return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
    static boolean exists(Context c){return p(c).contains("hash");}
    static boolean loggedIn(Context c){return p(c).getBoolean("loggedIn",false);}
    static boolean guest(Context c){return p(c).getBoolean("guest",false);}
    static String name(Context c){return loggedIn(c)?p(c).getString("name",null):null;}
    static String email(Context c){return loggedIn(c)?p(c).getString("email",null):null;}
    static String savedEmail(Context c){return p(c).getString("email","");}

    /** Returns an error message, or null when the account was created and signed in. */
    static String signUp(Context c,String name,String email,String password,String confirm){
        name=name.trim();email=email.trim().toLowerCase(Locale.US);
        if(name.isEmpty())return "Enter your name";
        if(!EMAIL.matcher(email).matches())return "Enter a valid email address";
        if(password.length()<8)return "Use at least 8 characters for your password";
        if(!password.equals(confirm))return "Passwords don't match";
        byte[] salt=new byte[16];new SecureRandom().nextBytes(salt);
        p(c).edit().putString("name",name).putString("email",email).putString("salt",Base64.encodeToString(salt,Base64.NO_WRAP))
            .putString("hash",hash(password,salt)).putBoolean("loggedIn",true).putBoolean("guest",false).putLong("createdAt",System.currentTimeMillis()).apply();
        return null;
    }

    /** Returns an error message, or null when signed in. */
    static String logIn(Context c,String email,String password){
        email=email.trim().toLowerCase(Locale.US);
        if(!exists(c))return "No account on this phone yet. Sign up first.";
        if(!email.equals(p(c).getString("email","")))return "Email or password is incorrect";
        byte[] salt=Base64.decode(p(c).getString("salt",""),Base64.NO_WRAP);
        // Constant-time comparison, so timing doesn't reveal how much of the hash matched.
        if(!MessageDigest.isEqual(hash(password,salt).getBytes(),p(c).getString("hash","").getBytes()))return "Email or password is incorrect";
        p(c).edit().putBoolean("loggedIn",true).putBoolean("guest",false).apply();
        return null;
    }
    static void continueAsGuest(Context c){p(c).edit().putBoolean("guest",true).putBoolean("loggedIn",false).apply();}
    static void logOut(Context c){p(c).edit().putBoolean("loggedIn",false).putBoolean("guest",false).apply();}

    private static String hash(String password,byte[] salt){
        try{
            PBEKeySpec spec=new PBEKeySpec(password.toCharArray(),salt,ITERATIONS,256);
            byte[] key=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();spec.clearPassword();
            return Base64.encodeToString(key,Base64.NO_WRAP);
        }catch(Exception e){throw new IllegalStateException(e);}
    }
}
