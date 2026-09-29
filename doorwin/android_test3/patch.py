# test3: 主程序框架不变，只改加载目标为 hello.html
p = 'java/com/doorwin/app/MainActivity.java'
src = open(p, encoding='utf-8').read()
src = src.replace('file:///android_asset/index.html', 'file:///android_asset/hello.html')
open(p, 'w', encoding='utf-8').write(src)
# 改名
p2 = 'AndroidManifest.xml'
m = open(p2, encoding='utf-8').read()
m = m.replace('android:label="门窗效果图"', 'android:label="测试3-框架"')
open(p2, 'w', encoding='utf-8').write(m)
print('test3 patched')
