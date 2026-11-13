package org.op2r.pocketplayer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Finds album covers by artist + album, since Spotify's media session carries no art.
 * Order: on-watch cache, Deezer (no key), iTunes (no key). Covers are cached per album
 * in files/art/, so downloaded music keeps its cover offline after the first lookup.
 * Blocking: call from a background thread.
 */
final class ArtFetcher {

    private static final String TAG = "OP2R";
    /** The cover is a dimmed background, so 320 px (screen is 466) is plenty. */
    private static final int SIZE = 320;
    private static final int WEBP_QUALITY = 60;
    /** Cache limits: oldest-played covers go first above 10 MB, and any cover unplayed for 90 days. */
    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final long MAX_AGE_MS = 90L * 24 * 60 * 60 * 1000;

    private ArtFetcher() {}

    static Bitmap get(Context c, String artist, String album) {
        if (isBlank(artist) || isBlank(album)) return null;
        File file = cacheFile(c, artist, album);
        if (file.exists()) {
            Bitmap cached = BitmapFactory.decodeFile(file.getPath());
            if (cached != null) {
                file.setLastModified(System.currentTimeMillis()); // "last played", for eviction
                Log.i(TAG, "art: cache hit " + album);
                return cached;
            }
        }
        String url = null;
        try {
            url = deezer(artist, album);
            if (url == null) url = itunes(artist, album);
        } catch (Exception e) {
            Log.i(TAG, "art: lookup failed (offline?) " + e);
            return null;
        }
        if (url == null) {
            Log.i(TAG, "art: not found for " + artist + " / " + album);
            return null;
        }
        try {
            byte[] bytes = download(url);
            Bitmap full = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (full == null) return null;
            Bitmap scaled = Bitmap.createScaledBitmap(full, SIZE, SIZE, true);
            file.getParentFile().mkdirs();
            try (FileOutputStream out = new FileOutputStream(file)) {
                scaled.compress(Bitmap.CompressFormat.WEBP_LOSSY, WEBP_QUALITY, out);
            }
            Log.i(TAG, "art: downloaded " + url + " -> " + file.length() / 1024 + " KB");
            trim(file.getParentFile());
            return scaled;
        } catch (Exception e) {
            Log.i(TAG, "art: download failed " + e);
            return null;
        }
    }

    private static String deezer(String artist, String album) throws Exception {
        String q = "artist:\"" + artist + "\" album:\"" + album + "\"";
        JSONArray data = new JSONObject(fetch("https://api.deezer.com/search/album?limit=5&q="
                + enc(q))).optJSONArray("data");
        if (data == null || data.length() == 0) return null;
        // Prefer an exact album-title match, else the first hit.
        for (int i = 0; i < data.length(); i++) {
            JSONObject a = data.getJSONObject(i);
            if (album.equalsIgnoreCase(a.optString("title"))) return emptyToNull(a.optString("cover_xl"));
        }
        return emptyToNull(data.getJSONObject(0).optString("cover_xl"));
    }

    private static String itunes(String artist, String album) throws Exception {
        JSONArray results = new JSONObject(fetch("https://itunes.apple.com/search?entity=album&limit=5&term="
                + enc(artist + " " + album))).optJSONArray("results");
        if (results == null || results.length() == 0) return null;
        JSONObject pick = results.getJSONObject(0);
        for (int i = 0; i < results.length(); i++) {
            JSONObject a = results.getJSONObject(i);
            if (album.equalsIgnoreCase(a.optString("collectionName"))) { pick = a; break; }
        }
        String small = pick.optString("artworkUrl100");
        return small.isEmpty() ? null : small.replace("100x100", "600x600");
    }

    private static String fetch(String url) throws Exception {
        return new String(download(url), StandardCharsets.UTF_8);
    }

    private static byte[] download(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(8000);
        con.setReadTimeout(8000);
        con.setRequestProperty("User-Agent", "PocketControls/0.2");
        try (InputStream in = con.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        } finally {
            con.disconnect();
        }
    }

    private static File cacheFile(Context c, String artist, String album) {
        return new File(new File(c.getFilesDir(), "art"), sha1(artist.toLowerCase() + "|" + album.toLowerCase()) + ".webp");
    }

    /** Deletes old-format files, covers unplayed for MAX_AGE_MS, then oldest-played until under MAX_BYTES. */
    static void trim(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;
        long now = System.currentTimeMillis();
        java.util.List<File> keep = new java.util.ArrayList<>();
        long total = 0;
        int removed = 0;
        for (File f : files) {
            if (!f.getName().endsWith(".webp") || now - f.lastModified() > MAX_AGE_MS) {
                if (f.delete()) removed++;
            } else {
                keep.add(f);
                total += f.length();
            }
        }
        keep.sort((a, b) -> Long.compare(a.lastModified(), b.lastModified())); // oldest first
        for (int i = 0; total > MAX_BYTES && i < keep.size(); i++) {
            total -= keep.get(i).length();
            if (keep.get(i).delete()) removed++;
        }
        Log.i(TAG, "art: cache " + (total / 1024) + " KB, removed " + removed);
    }

    private static String sha1(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    private static String enc(String s) throws Exception {
        return URLEncoder.encode(s, "UTF-8");
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }
}
