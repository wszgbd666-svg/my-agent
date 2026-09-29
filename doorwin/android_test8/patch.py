b = open('build.bat', encoding='utf-8').read()
b = b.replace('java\\com\\doorwin\\app\\MainActivity.java',
              'java\\com\\doorwin\\test7\\MainActivity.java')
b = b.replace('build\\classes\\com\\doorwin\\app\\MainActivity.class',
              'build\\classes\\com\\doorwin\\test7\\MainActivity.class')
open('build.bat', 'w', encoding='utf-8').write(b)
print('patched ok')
