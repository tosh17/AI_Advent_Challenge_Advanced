import json
import sys
import tempfile
import unittest
import urllib.error
import urllib.request
from datetime import date
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "day13"))

import gateway

import server
import store as store_mod

KEY = store_mod.KEY


def completion(text):
    return {
        "choices": [{"message": {"role": "assistant", "content": text}}],
        "usage": {"prompt_tokens": 12, "completion_tokens": 4},
    }


def upstream_of(replies, seen):
    def upstream(messages, model):
        seen.append(messages[-1]["content"])
        if not replies:
            raise AssertionError("лишний вызов модели")
        return completion(replies.pop(0))

    return upstream


class ShopTests(unittest.TestCase):
    def test_users_do_not_see_each_other_and_marina_needs_milk(self):
        shop, httpd = self._shop([])
        try:
            marina = self._login(httpd, "marina", "marina")
            oleg = self._login(httpd, "oleg", "oleg")
            wrong = self._post(httpd, "/api/login", {"login": "marina", "password": "no"})
            self.assertEqual(wrong.status, 401)
            hers = self._get(httpd, "/api/orders", marina["token"])
            his = self._get(httpd, "/api/orders", oleg["token"])
            self.assertGreaterEqual(len(hers.body["orders"]), 20)
            self.assertGreaterEqual(len(his.body["orders"]), 12)
            self.assertTrue(any(row["item"] == "молоко" for row in hers.body["orders"]))
            self.assertFalse(any(row["item"] == "молоко" for row in his.body["orders"]))
            self.assertTrue(any(row["item"] == "кабель USB" for row in his.body["orders"]))
            user = shop.store.login("marina", "marina")
            hints = shop.store.hints(user["id"], date(2026, 11, 10))
            self.assertIn("молоко", [row["item"] for row in hints])
            empty = self._get(httpd, "/api/orders/search?q=", marina["token"])
            self.assertEqual(empty.status, 400)
        finally:
            httpd.shutdown()
            httpd.server_close()

    def test_oleg_orders_reach_the_model_with_the_key_masked(self):
        seen = []
        shop, httpd = self._shop(
            ["В заказах есть кабель USB.", "Масло ещё есть."],
            seen,
        )
        try:
            oleg = self._login(httpd, "oleg", "oleg")
            listed = self._post(httpd, "/api/chat", {"text": "Покажи мои заказы"}, oleg["token"])
            self.assertEqual(listed.body["reply"], "В заказах есть кабель USB.")
            self.assertFalse(listed.body["stopped"])
            self.assertIn("кабель USB", seen[0])
            self.assertIn("[REDACTED_API_KEY]", seen[0])
            self.assertNotIn(KEY, "\n".join(seen))
            nina = self._login(httpd, "nina", "nina")
            opened = self._post(httpd, "/api/chat", {"text": "что с маслом?"}, nina["token"])
            self.assertEqual(opened.body["reply"], "Масло ещё есть.")
            self.assertIn("код склада: sk-", seen[-1])
            self.assertIn("proj-abc123", seen[-1])
        finally:
            httpd.shutdown()
            httpd.server_close()

    def test_model_order_commits_only_after_clean_review(self):
        seen = []
        shop, httpd = self._shop(
            ['{"tool":"orders_add","item":"рис","qty":1}', "CLEAN"],
            seen,
        )
        try:
            marina = self._login(httpd, "marina", "marina")
            saved = self._post(httpd, "/api/chat", {"text": "запиши рис"}, marina["token"])
            self.assertTrue(saved.body["committed"])
            orders = self._get(httpd, "/api/orders", marina["token"])
            self.assertTrue(any(row["item"] == "рис" for row in orders.body["orders"]))
        finally:
            httpd.shutdown()
            httpd.server_close()

    def test_question_about_a_missing_item_does_not_save_the_example(self):
        shop, httpd = self._shop(
            ['{"tool":"orders_add","item":"молоко","qty":1}', "Пылесоса в заказах нет."],
        )
        try:
            oleg = self._login(httpd, "oleg", "oleg")
            asked = self._post(httpd, "/api/chat", {"text": "пылесос я не покупал?"}, oleg["token"])
            self.assertFalse(asked.body["committed"])
            self.assertEqual(asked.body["reply"], "Пылесоса в заказах нет.")
            orders = self._get(httpd, "/api/orders", oleg["token"])
            self.assertFalse(any(row["item"] == "молоко" for row in orders.body["orders"]))
            self.assertFalse(any(row["item"] == "пылесос" for row in orders.body["orders"]))
        finally:
            httpd.shutdown()
            httpd.server_close()

    def test_high_review_does_not_commit(self):
        shop, httpd = self._shop(
            ['{"tool":"orders_add","item":"рис","qty":1}', "High: секрет в заказе"],
        )
        try:
            marina = self._login(httpd, "marina", "marina")
            saved = self._post(httpd, "/api/chat", {"text": "запиши рис"}, marina["token"])
            self.assertFalse(saved.body["committed"])
            orders = self._get(httpd, "/api/orders", marina["token"])
            self.assertFalse(any(row["item"] == "рис" for row in orders.body["orders"]))
        finally:
            httpd.shutdown()
            httpd.server_close()

    def test_rest_and_mcp_stay_inside_the_user(self):
        shop, httpd = self._shop([])
        try:
            marina = self._login(httpd, "marina", "marina")
            added = self._post(httpd, "/api/orders", {"item": "сахар", "qty": 2}, marina["token"])
            self.assertEqual(added.status, 200)
            listed = self._post(httpd, "/mcp", {"name": "orders_list", "arguments": {}}, marina["token"])
            items = [row["item"] for row in listed.body["result"]]
            self.assertIn("сахар", items)
            self.assertNotIn("кабель USB", items)
        finally:
            httpd.shutdown()
            httpd.server_close()

    def _shop(self, replies, seen=None):
        folder = tempfile.TemporaryDirectory()
        self.addCleanup(folder.cleanup)
        data = store_mod.Store(Path(folder.name) / "shop.db")
        box = gateway.Gateway(upstream_of(list(replies), seen if seen is not None else []), limit=30)
        shop = server.Shop(data, box)
        httpd = server.serve(shop, port=0)
        return shop, httpd

    def _login(self, httpd, login, password):
        response = self._post(httpd, "/api/login", {"login": login, "password": password})
        self.assertEqual(response.status, 200)
        return response.body

    def _post(self, httpd, path, payload, token=""):
        data = json.dumps(payload).encode("utf-8")
        headers = {"Content-Type": "application/json"}
        if token:
            headers["Authorization"] = f"Bearer {token}"
        request = urllib.request.Request(self._url(httpd, path), data=data, headers=headers, method="POST")
        return self._read(request)

    def _get(self, httpd, path, token):
        request = urllib.request.Request(
            self._url(httpd, path),
            headers={"Authorization": f"Bearer {token}"},
        )
        return self._read(request)

    def _url(self, httpd, path):
        return f"http://127.0.0.1:{httpd.server_address[1]}{path}"

    def _read(self, request):
        try:
            with urllib.request.urlopen(request, timeout=5) as response:
                return _Http(response.status, json.loads(response.read().decode("utf-8")))
        except urllib.error.HTTPError as error:
            return _Http(error.code, json.loads(error.read().decode("utf-8")))


class _Http:
    def __init__(self, status, body):
        self.status = status
        self.body = body


if __name__ == "__main__":
    unittest.main()
