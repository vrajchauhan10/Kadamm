package com.stride.run;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Base64;

import java.io.File;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Accounts kept on this phone in a SQLite database (kadamm.db, table accounts). Passwords are never stored:
 * only a salted PBKDF2 hash. Several accounts can live on one phone; prefs remember who is signed in, or guest.
 * The single account kept in prefs before 8.0 is moved into the database the first time it is read.
 */
final class LocalAccount {
    private static final String PREFS="kadamm_account";
    private static final int ITERATIONS=120000;
    private static final Pattern EMAIL=Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$");
    private LocalAccount() {}

    /** One row of the accounts table, without the password hash. */
    static final class Account { long id;String name,email,photo;long created,lastLogin; }

    private static final class Db extends SQLiteOpenHelper {
        Db(Context c){super(c.getApplicationContext(),"kadamm.db",null,1);}
        @Override public void onCreate(SQLiteDatabase d){
            d.execSQL("CREATE TABLE accounts(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,email TEXT NOT NULL UNIQUE,salt TEXT NOT NULL,hash TEXT NOT NULL,photo TEXT,created INTEGER NOT NULL,updated INTEGER NOT NULL,last_login INTEGER)");
        }
        @Override public void onUpgrade(SQLiteDatabase d,int from,int to){}
    }
    private static Db db;
    private static synchronized SQLiteDatabase db(Context c){if(db==null)db=new Db(c);SQLiteDatabase d=db.getWritableDatabase();migrate(c,d);return d;}
    private static boolean migrated;
    /** Moves the pre-8.0 prefs account into the table once, keeping its password hash so the same password works. */
    private static void migrate(Context c,SQLiteDatabase d){
        if(migrated)return;migrated=true;SharedPreferences p=p(c);
        if(!p.contains("hash")||p.contains("accountId"))return;
        ContentValues v=new ContentValues();v.put("name",p.getString("name","Kadamm athlete"));v.put("email",p.getString("email",""));v.put("salt",p.getString("salt",""));v.put("hash",p.getString("hash",""));
        long now=System.currentTimeMillis();v.put("created",p.getLong("createdAt",now));v.put("updated",now);
        long id=d.insertWithOnConflict("accounts",null,v,SQLiteDatabase.CONFLICT_IGNORE);
        if(id<0){Cursor q=d.rawQuery("SELECT id FROM accounts WHERE email=?",new String[]{p.getString("email","")});if(q.moveToFirst())id=q.getLong(0);q.close();}
        p.edit().putLong("accountId",id).remove("hash").remove("salt").remove("name").apply();
    }

    private static SharedPreferences p(Context c){return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
    private static Account read(Cursor q){Account a=new Account();a.id=q.getLong(0);a.name=q.getString(1);a.email=q.getString(2);a.photo=q.getString(3);a.created=q.getLong(4);a.lastLogin=q.isNull(5)?0:q.getLong(5);return a;}
    private static final String COLS="id,name,email,photo,created,last_login";

    /** Every account on this phone, most recently used first. */
    static List<Account> all(Context c){
        List<Account> out=new ArrayList<>();Cursor q=db(c).rawQuery("SELECT "+COLS+" FROM accounts ORDER BY COALESCE(last_login,created) DESC",null);
        while(q.moveToNext())out.add(read(q));q.close();return out;
    }
    /** The signed-in account, or null for guests and signed-out users. */
    static Account current(Context c){
        if(!p(c).getBoolean("loggedIn",false))return null;long id=p(c).getLong("accountId",-1);if(id<0){db(c);id=p(c).getLong("accountId",-1);}
        Cursor q=db(c).rawQuery("SELECT "+COLS+" FROM accounts WHERE id=?",new String[]{String.valueOf(id)});Account a=q.moveToFirst()?read(q):null;q.close();return a;
    }
    static boolean exists(Context c){Cursor q=db(c).rawQuery("SELECT COUNT(*) FROM accounts",null);q.moveToFirst();boolean any=q.getLong(0)>0;q.close();return any;}
    static boolean loggedIn(Context c){return current(c)!=null;}
    static boolean guest(Context c){return p(c).getBoolean("guest",false);}
    static String name(Context c){Account a=current(c);return a==null?null:a.name;}
    static String email(Context c){Account a=current(c);return a==null?null:a.email;}
    static String savedEmail(Context c){return p(c).getString("email","");}
    static File photo(Context c){Account a=current(c);if(a==null||a.photo==null)return null;File f=new File(a.photo);return f.exists()?f:null;}

    /** Returns an error message, or null when the account was created and signed in. */
    static String signUp(Context c,String name,String email,String password,String confirm){
        name=name.trim();email=email.trim().toLowerCase(Locale.US);
        if(name.isEmpty())return "Enter your name";
        if(!EMAIL.matcher(email).matches())return "Enter a valid email address";
        if(password.length()<8)return "Use at least 8 characters for your password";
        if(!password.equals(confirm))return "Passwords don't match";
        byte[] salt=new byte[16];new SecureRandom().nextBytes(salt);long now=System.currentTimeMillis();
        ContentValues v=new ContentValues();v.put("name",name);v.put("email",email);v.put("salt",Base64.encodeToString(salt,Base64.NO_WRAP));v.put("hash",hash(password,salt));v.put("created",now);v.put("updated",now);v.put("last_login",now);
        long id=db(c).insert("accounts",null,v);
        if(id<0)return "There's already an account with this email on this phone. Log in instead.";
        p(c).edit().putLong("accountId",id).putString("email",email).putBoolean("loggedIn",true).putBoolean("guest",false).apply();
        return null;
    }

    /** Returns an error message, or null when signed in. */
    static String logIn(Context c,String email,String password){
        email=email.trim().toLowerCase(Locale.US);
        if(!exists(c))return "No account on this phone yet. Sign up first.";
        Cursor q=db(c).rawQuery("SELECT id,salt,hash FROM accounts WHERE email=?",new String[]{email});
        if(!q.moveToFirst()){q.close();return "Email or password is incorrect";}
        long id=q.getLong(0);byte[] salt=Base64.decode(q.getString(1),Base64.NO_WRAP);String stored=q.getString(2);q.close();
        // Constant-time comparison, so timing doesn't reveal how much of the hash matched.
        if(!MessageDigest.isEqual(hash(password,salt).getBytes(),stored.getBytes()))return "Email or password is incorrect";
        ContentValues v=new ContentValues();v.put("last_login",System.currentTimeMillis());db(c).update("accounts",v,"id=?",new String[]{String.valueOf(id)});
        p(c).edit().putLong("accountId",id).putString("email",email).putBoolean("loggedIn",true).putBoolean("guest",false).apply();
        return null;
    }
    static void continueAsGuest(Context c){p(c).edit().putBoolean("guest",true).putBoolean("loggedIn",false).apply();}
    static void logOut(Context c){p(c).edit().putBoolean("loggedIn",false).putBoolean("guest",false).apply();}

    /** Returns an error message, or null when the name was changed. */
    static String rename(Context c,String name){
        Account a=current(c);if(a==null)return "Sign in to change your name";name=name.trim();if(name.isEmpty())return "Enter your name";if(name.length()>40)return "Use 40 characters or fewer";
        ContentValues v=new ContentValues();v.put("name",name);v.put("updated",System.currentTimeMillis());db(c).update("accounts",v,"id=?",new String[]{String.valueOf(a.id)});return null;
    }
    /** Points the signed-in account at a photo file in app storage; null removes it. */
    static void setPhoto(Context c,File f){
        Account a=current(c);if(a==null)return;if(a.photo!=null&&(f==null||!a.photo.equals(f.getPath())))new File(a.photo).delete();
        ContentValues v=new ContentValues();if(f==null)v.putNull("photo");else v.put("photo",f.getPath());v.put("updated",System.currentTimeMillis());db(c).update("accounts",v,"id=?",new String[]{String.valueOf(a.id)});
    }
    /** Returns an error message, or null when the password was changed. */
    static String changePassword(Context c,String current,String next,String confirm){
        Account a=current(c);if(a==null)return "Sign in first";
        Cursor q=db(c).rawQuery("SELECT salt,hash FROM accounts WHERE id=?",new String[]{String.valueOf(a.id)});q.moveToFirst();byte[] salt=Base64.decode(q.getString(0),Base64.NO_WRAP);String stored=q.getString(1);q.close();
        if(!MessageDigest.isEqual(hash(current,salt).getBytes(),stored.getBytes()))return "Your current password is incorrect";
        if(next.length()<8)return "Use at least 8 characters for your new password";if(!next.equals(confirm))return "New passwords don't match";
        byte[] ns=new byte[16];new SecureRandom().nextBytes(ns);
        ContentValues v=new ContentValues();v.put("salt",Base64.encodeToString(ns,Base64.NO_WRAP));v.put("hash",hash(next,ns));v.put("updated",System.currentTimeMillis());db(c).update("accounts",v,"id=?",new String[]{String.valueOf(a.id)});return null;
    }
    /** 0 to 4: length, mixed case, digits and symbols each add a point. */
    static int strength(String pw){if(pw.length()<8)return pw.isEmpty()?0:1;int s=1;if(pw.length()>=12)s++;if(!pw.equals(pw.toLowerCase(Locale.US))&&!pw.equals(pw.toUpperCase(Locale.US)))s++;if(pw.matches(".*\\d.*")&&pw.matches(".*[^A-Za-z0-9].*"))s++;return Math.min(4,s);}

    private static String hash(String password,byte[] salt){
        try{
            PBEKeySpec spec=new PBEKeySpec(password.toCharArray(),salt,ITERATIONS,256);
            byte[] key=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();spec.clearPassword();
            return Base64.encodeToString(key,Base64.NO_WRAP);
        }catch(Exception e){throw new IllegalStateException(e);}
    }
}
