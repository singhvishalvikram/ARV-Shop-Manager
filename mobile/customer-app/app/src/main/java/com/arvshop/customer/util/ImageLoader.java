package com.arvshop.customer.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.widget.ImageView;

import com.arvshop.customer.R;
import com.arvshop.customer.core.AppExecutors;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.arvshop.customer.data.remote.HttpClient;

/**
 * Minimal image loader: memory LRU + disk cache + background fetch, downsampled
 * to grid size. Deliberately replaces Glide/Picasso (dependency-minimalism
 * guardrail; the catalog is ~50 small JPEGs already resized ≤400px by the pipeline).
 * View-tag check prevents stale bitmaps landing on recycled ViewHolders (Phase 13).
 */
public class ImageLoader {

    private static final int TARGET_SIZE_PX = 480;

    private final File diskDir;
    private final String baseUrl;
    private final LruCache<String, Bitmap> memory;

    public ImageLoader(File cacheRoot, String baseUrl) {
        this.diskDir = new File(cacheRoot, "images");
        //noinspection ResultOfMethodCallIgnored
        this.diskDir.mkdirs();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        int cacheKb = (int) (Runtime.getRuntime().maxMemory() / 1024 / 8);
        this.memory = new LruCache<String, Bitmap>(cacheKb) {
            @Override
            protected int sizeOf(String key, Bitmap bmp) {
                return bmp.getByteCount() / 1024;
            }
        };
    }

    /** Resolves relative image paths ("/images/x.jpg") against the catalog base URL. */
    public String resolve(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) return "";
        if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) return imageUrl;
        return imageUrl.startsWith("/") ? baseUrl + imageUrl : baseUrl + "/" + imageUrl;
    }

    public void load(String rawImageUrl, ImageView target) {
        String url = resolve(rawImageUrl);
        target.setTag(url);
        if (url.isEmpty()) {
            target.setImageResource(R.drawable.image_placeholder);
            return;
        }
        Bitmap cached = memory.get(url);
        if (cached != null) {
            target.setImageBitmap(cached);
            return;
        }
        target.setImageResource(R.drawable.image_placeholder);
        AppExecutors.get().network().execute(() -> {
            Bitmap bmp = loadBlocking(url);
            if (bmp == null) return;
            memory.put(url, bmp);
            AppExecutors.get().mainThread(() -> {
                // Only bind if this view still wants this URL (RecyclerView reuse).
                if (url.equals(target.getTag())) {
                    target.setImageBitmap(bmp);
                }
            });
        });
    }

    private Bitmap loadBlocking(String url) {
        File file = new File(diskDir, md5(url) + ".img");
        if (!file.isFile()) {
            try {
                byte[] bytes = HttpClient.getBytes(url);
                File tmp = new File(diskDir, file.getName() + ".tmp");
                try (FileOutputStream out = new FileOutputStream(tmp)) {
                    out.write(bytes);
                }
                if (!tmp.renameTo(file)) return decodeSampled(tmp);
            } catch (IOException e) {
                return null; // offline and not cached — placeholder stays
            }
        }
        return decodeSampled(file);
    }

    private Bitmap decodeSampled(File file) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
        int sample = 1;
        while (opts.outWidth / (sample * 2) >= TARGET_SIZE_PX
                || opts.outHeight / (sample * 2) >= TARGET_SIZE_PX) {
            sample *= 2;
        }
        BitmapFactory.Options decode = new BitmapFactory.Options();
        decode.inSampleSize = sample;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), decode);
    }

    private static String md5(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            return new BigInteger(1, md.digest(s.getBytes())).toString(16);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(s.hashCode());
        }
    }
}
