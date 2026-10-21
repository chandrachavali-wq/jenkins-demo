"""Verify downloaded bytes before Jenkins executes them."""
import hashlib
import re
import sys
from pathlib import Path

artifact, checksum = map(Path, sys.argv[1:])
expected = checksum.read_text().split()
if not expected or not re.fullmatch(r"[a-fA-F0-9]{64}", expected[0]):
    raise SystemExit("Invalid SHA-256 file")
with artifact.open("rb") as stream:
    digest = hashlib.sha256()
    for chunk in iter(lambda: stream.read(1024 * 1024), b""):
        digest.update(chunk)
    actual = digest.hexdigest()
if actual != expected[0].lower():
    raise SystemExit("SHA-256 verification failed")
print("SHA-256 verified")
