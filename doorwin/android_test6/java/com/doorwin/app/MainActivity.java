package com.doorwin.app;

import android.app.Activity;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * 门窗效果图 App 外壳（HTTP 版）：
 * 内置仅监听 127.0.0.1 的迷你 HTTP 服务器，从 assets 提供网页资源，
 * 页面以标准 http:// 协议加载，避免个别机型对 file:// 加载的兼容性问题。
 */
public class MainActivity extends Activity {

    private WebView webView;
    private TextView splash;
    private ServerSocket server;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, final Throwable t) {
                try {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showError(t);
                        }
                    });
                    Thread.sleep(10000);
                } catch (Exception e) { /* ignore */ }
                System.exit(2);
            }
        });
        try {
            startApp();
        } catch (Throwable t) {
            showError(t);
        }
    }

    private void startApp() throws Exception {
        webView = new WebView(this);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);       // localStorage：自建款式/价格配置可持久化
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setSupportZoom(false);
        webView.setBackgroundColor(Color.parseColor("#0f1722"));

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView view, int errorCode,
                                        String description, String failingUrl) {
                view.loadData("<html><body style='background:#300000;color:#fff;font-family:sans-serif;padding:24px'>" +
                        "<h2>页面加载失败</h2><p>" + description + "</p></body></html>",
                        "text/html", "UTF-8");
            }
        });

        webView.addJavascriptInterface(new AndroidBridge(), "Android");

        splash = new TextView(this);
        splash.setBackgroundColor(Color.parseColor("#0f1722"));
        splash.setTextColor(Color.WHITE);
        splash.setGravity(Gravity.CENTER);
        splash.setText("门窗效果图\n正在启动…");
        FrameLayout root = new FrameLayout(this);
        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(splash, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);

        // 内置本地 HTTP 服务（仅本机回环地址可访问）
        server = new ServerSocket(8765, 50, InetAddress.getByName("127.0.0.1"));
        server.setReuseAddress(true);
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (server != null && !server.isClosed()) {
                    try {
                        final Socket client = server.accept();
                        handleRequest(client);
                    } catch (Exception e) { /* 关闭时退出 */ }
                }
            }
        }).start();

        webView.loadUrl("http://127.0.0.1:8765/index.html");
    }

    private void handleRequest(Socket client) {
        try {
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(client.getInputStream(), "UTF-8"));
            String line = in.readLine();
            String path = "/index.html";
            if (line != null && line.startsWith("GET ")) {
                path = line.split(" ")[1];
            }
            while (line != null && line.length() > 0) line = in.readLine(); // 读完请求头

            OutputStream out = client.getOutputStream();
            if (path.equals("/")) path = "/index.html";
            if (path.startsWith("/")) path = path.substring(1);
            try {
                InputStream asset = getAssets().open(path);
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] tmp = new byte[8192];
                int n;
                while ((n = asset.read(tmp)) > 0) buf.write(tmp, 0, n);
                asset.close();
                byte[] data = buf.toByteArray();
                String mime = mimeOf(path);
                out.write(("HTTP/1.1 200 OK\r\nContent-Type: " + mime +
                        "\r\nContent-Length: " + data.length +
                        "\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));
                out.write(data);
            } catch (Exception e) {
                out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                        .getBytes("UTF-8"));
            }
            out.flush();
            client.close();
        } catch (Exception e) { /* ignore */ }
    }

    private String mimeOf(String path) {
        if (path.endsWith(".html")) return "text/html; charset=utf-8";
        if (path.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (path.endsWith(".css")) return "text/css; charset=utf-8";
        if (path.endsWith(".json")) return "application/json; charset=utf-8";
        if (path.endsWith(".svg")) return "image/svg+xml";
        if (path.endsWith(".png")) return "image/png";
        return "application/octet-stream";
    }

    private void showError(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        TextView tv = new TextView(this);
        tv.setTextColor(Color.WHITE);
        tv.setBackgroundColor(Color.parseColor("#300000"));
        tv.setPadding(40, 40, 40, 40);
        tv.setText("启动失败，请截图或复制以下内容反馈：\n\n" + sw.toString());
        tv.setTextIsSelectable(true);
        setContentView(tv);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        try {
            if (server != null) server.close();
        } catch (Exception e) { /* ignore */ }
        super.onDestroy();
    }

    /** JS 桥 */
    public class AndroidBridge {

        /** 更新/隐藏启动屏文字 */
        @JavascriptInterface
        public void setSplash(final String text, final boolean hide) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (splash == null) return;
                    if (hide) {
                        splash.setVisibility(View.GONE);
                    } else {
                        splash.setVisibility(View.VISIBLE);
                        splash.setText(text);
                    }
                }
            });
        }

        /** 保存导出图片到相册，返回 "OK|位置" 或 "ERR|原因" */
        @JavascriptInterface
        public String saveImage(String dataUrl) {
            try {
                String b64 = dataUrl.substring(dataUrl.indexOf(',') + 1);
                byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
                Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (bmp == null) return "ERR|图片解码失败";

                String fileName = "门窗_" + System.currentTimeMillis() + ".png";
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
                    v.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                    v.put(MediaStore.Images.Media.RELATIVE_PATH,
                            Environment.DIRECTORY_PICTURES + "/门窗效果图");
                    Uri uri = getContentResolver().insert(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
                    if (uri == null) return "ERR|相册写入失败";
                    OutputStream out = getContentResolver().openOutputStream(uri);
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
                    out.close();
                    return "OK|相册/Pictures/门窗效果图";
                } else {
                    File dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
                    File f = new File(dir, fileName);
                    FileOutputStream out = new FileOutputStream(f);
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
                    out.close();
                    MediaScannerConnection.scanFile(MainActivity.this,
                            new String[]{f.getAbsolutePath()}, null, null);
                    return "OK|" + f.getAbsolutePath();
                }
            } catch (Exception e) {
                return "ERR|" + e.getMessage();
            }
        }
    }
}
