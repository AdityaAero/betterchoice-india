"""Inspect the *built* release APK, XML resources, test evidence and Gradle controls."""
import hashlib
import json
import os
from pathlib import Path
import re
import struct
import subprocess
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
A = '{http://schemas.android.com/apk/res/android}'


def main():
    apk_files = list((ROOT / 'app/build/outputs/apk/release').glob('*.apk'))
    assert len(apk_files) == 1, 'Build exactly one release APK first'
    apk = apk_files[0]
    sdk = Path(os.environ.get('ANDROID_HOME', ROOT / '.tools/sdk'))
    aapt = sdk / 'build-tools/36.0.0/aapt2.exe'
    if not aapt.exists():
        aapt = sdk / 'build-tools/36.0.0/aapt2'
    def run(*args):
        return subprocess.check_output([str(aapt), *args], text=True, encoding='utf-8')
    def tree(resource):
        return run('dump', 'xmltree', str(apk), '--file', resource)
    resources = run('dump', 'resources', str(apk))
    def xml_tree(name):
        # R8 resource shrinking shortens filenames. Resolve the shipped resource table.
        match = re.search(r'xml/' + re.escape(name) + r'\s*\n\s*\(\) \(file\) (\S+)', resources)
        assert match, 'Missing packaged XML resource: ' + name
        return tree(match.group(1))
    badging = run('dump', 'badging', str(apk))
    manifest = tree('AndroidManifest.xml')
    checks = {}
    checks['minimum_android_12'] = "minSdkVersion:'31'" in badging or "sdkVersion:'31'" in badging
    checks['target_api_37'] = "targetSdkVersion:'37'" in badging
    permissions = re.findall(r"uses-permission: name='([^']+)'", badging)
    checks['permission_allowlist'] = set(permissions) == {
        'android.permission.INTERNET', 'android.permission.HIDE_OVERLAY_WINDOWS', 'android.permission.QUERY_ADVANCED_PROTECTION_MODE',
        # Added by ML Kit's transport library and AndroidX core; no access to user data.
        'android.permission.ACCESS_NETWORK_STATE', 'com.betterchoice.india.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'}
    checks['not_debuggable'] = 'application-debuggable' not in badging
    checks['service_binding_protected'] = 'android.permission.BIND_ACCESSIBILITY_SERVICE' in manifest
    service_xml = xml_tree('bubble_service')
    checks['non_accessibility_tool'] = bool(re.search(r'isAccessibilityTool[^\n]*=(?:false|0x0)\s*$', service_xml, re.M))
    checks['no_gesture_or_key_filter_capability'] = not any(x in service_xml for x in ('canPerformGestures', 'canRequestFilterKeyEvents'))
    # Android must deliver events only from the supported shopping apps, and exactly the apps the
    # code itself accepts. Without packageNames the service would hear from every app on the phone.
    policy = (ROOT / 'app/src/main/java/com/betterchoice/android/ForegroundPolicy.java').read_text(encoding='utf-8')
    supported = set(re.findall(r'"([a-z0-9_.]+)"', policy[policy.index('SUPPORTED'):policy.index('shouldShow')]))
    declared = re.search(r'packageNames[^\n]*="([^"]+)"', service_xml)
    checks['accessibility_limited_to_supported_apps'] = bool(declared) and bool(supported) and \
        set(x.strip() for x in declared.group(1).split(',')) == supported
    # MVP reads pack photos: screenshots are intended, and must be disclosed in the consent text.
    strings = run('dump', 'strings', str(apk))
    checks['screenshot_capability_disclosed'] = 'canTakeScreenshot' in service_xml and 'screenshot' in strings.lower()
    checks['cleartext_denied'] = bool(re.search(r'cleartextTrafficPermitted[^\n]*=(?:false|0x0)\s*$', xml_tree('network_security_config'), re.M))
    # Inspect manifest structure before binary encoding, then confirm critical attributes above in APK.
    merged_files = list((ROOT / 'app/build/intermediates/merged_manifests/release').rglob('AndroidManifest.xml'))
    assert len(merged_files) == 1, 'Expected one merged release manifest'
    merged = ET.parse(merged_files[0]).getroot()
    app = merged.find('application')
    exported = [x for x in app if x.get(A + 'exported') == 'true']
    checks['only_launcher_link_target_and_protected_service_exported'] = {x.get(A + 'name') for x in exported} == {
        'com.betterchoice.android.OnboardingActivity', 'com.betterchoice.android.AuthLinkActivity', 'com.betterchoice.android.BubbleService'}
    library = {
        'com.google.mlkit.common.internal.MlKitComponentDiscoveryService', 'com.google.mlkit.common.internal.MlKitInitProvider',
        'com.google.android.gms.common.api.GoogleApiActivity', 'androidx.startup.InitializationProvider',
        'com.google.android.datatransport.runtime.backends.TransportBackendDiscovery',
        'com.google.android.datatransport.runtime.scheduling.jobscheduling.JobInfoSchedulerService',
        'com.google.android.datatransport.runtime.scheduling.jobscheduling.AlarmManagerSchedulerBroadcastReceiver'}
    ours = {'com.betterchoice.android.MainActivity', 'com.betterchoice.android.OnboardingActivity', 'com.betterchoice.android.AuthLinkActivity',
            'com.betterchoice.android.ContributeActivity', 'com.betterchoice.android.BubbleService'}
    components = [x for x in app if x.tag in ('activity', 'service', 'provider', 'receiver')]
    checks['only_allowlisted_components'] = all(x.get(A + 'name') in ours | library for x in components)
    checks['library_components_not_exported'] = all(x.get(A + 'exported') != 'true' for x in components if x.get(A + 'name') in library)
    checks['backup_disabled'] = app.get(A + 'allowBackup') == 'false' and app.get(A + 'fullBackupContent') == 'false'
    backups = xml_tree('data_extraction_rules')
    checks['all_storage_domains_excluded_from_both_backup_modes'] = all(
        backups.count('domain="' + domain + '"') == 2 for domain in (
            'root', 'sharedpref', 'file', 'database', 'external',
            'device_root', 'device_sharedpref', 'device_file', 'device_database'))
    with zipfile.ZipFile(apk) as archive:
        dex = b''.join(archive.read(n) for n in archive.namelist() if n.endswith('.dex'))
        # ML Kit ships its own logging; BetterChoice's diagnostics are checked by message below.
        checks['no_test_libraries_in_release_dex'] = all(x not in dex for x in (b'org/robolectric', b'org/junit', b'androidx/test'))
        checks['no_test_sign_in_in_release_dex'] = all(x not in dex for x in (
            b'test_mode_backend', b'test-access-', b'Skip sign-in', b'tester@betterchoice.test', b'bypass_active'))
        checks['no_diagnostic_messages_in_release_dex'] = all(x not in dex for x in (b'event_to_draw_ms', b'bubble_hidden', b'Window resolution unavailable'))
        # Native code for every phone the app supports: 64-bit ARM, 32-bit ARM (budget and Android Go phones run only
        # 32-bit apps) and x86_64 (emulators). Each ABI carries the same libraries, each built for that ABI: ELF class
        # (1 = 32-bit, 2 = 64-bit) and machine (40 ARM, 183 AArch64, 62 x86-64) read from the library's own header.
        expected = {'arm64-v8a': (2, 183), 'armeabi-v7a': (1, 40), 'x86_64': (2, 62)}
        libs = {}
        for name in archive.namelist():
            match = re.match(r'lib/([^/]+)/([^/]+\.so)$', name)
            if match:
                libs.setdefault(match.group(1), {})[match.group(2)] = archive.read(name)[:20]
        same_libraries = len({frozenset(files) for files in libs.values()}) == 1
        checks['native_code_for_64_and_32_bit_phones'] = set(libs) == set(expected) and same_libraries and all(
            header[:4] == b'\x7fELF' and header[4] == expected[abi][0]
            and int.from_bytes(header[18:20], 'little') == expected[abi][1]
            for abi, files in libs.items() for header in files.values())
        # Google Play requires 16 KB page support: every 64-bit library's loadable segments aligned to 16 KB, and every
        # library stored uncompressed starting on a 16 KB boundary of the APK, so the phone can map it in place.
        apk_bytes = apk.read_bytes()
        sixteen_kb = []
        for info in archive.infolist():
            if not re.match(r'lib/[^/]+/[^/]+\.so$', info.filename):
                continue
            name_length, extra_length = struct.unpack_from('<HH', apk_bytes, info.header_offset + 26)
            start = info.header_offset + 30 + name_length + extra_length
            data = archive.read(info.filename)
            aligned = info.compress_type == zipfile.ZIP_STORED and start % 16384 == 0
            if data[4] == 2:   # 64-bit ELF: program headers' p_align for each PT_LOAD segment
                table, = struct.unpack_from('<Q', data, 32)
                entry, count = struct.unpack_from('<HH', data, 54)
                for i in range(count):
                    kind, = struct.unpack_from('<I', data, table + i * entry)
                    if kind == 1:
                        aligned = aligned and struct.unpack_from('<Q', data, table + i * entry + 48)[0] >= 16384
            sixteen_kb.append(aligned)
        checks['native_code_ready_for_16_kb_pages'] = bool(sixteen_kb) and all(sixteen_kb)
    results = [ET.parse(p).getroot() for p in (ROOT / 'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml')]
    totals = {key: sum(int(x.get(key, '0')) for x in results) for key in ('tests', 'failures', 'errors', 'skipped')}
    checks['unit_tests_pass'] = totals['tests'] >= 60 and totals['failures'] == totals['errors'] == totals['skipped'] == 0
    properties = (ROOT / 'gradle/wrapper/gradle-wrapper.properties').read_text()
    checks['gradle_distribution_checksum_pinned'] = bool(re.search(r'^distributionSha256Sum=[0-9a-f]{64}$', properties, re.M))
    checks['dependency_verification_and_locks_exist'] = all((ROOT / path).exists() for path in
        ('gradle/verification-metadata.xml', 'app/gradle.lockfile', 'buildscript-gradle.lockfile'))
    report = dict(apk=str(apk.relative_to(ROOT)), sha256=hashlib.sha256(apk.read_bytes()).hexdigest(),
                  permissions=permissions, unit_tests=totals, checks=checks,
                  limitations=['Static/host checks do not prove device lifecycle, obscured-window enforcement or 500ms latency.',
                               'Signature verification with the owner certificate is required before distribution.'])
    output = ROOT / 'security/evidence/release-inspection.json'
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(report, indent=2))
    assert all(checks.values()), 'Release checks failed: ' + ', '.join(k for k, v in checks.items() if not v)


if __name__ == '__main__':
    main()
