package com.jianji.app;

import android.Manifest;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.webkit.WebView;

import androidx.activity.result.ActivityResult;
import androidx.core.content.FileProvider;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 分享导入桥接插件：
 * - MainActivity 收到系统分享/打开的 JSON 后调用 receive(json) 暂存并通知页面
 * - 收到系统分享的图片后调用 receiveImage(path, mime) 暂存并通知页面
 * - 冷启动时页面可能尚未加载，页面就绪后通过 takePending() 拉取暂存数据
 * - exportJson(): 将 JSON 写入系统下载目录（Android 10+），旧版本写入应用目录并弹出系统分享
 * - ensureImagePermission(): 选图前申请媒体权限（部分 ROM 相册按权限过滤，未授权显示为空）
 * - readPendingImage(): 读取暂存分享图片，base64 返回给页面
 */
@CapacitorPlugin(name = "SharedImport")
public class SharedImportPlugin extends Plugin {

    private String pending;
    private String pendingImagePath;
    private String pendingImageMime;

    @PluginMethod
    public void takePending(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("json", pending);
        ret.put("imagePath", pendingImagePath);
        pending = null;
        pendingImagePath = null;
        call.resolve(ret);
    }

    /** 选图前确保媒体读取权限（Android 13+ 用 READ_MEDIA_IMAGES，旧版本用 READ_EXTERNAL_STORAGE） */
    @PluginMethod
    public void ensureImagePermission(PluginCall call) {
        String perm = imagePermission();
        if (hasPermission(perm)) {
            JSObject ret = new JSObject();
            ret.put("granted", true);
            call.resolve(ret);
            return;
        }
        requestPermissionForAlias("images", call, "imagePermCallback");
    }

    private String imagePermission() {
        return Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    /**
     * 原生选图：先确保权限，再拉起系统选择器（支持多选）。
     * WebView 的 file input 在异步授权后会丢失用户手势导致点击无效，因此选图必须走原生。
     */
    @PluginMethod
    public void pickImages(PluginCall call) {
        if (hasPermission(imagePermission())) {
            launchImagePicker(call);
        } else {
            requestPermissionForAlias("images", call, "pickPermCallback");
        }
    }

    private void launchImagePicker(PluginCall call) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(call, intent, "pickResult");
    }

    @PermissionCallback
    private void pickPermCallback(PluginCall call) {
        if (hasPermission(imagePermission())) {
            launchImagePicker(call);
        } else {
            call.reject("未授权照片权限");
        }
    }

    @PermissionCallback
    private void imagePermCallback(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("granted", hasPermission(imagePermission()));
        call.resolve(ret);
    }

    /** 系统选择器返回：读取所选图片（多选走 ClipData），base64 返回给页面 */
    @ActivityCallback
    private void pickResult(PluginCall call, ActivityResult result) {
        if (call == null) return;
        JSArray images = new JSArray();
        Intent data = result != null ? result.getData() : null;
        if (data != null) {
            ClipData clip = data.getClipData();
            if (clip != null) {
                for (int i = 0; i < clip.getItemCount(); i++) {
                    JSObject img = readImageBase64(clip.getItemAt(i).getUri());
                    if (img != null) images.put(img);
                }
            } else if (data.getData() != null) {
                JSObject img = readImageBase64(data.getData());
                if (img != null) images.put(img);
            }
        }
        JSObject ret = new JSObject();
        ret.put("images", images);
        call.resolve(ret);
    }

    /**
     * 单张图片读取为压缩后的 JPEG base64：
     * 长边压到 2000px（保证订单文字可识别），质量 82。
     * 原图整张 base64（可达 12MB+）多张同时传给 WebView 会撑爆内存导致闪退。
     */
    private JSObject readImageBase64(Uri uri) {
        try {
            byte[] raw = readBytesLimited(getContext().getContentResolver().openInputStream(uri), 25 * 1024 * 1024);
            if (raw == null) return null;
            byte[] jpeg = compressToJpeg(raw);
            if (jpeg == null) return null;
            JSObject o = new JSObject();
            o.put("data", android.util.Base64.encodeToString(jpeg, android.util.Base64.NO_WRAP));
            o.put("mime", "image/jpeg");
            return o;
        } catch (Throwable e) {
            // 含 OOM：单张失败直接跳过，不让 App 崩溃
            return null;
        }
    }

    /** 解码位图并压缩为长边 ≤2000px、质量 82 的 JPEG；失败返回 null */
    private byte[] compressToJpeg(byte[] src) {
        Bitmap bmp = null;
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(src, 0, src.length, opts);
            if (opts.outWidth <= 0 || opts.outHeight <= 0) return null;

            int maxDim = Math.max(opts.outWidth, opts.outHeight);
            int sample = 1;
            while (maxDim / (sample * 2) >= 2000) sample *= 2;
            opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            bmp = BitmapFactory.decodeByteArray(src, 0, src.length, opts);
            if (bmp == null) return null;

            maxDim = Math.max(bmp.getWidth(), bmp.getHeight());
            if (maxDim > 2000) {
                float scale = 2000f / maxDim;
                Bitmap scaled = Bitmap.createScaledBitmap(bmp,
                        Math.max(1, Math.round(bmp.getWidth() * scale)),
                        Math.max(1, Math.round(bmp.getHeight() * scale)), true);
                if (scaled != bmp) bmp.recycle();
                bmp = scaled;
            }

            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.JPEG, 82, bos);
            return bos.toByteArray();
        } catch (Throwable e) {
            return null;
        } finally {
            if (bmp != null && !bmp.isRecycled()) bmp.recycle();
        }
    }

    private byte[] readBytesLimited(java.io.InputStream in, int max) throws Exception {
        if (in == null) return null;
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            if (bos.size() + n > max) { in.close(); return null; }
            bos.write(buf, 0, n);
        }
        in.close();
        return bos.toByteArray();
    }

    /** 读取暂存的分享图片，压缩后 base64 返回；读取后清除暂存 */
    @PluginMethod
    public void readPendingImage(PluginCall call) {
        if (pendingImagePath == null) {
            call.resolve(new JSObject());
            return;
        }
        try {
            byte[] raw = readBytesLimited(new FileInputStream(new File(pendingImagePath)), 25 * 1024 * 1024);
            String mime = pendingImageMime;
            pendingImagePath = null;
            pendingImageMime = null;
            if (raw == null) {
                call.resolve(new JSObject());
                return;
            }
            byte[] jpeg = compressToJpeg(raw);
            JSObject ret = new JSObject();
            if (jpeg != null) {
                ret.put("data", android.util.Base64.encodeToString(jpeg, android.util.Base64.NO_WRAP));
                ret.put("mime", "image/jpeg");
            } else {
                // 压缩失败时退回原图
                ret.put("data", android.util.Base64.encodeToString(raw, android.util.Base64.NO_WRAP));
                ret.put("mime", mime != null ? mime : "image/jpeg");
            }
            call.resolve(ret);
        } catch (Exception e) {
            pendingImagePath = null;
            call.reject("读取图片失败：" + e.getMessage());
        }
    }

    @PluginMethod
    public void exportJson(PluginCall call) {
        String json = call.getString("json", "");
        String fileName = call.getString("fileName", "jianji-export.json");
        if (json.isEmpty()) {
            call.reject("没有可导出的数据");
            return;
        }
        Context ctx = getContext();
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                // Android 10+：写入公共下载目录（无需存储权限）
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                cv.put(MediaStore.Downloads.MIME_TYPE, "application/json");
                Uri uri = ctx.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                if (uri == null) throw new IllegalStateException("无法创建文件");
                try (OutputStream os = ctx.getContentResolver().openOutputStream(uri)) {
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                }
                JSObject ret = new JSObject();
                ret.put("saved", true);
                ret.put("path", "下载/" + fileName);
                call.resolve(ret);
            } else {
                // Android 9-：写入应用专属目录后弹出系统分享，由用户选择保存位置
                File dir = new File(ctx.getExternalFilesDir(null), "export");
                if (!dir.exists()) dir.mkdirs();
                File f = new File(dir, fileName);
                try (FileOutputStream fos = new FileOutputStream(f)) {
                    fos.write(json.getBytes(StandardCharsets.UTF_8));
                }
                Uri uri = FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".fileprovider", f);
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("application/json");
                send.putExtra(Intent.EXTRA_STREAM, uri);
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                Intent chooser = Intent.createChooser(send, "导出账目数据");
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(chooser);
                JSObject ret = new JSObject();
                ret.put("saved", true);
                ret.put("path", "应用目录/export/" + fileName);
                call.resolve(ret);
            }
        } catch (Exception e) {
            call.reject("导出失败：" + e.getMessage());
        }
    }

    public void receive(String json) {
        pending = json;
        try {
            WebView wv = getBridge().getWebView();
            if (wv != null) {
                wv.post(() -> wv.evaluateJavascript(
                        "window.__onSharedJson&&window.__onSharedJson(" + JSONObject.quote(json) + ")",
                        null));
            }
        } catch (Exception ignored) {
            // 页面未就绪时静默失败，pending 由 takePending 兜底
        }
    }

    public void receiveImage(String path, String mime) {
        pendingImagePath = path;
        pendingImageMime = mime;
        try {
            WebView wv = getBridge().getWebView();
            if (wv != null) {
                wv.post(() -> wv.evaluateJavascript(
                        "window.__onSharedImage&&window.__onSharedImage()",
                        null));
            }
        } catch (Exception ignored) {
            // 页面未就绪时静默失败，pendingImagePath 由 takePending 兜底
        }
    }
}
