"""Negative tests: strict checksum rejection and incomplete signing configuration.

Only creates an isolated test project under ignored .tools/. Never alters the real
verification metadata, signing material, or downloaded dependency artifacts.
"""
import json
import os
from pathlib import Path
import subprocess
import shutil

ROOT = Path(__file__).resolve().parents[1]


def main():
    env = os.environ.copy()
    jdk_roots = list((ROOT / '.tools/jdk').glob('*/bin/java.exe'))
    java = str(jdk_roots[0]) if jdk_roots else shutil.which('java')
    assert java, 'JDK 17 is required'
    env['JAVA_HOME'] = str(Path(java).parent.parent)
    env['GRADLE_USER_HOME'] = str(ROOT / '.tools/gradle-home')
    env['ANDROID_USER_HOME'] = str(ROOT / '.tools/android-home')
    env['ANDROID_HOME'] = str(ROOT / '.tools/sdk')
    for key in ('BC_KEYSTORE_PATH', 'BC_KEYSTORE_PASSWORD', 'BC_KEY_ALIAS', 'BC_KEY_PASSWORD'):
        env.pop(key, None)
    command = [java, '-classpath', str(ROOT / 'gradle/wrapper/gradle-wrapper.jar'),
               'org.gradle.wrapper.GradleWrapperMain', '--offline', '--no-daemon', '--dependency-verification', 'strict']
    probe = ROOT / '.tools/negative-verification'
    (probe / 'gradle').mkdir(parents=True, exist_ok=True)
    (probe / 'settings.gradle').write_text("rootProject.name = 'ChecksumRejectionTest'\n")
    (probe / 'build.gradle').write_text("""repositories { mavenCentral() }
configurations { probe }
dependencies { probe('junit:junit:4.13.2') { transitive = false } }
tasks.register('verifyProbe') { doLast { configurations.probe.files } }
""")
    (probe / 'gradle/verification-metadata.xml').write_text('''<verification-metadata xmlns="https://schema.gradle.org/dependency-verification">
<configuration><verify-metadata>false</verify-metadata><verify-signatures>false</verify-signatures></configuration>
<components><component group="junit" name="junit" version="4.13.2"><artifact name="junit-4.13.2.jar">
<sha256 value="''' + '0' * 64 + '''"/></artifact></component></components></verification-metadata>''')
    checksum = subprocess.run(command + ['-p', str(probe), 'verifyProbe'], cwd=ROOT, env=env,
                              capture_output=True, text=True, encoding='utf-8', errors='replace')
    checksum_output = checksum.stdout + checksum.stderr
    env['BC_KEYSTORE_PATH'] = str(ROOT / '.tools/not-a-production-key.jks')
    signing = subprocess.run(command + ['help'], cwd=ROOT, env=env,
                             capture_output=True, text=True, encoding='utf-8', errors='replace')
    signing_output = signing.stdout + signing.stderr
    checks = {
        'tampered_checksum_rejected': checksum.returncode != 0 and 'Dependency verification failed' in checksum_output,
        'partial_signing_configuration_rejected': signing.returncode != 0 and 'Provide all four BC signing environment variables or none' in signing_output,
    }
    evidence = ROOT / 'security/evidence'
    evidence.mkdir(parents=True, exist_ok=True)
    (evidence / 'build-control-tests.json').write_text(json.dumps(checks, indent=2) + '\n')
    # Logs contain the isolated test project path and dummy key path, no real credentials.
    (ROOT / '.tools/checksum-rejection.log').write_text(checksum_output, encoding='utf-8')
    (ROOT / '.tools/partial-signing-rejection.log').write_text(signing_output, encoding='utf-8')
    print(json.dumps(checks, indent=2))
    assert all(checks.values()), 'A negative build-control test failed; inspect .tools logs'


if __name__ == '__main__':
    main()
