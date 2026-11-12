
import json
import os
import subprocess
from datetime import datetime, timezone

KUBE_CONTEXT = os.getenv("KUBE_CONTEXT", "minikube")
JOB_NAME = os.getenv("JOB_NAME")
BUILD_NUMBER = os.getenv("BUILD_NUMBER")

def run_kubectl(*args):
    return subprocess.run(
        ["kubectl", "--context", KUBE_CONTEXT, *args],
        capture_output=True,
        text=True,
        timeout=20,
        check=True,
    ).stdout


def collect_metrics():
    result = {
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "jenkins_job": JOB_NAME,
        "build_number": int(BUILD_NUMBER) if BUILD_NUMBER else None,
        "cluster": KUBE_CONTEXT,
        "kubernetes_api_reachable": False,
        "kubernetes_node_ready": False,
        "node_cpu_millicores": None,
        "node_cpu_percent": None,
        "node_memory_mib": None,
        "node_memory_percent": None,
}

    try:
        nodes = json.loads(
            run_kubectl("get", "nodes", "-o", "json")
        )

        result["kubernetes_api_reachable"] = True

        for node in nodes["items"]:
            if node["metadata"]["name"] == "minikube":
                result["kubernetes_node_ready"] = any(
                    condition["type"] == "Ready"
                    and condition["status"] == "True"
                    for condition in node["status"]["conditions"]
                )

        top = run_kubectl("top", "nodes", "--no-headers")

        for line in top.splitlines():
            parts = line.split()

            if len(parts) >= 5 and parts[0] == "minikube":
                result["node_cpu_millicores"] = int(
                    parts[1].removesuffix("m")
                )
                result["node_cpu_percent"] = float(
                    parts[2].removesuffix("%")
                )
                result["node_memory_mib"] = float(
                    parts[3].removesuffix("Mi")
                )
                result["node_memory_percent"] = float(
                    parts[4].removesuffix("%")
                )

    except (subprocess.CalledProcessError,
            subprocess.TimeoutExpired,
            ValueError,
            KeyError) as error:
        result["collection_error"] = str(error)

    return result


if __name__ == "__main__":
    print(json.dumps(collect_metrics(), indent=2))
