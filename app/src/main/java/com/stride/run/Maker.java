package com.stride.run;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import java.security.MessageDigest;

/**
 * The maker's mark, "crafted by V", and whether this copy is the official build.
 * The mark isn't kept as plain text, and the official build is the one signed with the maker's key: a copy rebuilt
 * from the source carries a different signature, so it shows "Unofficial build" next to the mark and can't install
 * over (or update) the official app.
 */
final class Maker {
    private Maker() {}
    private static final int K = 0x5A;
    private static final int[] A = {57, 40, 59, 60, 46, 63, 62, 122, 56, 35, 122}, V = {12};
    private static final String OFFICIAL = "1c1e8f2cfb03494bcde526b2a3ca25d0ddbcd9334b648600b174a46581a3e6c1";
    private static Boolean official;

    static String by() { return d(A); }
    static String v() { return d(V); }
    static String mark() { return by() + v(); }
    private static String d(int[] x) { char[] c = new char[x.length]; for (int i = 0; i < x.length; i++) c[i] = (char) (x[i] ^ K); return new String(c); }

    /** True when this app is signed with the maker's key. */
    @SuppressWarnings("deprecation")
    static boolean official(Context c) {
        if (official != null) return official;
        boolean ok = false;
        try {
            Signature[] sigs;
            if (Build.VERSION.SDK_INT >= 28) {
                PackageInfo p = c.getPackageManager().getPackageInfo(c.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
                sigs = p.signingInfo.hasMultipleSigners() ? p.signingInfo.getApkContentsSigners() : p.signingInfo.getSigningCertificateHistory();
            } else sigs = c.getPackageManager().getPackageInfo(c.getPackageName(), PackageManager.GET_SIGNATURES).signatures;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (Signature s : sigs) { StringBuilder b = new StringBuilder(); for (byte x : md.digest(s.toByteArray())) b.append(String.format("%02x", x)); if (OFFICIAL.equals(b.toString())) ok = true; }
        } catch (Exception ignored) {}
        return official = ok;
    }
}
