package com.doorwin.test9;

import android.app.Activity;
import android.content.ContentValues;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.Gravity;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * 测试9：日志诊断版。
 * 每一步都写入日志文件（内部 + 下载目录 doorwin-log.txt），
 * 同时用 postDelayed 更新屏幕（不阻塞主线程）。
 * 闪退后，日志最后一行即崩溃点。
 */
public class MainActivity extends Activity {

    private WebView webView;
    private TextView splash;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Uri logUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, final Throwable t) {
                log("!! 未捕获异常: " + t);
                log("!! " + stack(t));
                try {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() { showError(t); }
                    });
                    Thread.sleep(8000);
                } catch (Exception e) { /* ignore */ }
                System.exit(2);
            }
        });
        try {
            run();
        } catch (Throwable t) {
            log("!! 启动异常: " + t);
            log("!! " + stack(t));
            showError(t);
        }
    }

    private void run() {
        log("onCreate 开始");

        splash = new TextView(this);
        splash.setBackgroundColor(Color.parseColor("#0f1722"));
        splash.setTextColor(Color.WHITE);
        splash.setGravity(Gravity.CENTER);
        splash.setTextSize(16);
        splash.setText("诊断中…\n日志位置：下载目录 doorwin-log.txt");
        setContentView(splash);
        log("启动屏显示");

        step(1, "创建 WebView", new Runnable() {
            @Override
            public void run() {
                webView = new WebView(MainActivity.this);
                log("WebView 创建 OK");
            }
        });

        step(2, "网页设置", new Runnable() {
            @Override
            public void run() {
                WebSettings s = webView.getSettings();
                s.setJavaScriptEnabled(true);
                log("JavaScript 开启 OK");
                s.setDomStorageEnabled(true);
                log("本地存储开启 OK");
                s.setAllowFileAccess(true);
                s.setAllowContentAccess(true);
                log("文件访问开启 OK");
            }
        });

        step(3, "JS 桥接", new Runnable() {
            @Override
            public void run() {
                webView.addJavascriptInterface(new AndroidBridge(), "Android");
                log("桥接注册 OK");
            }
        });

        step(4, "网络客户端", new Runnable() {
            @Override
            public void run() {
                webView.setWebViewClient(new WebViewClient());
                log("WebViewClient OK");
            }
        });

        step(5, "加入界面", new Runnable() {
            @Override
            public void run() {
                FrameLayout root = new FrameLayout(MainActivity.this);
                root.addView(webView, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
                root.addView(splash, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
                setContentView(root);
                log("界面叠加 OK");
            }
        });

        step(6, "内嵌页面", new Runnable() {
            @Override
            public void run() {
                webView.loadData("<html><body style='background:#0f1722;color:#fff;font-size:18px;" +
                        "text-align:center;padding-top:40%'>✅ 内嵌页面正常</body></html>",
                        "text/html", "UTF-8");
                log("loadData 已发出");
            }
        });

        step(7, "file 资源页", new Runnable() {
            @Override
            public void run() {
                webView.loadUrl("file:///android_asset/hello.html");
                log("loadUrl(hello.html) 已发出");
            }
        });

        step(8, "正式页面", new Runnable() {
            @Override
            public void run() {
                webView.loadUrl("file:///android_asset/index.html");
                log("loadUrl(index.html) 已发出");
            }
        });

        step(9, "完成", new Runnable() {
            @Override
            public void run() {
                log("全部步骤执行完毕（页面可能还在加载）");
            }
        });
    }

    /** 顺序执行步骤：先显示文字，稍后执行，绝不阻塞主线程 */
    private void step(final int n, final String name, final Runnable action) {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (splash != null) {
                    splash.setText("诊断中 第" + n + "/9步：" + name + "\n日志位置：下载目录 doorwin-log.txt");
                }
            }
        }, (n - 1) * 1300L);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                log(">> 第" + n + "步：" + name);
                action.run();
            }
        }, (n - 1) * 1300L + 300L);
    }

    private String stack(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        String s = sw.toString();
        return s.length() > 600 ? s.substring(0, 600) : s;
    }

    private void showError(Throwable t) {
        TextView tv = new TextView(this);
        tv.setTextColor(Color.WHITE);
        tv.setBackgroundColor(Color.parseColor("#300000"));
        tv.setPadding(40, 40, 40, 40);
        tv.setText("出错：" + t + "\n\n日志位置：下载目录 doorwin-log.txt");
        tv.setTextIsSelectable(true);
        setContentView(tv);
    }

    /* ---------- 日志：内部文件 + 下载目录（每次全量覆盖同一文件） ---------- */
    private String logBuf = "";

    private void log(String msg) {
        logBuf += msg + "\n";
        try {
            File f = new File(getFilesDir(), "log.txt");
            FileOutputStream fos = new FileOutputStream(f);
            fos.write(logBuf.getBytes("UTF-8"));
            fos.close();
        } catch (Exception e) { /* ignore */ }
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                if (logUri == null) {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.Downloads.DISPLAY_NAME, "doorwin-log.txt");
                    v.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
                    v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    v.put(MediaStore.Downloads.IS_PENDING, 1);
                    logUri = getContentResolver().insert(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                }
                if (logUri != null) {
                    OutputStream os = getContentResolver().openOutputStream(logUri, "rwt");
                    os.write(logBuf.getBytes("UTF-8"));
                    os.close();
                    // 清除 Pending 标记，让文件管理器可见
                    ContentValues clear = new ContentValues();
                    clear.put(MediaStore.Downloads.IS_PENDING, 0);
                    getContentResolver().update(logUri, clear, null, null);
                }
            } else {
                File pub = new File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        "doorwin-log.txt");
                FileOutputStream fos2 = new FileOutputStream(pub);
                fos2.write(logBuf.getBytes("UTF-8"));
                fos2.close();
            }
        } catch (Exception e) { /* ignore */ }
    }

    /* ---------- JS 桥 ---------- */
    public class AndroidBridge {
        @JavascriptInterface
        public void log(String msg) {
            log("JS: " + msg);
        }

        @JavascriptInterface
        public void setSplash(final String text, final boolean hide) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (splash == null) return;
                    splash.setText(text);
                }
            });
        }
    }
}
