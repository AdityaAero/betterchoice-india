"""Check Gradle's candidate checksums against publisher repositories and query OSV.

Network payloads contain public Maven coordinates only. Never sends project source.
Run after resolving *all* variants with --write-verification-metadata sha256.
This reviews bootstrap metadata; normal builds must use strict verification.
"""
import concurrent.futures
import datetime
import hashlib
import json
from pathlib import Path
import re
import urllib.error
import urllib.request
import urllib.parse
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / '.tools/gradle-home/caches/modules-2/files-2.1'
OUTPUT = ROOT / 'security/evidence'
NS = {'g': 'https://schema.gradle.org/dependency-verification'}


def fetch(url, payload=None):
    request = urllib.request.Request(url, data=payload, headers={
        'User-Agent': 'BetterChoice-dependency-audit/1',
        **({'Content-Type': 'application/json'} if payload is not None else {})})
    with urllib.request.urlopen(request, timeout=30) as response:
        return response.read()


def verify_artifact(item):
    group, name, version, filename, expected = item
    result = dict(group=group, name=name, version=version, artifact=filename, sha256=expected)
    candidates = list((CACHE / group / name / version).glob('*/' + filename))
    local = next((p for p in candidates if hashlib.sha256(p.read_bytes()).hexdigest() == expected), None)
    if local is None:
        return dict(result, status='FAIL', reason='No cached artifact matching reviewed SHA256')
    path = '/'.join([group.replace('.', '/'), name, version, filename])
    # Gradle module metadata can select a file from another published variant directory.
    # Read the cached, checksum-verified .module rather than guessing the download URL.
    for module in (CACHE / group / name / version).glob('*/*.module'):
        for variant in json.loads(module.read_text()).get('variants', []):
            for entry in variant.get('files', []):
                if entry.get('name') == filename:
                    path = urllib.parse.urljoin('/'.join([group.replace('.', '/'), name, version]) + '/', entry['url'])
    repositories = ['https://repo.maven.apache.org/maven2/', 'https://dl.google.com/dl/android/maven2/',
                    'https://plugins.gradle.org/m2/']
    if group.startswith(('androidx.', 'com.android', 'com.google.testing.platform')):
        repositories = [repositories[1], repositories[0], repositories[2]]
    for base in repositories:
        for algorithm in ('sha256', 'sha1'):
            url = base + path + '.' + algorithm
            try:
                published = fetch(url).decode().strip().split()[0].lower()
            except (urllib.error.URLError, TimeoutError, IndexError, UnicodeError):
                continue
            if not re.fullmatch('[0-9a-f]{' + str(64 if algorithm == 'sha256' else 40) + '}', published):
                continue
            actual = hashlib.new(algorithm, local.read_bytes()).hexdigest()
            return dict(result, status='PASS' if actual == published else 'FAIL',
                        publisher_checksum_url=url, publisher_algorithm=algorithm, publisher_checksum=published)
    return dict(result, status='UNVERIFIED', reason='Publisher checksum unavailable')


def main():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    document = ET.parse(ROOT / 'gradle/verification-metadata.xml')
    components = document.findall('g:components/g:component', NS)
    artifacts = []
    coordinates = []
    locked = set()
    for lock in [ROOT / 'app/gradle.lockfile', ROOT / 'buildscript-gradle.lockfile']:
        if lock.exists():
            for line in lock.read_text().splitlines():
                coordinate = line.split('=')[0].split(':')
                if len(coordinate) == 3 and not line.startswith('#'):
                    locked.add(tuple(coordinate))
    for component in components:
        group, name, version = (component.attrib[key] for key in ('group', 'name', 'version'))
        if (group, name, version) in locked:
            coordinates.append((group, name, version))
        for artifact in component.findall('g:artifact', NS):
            for digest in artifact.findall('g:sha256', NS):
                artifacts.append((group, name, version, artifact.attrib['name'], digest.attrib['value']))
    assert locked and coordinates, 'Resolve and lock buildscript/app configurations before auditing'
    with concurrent.futures.ThreadPoolExecutor(max_workers=12) as pool:
        checks = list(pool.map(verify_artifact, artifacts))
    stamp = datetime.datetime.now(datetime.timezone.utc).isoformat()
    report = dict(checked_at=stamp, checks=checks, summary={
        state: sum(x['status'] == state for x in checks) for state in ('PASS', 'FAIL', 'UNVERIFIED')})
    (OUTPUT / 'publisher-checksums.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print('Publisher checks:', report['summary'], flush=True)
    queries = [{'package': {'ecosystem': 'Maven', 'name': g + ':' + n}, 'version': v} for g, n, v in coordinates]
    try:
        results = []
        for start in range(0, len(queries), 100):
            batch = json.loads(fetch('https://api.osv.dev/v1/querybatch', json.dumps({'queries': queries[start:start + 100]}).encode()))
            results.extend(batch['results'])
        if len(results) != len(queries):
            raise ValueError('Incomplete OSV response')
        findings = [dict(query=q, vulnerabilities=r.get('vulns', [])) for q, r in zip(queries, results)]
        osv = dict(checked_at=stamp, status='COMPLETE', queries=findings)
        print('OSV packages with advisories:', sum(bool(x['vulnerabilities']) for x in findings), flush=True)
    except (urllib.error.URLError, TimeoutError, KeyError, ValueError) as error:
        osv = dict(checked_at=stamp, status='UNAVAILABLE', reason=str(error))
        print('OSV unavailable; vulnerability review incomplete', flush=True)
    (OUTPUT / 'osv-dependencies.json').write_text(json.dumps(osv, indent=2) + '\n', encoding='utf-8')
    if (report['summary']['FAIL'] or report['summary']['UNVERIFIED'] or osv['status'] != 'COMPLETE'
            or any(q['vulnerabilities'] for q in osv.get('queries', []))):
        raise SystemExit(1)


if __name__ == '__main__':
    main()
