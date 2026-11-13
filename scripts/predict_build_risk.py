import json
from urllib.request import Request, urlopen

with open("build_metrics.json", encoding="utf-8") as f:
    metrics = json.load(f)

payload = {
    "previous_failures": metrics["previous_failures"],
    "previous_build_duration_minutes": metrics[
        "previous_build_duration_minutes"
    ],
}

print("===== REAL JENKINS METRICS =====", flush=True)
print(json.dumps(payload, indent=2), flush=True)

request = Request(
    "http://localhost:8001/predict/v2",
    data=json.dumps(payload).encode("utf-8"),
    headers={"Content-Type": "application/json"},
    method="POST",
)

with urlopen(request, timeout=15) as response:
    result = json.load(response)

print("===== ML BUILD RISK ANALYSIS =====")
print("Prediction          :", result["prediction"])
print(
    "Failure Probability :",
    f'{float(result["failure_probability"]) * 100:.1f}%',
)
print("Model               : EXPERIMENTAL V2")
print("Input Source        : REAL JENKINS HISTORY")
print("Pipeline Mode       : ADVISORY")
print("==================================")
