# 测试9 补丁：build.bat 路径 + JS 侧日志埋点
b = open('build.bat', encoding='utf-8').read()
b = b.replace('java\\com\\doorwin\\app\\MainActivity.java',
              'java\\com\\doorwin\\test9\\MainActivity.java')
b = b.replace('build\\classes\\com\\doorwin\\app\\MainActivity.class',
              'build\\classes\\com\\doorwin\\test9\\MainActivity.class')
open('build.bat', 'w', encoding='utf-8').write(b)

# hello.html 加日志
h = open('assets/hello.html', encoding='utf-8').read()
h = h.replace(
    "  document.body.innerHTML = '✅ 文件加载 OK<br>✅ 本地存储 OK<br>✅ 页面脚本 OK';",
    "  document.body.innerHTML = '✅ 文件加载 OK<br>✅ 本地存储 OK<br>✅ 页面脚本 OK';\n" +
    "  if (window.Android && window.Android.log) window.Android.log('hello页脚本运行 OK');"
)
open('assets/hello.html', 'w', encoding='utf-8').write(h)

# main.js 加关键点日志
js = open('assets/js/main.js', encoding='utf-8').read()
jslog = "  function jslog(m) { try { if (window.Android && window.Android.log) window.Android.log(m); } catch (e) {} }\n"
js = js.replace(
    "  function boot() {\n    loadCustomStyles();",
    jslog + "  function boot() {\n    jslog('JS boot 开始');\n    loadCustomStyles();"
)
js = js.replace(
    "      splash('正在初始化 3D 引擎…');",
    "      splash('正在初始化 3D 引擎…');\n      jslog('3D 初始化前');"
)
js = js.replace(
    "      SceneManager.init($('view3d'));",
    "      SceneManager.init($('view3d'));\n      jslog('3D 初始化后');"
)
js = js.replace(
    "        localStorage.removeItem('doorwin-3d-attempt');\n        splash('', true);",
    "        localStorage.removeItem('doorwin-3d-attempt');\n        jslog('首帧完成');\n        splash('', true);"
)
open('assets/js/main.js', 'w', encoding='utf-8').write(js)
print('patched ok')
