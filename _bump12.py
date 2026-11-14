import pathlib

m = pathlib.Path('app/src/main/AndroidManifest.xml')
s = m.read_text(encoding='utf-8')
s = s.replace('android:versionCode="2"', 'android:versionCode="3"')
s = s.replace('android:versionName="1.1"', 'android:versionName="1.2"')
m.write_text(s, encoding='utf-8')

a = pathlib.Path('app/src/main/java/com/junjun/sideback/BackFxActivity.java')
t = a.read_text(encoding='utf-8')
t = t.replace('侧滑返回样式 v1.1 · LSPosed 模块', '侧滑返回样式 v1.2 · LSPosed 模块')
a.write_text(t, encoding='utf-8')
print('bumped to 1.2')
