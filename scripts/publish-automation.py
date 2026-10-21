"""Publish an immutable automation release using JFROG_TOKEN from the environment."""
import hashlib
import os
import re
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

version = ET.parse('pom.xml').findtext('{http://maven.apache.org/POM/4.0.0}version')
if not re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+', version or ''):
    raise SystemExit('Expected a release version in pom.xml')
name = f'sre-automation-{version}.jar'
jar = Path('target') / name
payload = jar.read_bytes()
checksum = hashlib.sha256(payload).hexdigest().encode()
jar.with_suffix('.jar.sha256').write_bytes(checksum)
token = os.environ.get('JFROG_TOKEN')
if not token:
    raise SystemExit('JFROG_TOKEN is required; use Jenkins credential jfrog-token')
base = os.environ.get('ARTIFACTORY_URL', 'http://localhost:8086').rstrip('/')
base += f'/artifactory/jenkins-artifacts-local/sre-automation/{version}/'
class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None
opener = urllib.request.build_opener(NoRedirect)
def request(filename, method='GET', data=None):
    req = urllib.request.Request(base + filename, data=data, method=method,
        headers={'Authorization': 'Bearer ' + token, 'Content-Type': 'application/octet-stream', 'User-Agent': 'curl/8.0'})
    try:
        return opener.open(req, timeout=60)
    except urllib.error.HTTPError as error:
        raise RuntimeError(f'Artifactory {method} failed: HTTP {error.code}') from None
# Existing matching bytes allow safe retry; different bytes must never be replaced.
for filename, data in [(name, payload), (name + '.sha256', checksum)]:
    try:
        with request(filename) as response:
            existing = response.read()
    except RuntimeError as error:
        if str(error).endswith('HTTP 404'):
            existing = None
        else:
            raise
    if existing is not None and existing != data:
        raise SystemExit('Release path already contains different bytes; increment pom.xml version')
# Repeat preflight for both files before uploading either.
for filename, data in [(name, payload), (name + '.sha256', checksum)]:
    try:
        with request(filename) as response:
            if response.read() != data:
                raise SystemExit('Release changed during publication; refusing overwrite')
            continue
    except RuntimeError as error:
        if not str(error).endswith('HTTP 404'):
            raise
    with request(filename, 'PUT', data) as response:
        response.read()
    with request(filename) as response:
        download = response.read()
        if download != data:
            raise SystemExit(f'Published artifact verification failed: {filename}; expected {len(data)} bytes, got {len(download)}; content type {response.headers.get("Content-Type")}')
print(f'Published and verified sre-automation/{version}/{name} and SHA-256')
