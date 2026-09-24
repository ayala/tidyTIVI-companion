"""Build a signed APK with an installed Android SDK and an external keystore."""
from pathlib import Path
import os
import hashlib
import shutil
import subprocess

ROOT = Path(__file__).resolve().parent
sdk = Path(os.environ.get('ANDROID_SDK_ROOT') or os.environ.get('ANDROID_HOME') or Path.home() / 'Library/Android/sdk')
java_home = os.environ.get('JAVA_HOME')
if not java_home:
    mac_jdk = Path('/Applications/Android Studio.app/Contents/jbr/Contents/Home')
    if mac_jdk.is_dir():
        java_home = str(mac_jdk)
    elif shutil.which('javac'):
        java_home = str(Path(shutil.which('javac')).resolve().parent.parent)
    else:
        raise SystemExit('Set JAVA_HOME to a JDK installation.')
java = Path(java_home) / 'bin'
build_tools = sdk / 'build-tools' / os.environ.get('ANDROID_BUILD_TOOLS', '36.0.0')
android_jar = sdk / 'platforms' / os.environ.get('ANDROID_PLATFORM', 'android-37.0') / 'android.jar'
key = Path(os.environ.get('TIDYTIVI_SIGNING_KEY', str(Path.home() / '.local/share/tidytivi/development.jks'))).expanduser()
alias = os.environ.get('TIDYTIVI_KEY_ALIAS', 'tidytivi')
env = dict(os.environ, JAVA_HOME=java_home, PATH=str(java) + os.pathsep + os.environ.get('PATH', ''))
env.setdefault('TIDYTIVI_STORE_PASSWORD', 'android')
env.setdefault('TIDYTIVI_KEY_PASSWORD', env['TIDYTIVI_STORE_PASSWORD'])

def run(args, cwd=ROOT):
    subprocess.run(list(map(str, args)), cwd=cwd, env=env, check=True)

if not android_jar.is_file() or not (build_tools / 'aapt').is_file():
    raise SystemExit('Install the configured Android platform/build tools or set ANDROID_PLATFORM and ANDROID_BUILD_TOOLS.')
if not key.exists():
    if 'TIDYTIVI_SIGNING_KEY' in os.environ:
        raise SystemExit('The configured signing key does not exist; refusing to replace it.')
    key.parent.mkdir(parents=True, exist_ok=True)
    run([java / 'keytool', '-genkeypair', '-keystore', key,
         '-storepass:env', 'TIDYTIVI_STORE_PASSWORD', '-keypass:env', 'TIDYTIVI_KEY_PASSWORD',
         '-alias', alias, '-keyalg', 'RSA', '-keysize', '3072', '-validity', '10000',
         '-dname', 'CN=tidyTIVI Development'])
    key.chmod(0o600)
dependency = ROOT / 'libs/zxing-core-3.5.3.jar'
if hashlib.sha256(dependency.read_bytes()).hexdigest() != '8d8064c1636fdaef7189dd9055c7d59950a8940a12f2293956446ec3c109fd82':
    raise SystemExit('ZXing dependency checksum mismatch.')
for name in ('gen', 'classes', 'build'):
    path = ROOT / name
    if path.exists():
        shutil.rmtree(path)
    path.mkdir()
run([build_tools / 'aapt', 'package', '-f', '-M', 'AndroidManifest.xml', '-I', android_jar,
     '-S', 'res', '-A', 'assets', '-J', 'gen', '-F', 'build/unsigned.apk'])
run([java / 'javac', '--release', '8', '-classpath', str(android_jar) + os.pathsep + str(dependency), '-d', 'classes',
     *ROOT.glob('src/**/*.java'), *ROOT.glob('gen/**/*.java')])
run([build_tools / 'd8', '--min-api', '23', '--lib', android_jar, '--output', 'build',
     *ROOT.glob('classes/**/*.class'), dependency])
run([build_tools / 'aapt', 'add', 'unsigned.apk', 'classes.dex'], cwd=ROOT / 'build')
run([build_tools / 'zipalign', '-f', '4', 'build/unsigned.apk', 'build/aligned.apk'])
run([build_tools / 'apksigner', 'sign', '--ks', key, '--ks-key-alias', alias,
     '--ks-pass', 'env:TIDYTIVI_STORE_PASSWORD', '--key-pass', 'env:TIDYTIVI_KEY_PASSWORD',
     '--out', 'build/tidyTIVI-companion.apk', 'build/aligned.apk'])
run([build_tools / 'apksigner', 'verify', 'build/tidyTIVI-companion.apk'])
print(ROOT / 'build/tidyTIVI-companion.apk')
