# test4: 完整页面但强制禁用 3D（走 2D 安全模式）
p = 'assets/index.html'
h = open(p, encoding='utf-8').read()
h = h.replace('<head>', '<head>\n<script>window.__NO_3D__=true;</script>')
open(p, 'w', encoding='utf-8').write(h)

p2 = 'assets/js/main.js'
js = open(p2, encoding='utf-8').read()
js = js.replace(
    "const noGl = localStorage.getItem('doorwin-nogl') === '1';",
    "const noGl = localStorage.getItem('doorwin-nogl') === '1' || !!window.__NO_3D__;"
)
open(p2, 'w', encoding='utf-8').write(js)

p3 = 'AndroidManifest.xml'
m = open(p3, encoding='utf-8').read()
m = m.replace('android:label="门窗效果图"', 'android:label="测试4-无3D"')
open(p3, 'w', encoding='utf-8').write(m)
print('test4 patched')
