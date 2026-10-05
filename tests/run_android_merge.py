"""Run the real Android SQLite merger against synthetic receiver data.
Usage: python3 tests/run_android_merge.py emulator-5560
Requires the sibling tidyTIVI checkout and the normal Android build toolchain.
"""
from pathlib import Path
import os, shutil, subprocess, sys, tempfile, time, uuid
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT.parent))
from tidyTIVI.tests.test_merge import MergeTests
serial=sys.argv[1]
if not serial.startswith('emulator-'):raise SystemExit('This test runner only targets an emulator.')
sdk=Path(os.environ.get('ANDROID_SDK_ROOT',str(Path.home()/'Library/Android/sdk')))
jdk=Path(os.environ.get('JAVA_HOME','/Applications/Android Studio.app/Contents/jbr/Contents/Home'))
bt=sdk/'build-tools'/os.environ.get('ANDROID_BUILD_TOOLS','36.0.0')
jar=sdk/'platforms'/os.environ.get('ANDROID_PLATFORM','android-37.0')/'android.jar'
adb=[str(sdk/'platform-tools/adb'),'-s',serial]
env=dict(os.environ,JAVA_HOME=str(jdk),PATH=str(jdk/'bin')+os.pathsep+os.environ['PATH'])
def run(args,**kw):return subprocess.run(list(map(str,args)),env=env,check=True,**kw)
with tempfile.TemporaryDirectory(prefix='tidytivi-android-merge-') as tmp:
 p=Path(tmp);(p/'assets').mkdir();(p/'classes').mkdir()
 t=MergeTests();t.setUp()
 try:
  shutil.copyfile(t.receiver,p/'assets/receiver.db');shutil.copyfile(t.incoming,p/'assets/incoming.db')
 finally:t.doCleanups()
 (p/'AndroidManifest.xml').write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="ayala.tidytivi.mergetest"><uses-sdk android:minSdkVersion="23" android:targetSdkVersion="28"/><application android:label="Merge regression test" android:theme="@android:style/Theme.Material.NoActionBar"><activity android:name="ayala.tidytivi.companion.MergeTestActivity" android:exported="true"/></application></manifest>')
 run([jdk/'bin/javac','--release','8','-classpath',jar,'-d',p/'classes',ROOT/'src/ayala/tidytivi/companion/CurationMerge.java',ROOT/'src/ayala/tidytivi/companion/VodMerge.java',ROOT/'tests/MergeTestActivity.java'])
 run([bt/'d8','--min-api','23','--lib',jar,'--output',p,*p.glob('classes/**/*.class')])
 run([bt/'aapt','package','-f','-M',p/'AndroidManifest.xml','-I',jar,'-A',p/'assets','-F',p/'unsigned.apk'])
 run([bt/'aapt','add','unsigned.apk','classes.dex'],cwd=p)
 run([bt/'zipalign','-f','4',p/'unsigned.apk',p/'aligned.apk'])
 run([jdk/'bin/keytool','-genkeypair','-keystore',p/'test.jks','-storepass','android','-alias','test','-keyalg','RSA','-validity','2','-dname','CN=Local Synthetic Merge Test'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
 run([bt/'apksigner','sign','--ks',p/'test.jks','--ks-pass','pass:android','--out',p/'test.apk',p/'aligned.apk'])
 # This disposable test package contains synthetic fixtures only.
 subprocess.run(adb+['uninstall','ayala.tidytivi.mergetest'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
 run(adb+['install',str(p/'test.apk')]);run_id=uuid.uuid4().hex
 run(adb+['shell','am','start','-n','ayala.tidytivi.mergetest/ayala.tidytivi.companion.MergeTestActivity','--es','run_id',run_id])
 for attempt in range(30):
  output=subprocess.check_output(adb+['logcat','-d','-t','1000','-s','MergeRegression:I','*:S'],text=True)
  if run_id+' FAIL:' in output:raise SystemExit(output)
  if run_id+' PASS:' in output:print(next(line for line in output.splitlines() if run_id+' PASS:' in line));break
  time.sleep(1)
 else:raise SystemExit('No test result within 30 seconds.')
