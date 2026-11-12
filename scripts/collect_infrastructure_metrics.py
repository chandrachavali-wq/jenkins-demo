
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
        "collection_status": "INCOMPLETE",
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

        node_items = nodes["items"]

        if len(node_items) != 1:
            raise ValueError(
                f"Expected one Kubernetes node, found {len(node_items)}"
            )

        node = node_items[0]
        node_name = node["metadata"]["name"]

        result["node_name"] = node_name

        result["kubernetes_node_ready"] = any(
            condition["type"] == "Ready"
            and condition["status"] == "True"
            for condition in node["status"]["conditions"]
        )

        top = run_kubectl("top", "nodes", "--no-headers")

        for line in top.splitlines():
            parts = line.split()

            if len(parts) >= 5 and parts[0] == node_name:
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
    except (
        subprocess.CalledProcessError,
        subprocess.TimeoutExpired,
        ValueError,
        KeyError,
    ) as error:
        result["collection_error"] = str(error)

    required_fields = [
        "node_cpu_millicores",
        "node_cpu_percent",
        "node_memory_mib",
        "node_memory_percent",
    ]

    if (
        result["kubernetes_api_reachable"]
        and all(result[field] is not None for field in required_fields)
        and "collection_error" not in result
    ):
        result["collection_status"] = "SUCCESS"

    return result

if __name__ == "__main__":
    print(json.dumps(collect_metrics(), indent=2))
