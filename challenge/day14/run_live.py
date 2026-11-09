import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
DAY13 = ROOT.parent / "day13"
sys.path.insert(0, str(DAY13))
sys.path.insert(0, str(ROOT))

import gateway
import loop
import write_report


def main():
    audit = []
    box = gateway.Gateway(gateway.ollama_upstream, limit=80, audit=audit)
    server = gateway.serve(box, port=0)
    port = server.server_address[1]
    print(f"gateway http://127.0.0.1:{port}/v1/chat/completions", flush=True)
    try:
        client = loop.GatewayClient(f"http://127.0.0.1:{port}/v1/chat/completions")
        results = []
        for task in loop.TASKS:
            print("task", task["id"], flush=True)
            result = loop.Loop(client, ROOT / "workspace", ROOT / "sandbox").run_task(task)
            results.append(result)
            print(
                task["id"],
                "commit" if result["committed"] else "no-commit",
                result["commit"][:8] if result["commit"] else "",
                "security",
                result["caught_security"],
                "missed",
                result["missed_both"],
                flush=True,
            )
    finally:
        server.shutdown()
        server.server_close()
    path = write_report.write(results, audit)
    print("report", path, flush=True)


if __name__ == "__main__":
    main()
