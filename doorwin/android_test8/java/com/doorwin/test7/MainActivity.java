package com.doorwin.test7;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * 测试7：阶梯诊断。
 * 每一步都先更新屏幕文字再执行，若闪退，最后停留的文字即崩溃点。
 */
public class MainActivity extends Activity {

    private WebView webView;
    private TextView splash;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, final Throwable t) {
                try {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() { showError(t); }
                    });
                    Thread.sleep(12000);
                } catch (Exception e) { /* ignore */ }
                System.exit(2);
            }
        });
        try {
            ladder();
        } catch (Throwable t) {
            showError(t);
        }
    }

    private void ladder() throws Exception {
        // 第 0 步：启动屏
        splash = new TextView(this);
        splash.setBackgroundColor(Color.parseColor("#0f1722"));
        splash.setTextColor(Color.WHITE);
        splash.setGravity(Gravity.CENTER);
        splash.setTextSize(17);
        setContentView(splash);
        mark("第0步：应用启动 OK");

        // 第 1 步：创建 WebView（不设置任何选项）
        webView = new WebView(this);
        mark("第1步：WebView 创建 OK");

        // 第 2 步：启动屏盖层（FrameLayout 叠加）
        FrameLayout root = new FrameLayout(this);
        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(splash, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);
        mark("第2步：界面叠加 OK");

        // 第 3 步：开启 JavaScript
        webView.getSettings().setJavaScriptEnabled(true);
        mark("第3步：JavaScript OK");

        // 第 4 步：开启本地存储
        webView.getSettings().setDomStorageEnabled(true);
        mark("第4步：本地存储 OK");

        // 第 5 步：文件访问
        WebSettings s = webView.getSettings();
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        mark("第5步：文件访问 OK");

        // 第 6 步：JS 桥接
        webView.addJavascriptInterface(new AndroidBridge(), "Android");
        mark("第6步：桥接 OK");

        // 第 7 步：WebViewClient
        webView.setWebViewClient(new WebViewClient());
        mark("第7步：网络客户端 OK");

        // 第 8 步：内嵌页面加载（loadData）
        webView.loadData("<html><body style='background:#0f1722;color:#fff;font-size:18px;" +
                "text-align:center;padding-top:40%'>✅ loadData 加载 OK</body></html>",
                "text/html", "UTF-8");
        mark("第8步：内嵌页面 OK");
        Thread.sleep(1500);

        // 第 9 步：file:// 资源加载
        webView.loadUrl("file:///android_asset/hello.html");
        mark("第9步：file 资源加载已发出");
        Thread.sleep(2000);
        mark("第10步：全部通过 ✅");
    }

    private void mark(String text) {
        splash.setText("阶梯诊断\n\n" + text);
        try { Thread.sleep(900); } catch (Exception e) { /* ignore */ }
    }

    private void showError(Throwable t) {
        TextView tv = new TextView(this);
        tv.setTextColor(Color.WHITE);
        tv.setBackgroundColor(Color.parseColor("#300000"));
        tv.setPadding(40, 40, 40, 40);
        tv.setText("出错：" + t + "\n\n请把本页截图反馈");
        tv.setTextIsSelectable(true);
        setContentView(tv);
    }

    public class AndroidBridge {
        @JavascriptInterface
        public void setSplash(String text, boolean hide) {
            if (splash != null) splash.setText("页面调用桥接成功：" + text);
        }
    }
}
