import sys
import unittest
from pathlib import Path

DAY13 = Path(__file__).resolve().parents[1] / "day13"
sys.path.insert(0, str(DAY13))

import gateway
import loop

ROOT = Path(__file__).resolve().parent
WORKSPACE = ROOT / "workspace"

TOKEN_BAD = '''```python
TOKEN = "hardcoded-auth-token"
def save_token(token):
    global TOKEN
    TOKEN = token
def load_token():
    return TOKEN
```'''

TOKEN_OK = '''```python
_token = ""
def save_token(token):
    global _token
    _token = token
def load_token():
    return _token
```'''

API_HTTP = '''```python
def request_url(path):
    return "http://10.10.1.101:11434" + path
```'''

NOT_CODE = "Сначала объясню, потом ничего не напишу."


def completion(text):
    return {
        "choices": [{"message": {"role": "assistant", "content": text}}],
        "usage": {"prompt_tokens": 20, "completion_tokens": 12},
    }


def upstream_of(replies, seen):
    def upstream(messages, model):
        content = messages[-1]["content"]
        seen.append(content)
        if not replies:
            raise AssertionError("лишний вызов модели")
        return completion(replies.pop(0))

    return upstream


class LoopTests(unittest.TestCase):
    def test_bare_high_is_a_finding(self):
        findings, clean = loop.parse_review("High")
        self.assertEqual(findings, [{"severity": "high", "message": ""}])
        self.assertFalse(clean)
        self.assertEqual(loop.decide(findings, clean)[0], "fix")

    def test_bare_high_stops_code_even_without_a_detail(self):
        code = "def request_url(path):\n    return 'http://10.10.1.101:11434' + path\n"
        attempts = [
            {"stage": "generate", "code": code},
            {"stage": "security", "status": 200, "text": "High", "findings": [{"severity": "high", "message": ""}]},
        ]
        caught, missed = loop._score("api", attempts)
        self.assertEqual(missed, [])
        self.assertEqual(caught, ["High, коммит остановлен"])

    def test_high_returns_to_generation_then_commits(self):
        seen = []
        result = self._run(
            seen,
            ["да", TOKEN_BAD, "High: константа токена в коде", TOKEN_OK, "CLEAN"],
            loop.TASKS[0],
        )
        self.assertTrue(result["committed"])
        self.assertEqual(len(result["commit"]), 40)
        self.assertEqual(result["warning"], "")
        self.assertIn("константа токена в коде", result["caught_security"])
        self.assertTrue(any(row["stage"] == "context-secret" and row["status"] == 400 for row in result["attempts"]))
        self.assertTrue(result["caught_gateway"])
        self.assertFalse(any(row.get("prompt_has_secret") for row in result["attempts"]))
        blob = "\n".join(seen)
        self.assertNotIn("sk-proj-", blob)
        self.assertNotIn("anton@example.com", blob)
        saved = (ROOT / "sandbox" / "token" / "repo" / "token_box.py").read_text(encoding="utf-8")
        self.assertNotIn("hardcoded-auth-token", saved)

    def test_medium_commits_with_warning_and_records_a_miss(self):
        seen = []
        result = self._run(
            seen,
            ["да", API_HTTP, "Medium: адрес на http"],
            loop.TASKS[2],
        )
        self.assertTrue(result["committed"])
        self.assertIn("адрес на http", result["warning"])
        self.assertIn("адрес на http", result["caught_security"])
        self.assertIn("нет проверки входа", result["missed_both"])
        self.assertNotIn("sk-proj-", "\n".join(seen))

    def test_failed_test_is_retried_before_security(self):
        seen = []
        replies = ["да", NOT_CODE, TOKEN_OK, "CLEAN"]
        result = self._run(seen, replies, loop.TASKS[0])
        self.assertTrue(result["committed"])
        tests = [row for row in result["attempts"] if row["stage"] == "test"]
        self.assertFalse(tests[0]["ok"])
        self.assertTrue(tests[1]["ok"])
        security_after_fail = result["attempts"].index(tests[1]) < max(
            index for index, row in enumerate(result["attempts"]) if row["stage"] == "security"
        )
        self.assertTrue(security_after_fail)

    def _run(self, seen, replies, task):
        box = gateway.Gateway(upstream_of(replies, seen), limit=30)
        server = gateway.serve(box, port=0)
        port = server.server_address[1]
        try:
            client = loop.GatewayClient(f"http://127.0.0.1:{port}/v1/chat/completions")
            return loop.Loop(client, WORKSPACE, ROOT / "sandbox").run_task(task)
        finally:
            server.shutdown()
            server.server_close()


if __name__ == "__main__":
    unittest.main()
