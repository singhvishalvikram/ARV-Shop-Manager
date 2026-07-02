package com.arvshop.admin.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Downscales a captured/picked image and encodes it as a base64 JPEG data URL,
 * matching what the backend's image_storage expects (base64 in `image_base64`).
 * Downscaling client-side keeps uploads small on 3G and respects the server's
 * 5 MB cap. EXIF rotation is honored so photos aren't sideways.
 */
public final class ImageEncoder {

    private static final int MAX_DIMENSION = 1024;
    private static final int JPEG_QUALITY = 80;

    private ImageEncoder() { }

    /** @return a data URL "data:image/jpeg;base64,..." or null if decoding failed. */
    public static String encode(InputStream imageStream, int orientationExif) {
        Bitmap bitmap = BitmapFactory.decodeStream(imageStream);
        if (bitmap == null) return null;
        bitmap = scaleDown(bitmap);
        bitmap = applyOrientation(bitmap, orientationExif);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out);
        String base64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
        return "data:image/jpeg;base64," + base64;
    }

    private static Bitmap scaleDown(Bitmap src) {
        int w = src.getWidth();
        int h = src.getHeight();
        int longest = Math.max(w, h);
        if (longest <= MAX_DIMENSION) return src;
        float ratio = (float) MAX_DIMENSION / longest;
        return Bitmap.createScaledBitmap(src, Math.round(w * ratio), Math.round(h * ratio), true);
    }

    private static Bitmap applyOrientation(Bitmap src, int exifOrientation) {
        int degrees;
        switch (exifOrientation) {
            case ExifInterface.ORIENTATION_ROTATE_90: degrees = 90; break;
            case ExifInterface.ORIENTATION_ROTATE_180: degrees = 180; break;
            case ExifInterface.ORIENTATION_ROTATE_270: degrees = 270; break;
            default: return src;
        }
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), matrix, true);
    }

    public static int readExifOrientation(InputStream in) {
        try {
            return new ExifInterface(in).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        } catch (IOException e) {
            return ExifInterface.ORIENTATION_NORMAL;
        }
    }
}
