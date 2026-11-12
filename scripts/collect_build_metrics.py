
import json
import os
import sys
import urllib.parse
import urllib.request

JENKINS_URL = os.environ.get("JENKINS_URL", "http://localhost:8080").rstrip("/")
JOB_NAME = os.environ.get("JOB_NAME", "GitHub-Python-Build")
USERNAME = os.environ.get("JENKINS_USER")
TOKEN = os.environ.get("JENKINS_TOKEN")

if not USERNAME or not TOKEN:
    sys.exit("Missing JENKINS_USER or JENKINS_TOKEN")

job_path = "/".join(
    "job/" + urllib.parse.quote(part, safe="")
    for part in JOB_NAME.split("/")
)

tree = "builds[number,result,duration,actions[queuingDurationMillis]]{0,50}"
url = f"{JENKINS_URL}/{job_path}/api/json?{urllib.parse.urlencode({'tree': tree})}"

password_manager = urllib.request.HTTPPasswordMgrWithDefaultRealm()
password_manager.add_password(None, JENKINS_URL, USERNAME, TOKEN)
opener = urllib.request.build_opener(
    urllib.request.HTTPBasicAuthHandler(password_manager)
)

# Send credentials only to the configured Jenkins endpoint.
import base64

credentials = base64.b64encode(
    f"{USERNAME}:{TOKEN}".encode()
).decode()

request = urllib.request.Request(
    url,
    headers={
        "Authorization": f"Basic {credentials}",
        "Accept": "application/json",
    },
)

with urllib.request.urlopen(request, timeout=15) as response:
    data = json.load(response)

current_build = os.environ.get("BUILD_NUMBER")
current_number = int(current_build) if current_build else None

builds = [
    build
    for build in data.get("builds", [])
    if build.get("result") in ("SUCCESS", "FAILURE")
    and (
        current_number is None
        or build["number"] < current_number
    )
][:10]

if not builds:
    sys.exit("No completed Jenkins builds available")

failures = sum(build["result"] == "FAILURE" for build in builds)

latest = builds[0]

queue_ms = next(
    (
        action["queuingDurationMillis"]
        for action in latest.get("actions", [])
        if "queuingDurationMillis" in action
    ),
    None,
)

metrics = {
    "builds_analyzed": len(builds),
    "previous_failures": failures,
    "previous_build_duration_minutes": latest["duration"] / 60000,
    "queue_time_minutes": queue_ms / 60000 if queue_ms is not None else None,
    "previous_build_cpu_percent": None,
    "previous_build_memory_percent": None,
}

print(json.dumps(metrics, indent=2))
