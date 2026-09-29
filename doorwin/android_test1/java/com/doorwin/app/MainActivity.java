package com.doorwin.app;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;

/** 测试1：最小 WebView 空白页 —— 验证设备 WebView 基础能力 */
public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WebView webView = new WebView(this);
        setContentView(webView);
        webView.loadData("<html><head><meta name='viewport' content='width=device-width'>" +
                "<style>body{background:#0f1722;color:#fff;font-family:sans-serif;" +
                "text-align:center;padding-top:40%;font-size:18px}</style></head>" +
                "<body>✅ 测试页正常<br><br>看到这行字 = 设备 WebView 基础功能 OK" +
                "<br><br>问题出在 3D/页面加载环节</body></html>", "text/html", "UTF-8");
    }
}
