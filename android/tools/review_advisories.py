"""Fetch complete OSV records for review; public advisory IDs only."""
import concurrent.futures
import json
import sys
sys.stdout.reconfigure(encoding='utf-8')
from audit_dependencies import OUTPUT, fetch

report = json.loads((OUTPUT / 'osv-dependencies.json').read_text())
ids = sorted({v['id'] for q in report['queries'] for v in q['vulnerabilities']})
with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
    records = list(pool.map(lambda key: json.loads(fetch('https://api.osv.dev/v1/vulns/' + key)), ids))
(OUTPUT / 'advisory-details.json').write_text(json.dumps(records, indent=2) + '\n', encoding='utf-8')
for record in records:
    fixes = sorted({event['fixed'] for affected in record.get('affected', [])
                    for value in affected.get('ranges', []) for event in value.get('events', []) if 'fixed' in event})
    print(record['id'], record.get('summary'), 'fixes=' + ','.join(fixes))
