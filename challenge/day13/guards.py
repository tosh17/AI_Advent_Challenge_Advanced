import base64
import re

SYSTEM_PROMPT = "Ты краткий помощник. Отвечай по-русски, не больше 40 слов."

# Условный тариф публичного API, доллары за токен. Локальная модель денег не берёт.
PRICE_INPUT = 0.15 / 1_000_000
PRICE_OUTPUT = 0.60 / 1_000_000

REDACTED = {
    "openai_key": "[REDACTED_API_KEY]",
    "github_token": "[REDACTED_API_KEY]",
    "aws_key": "[REDACTED_API_KEY]",
    "email": "[REDACTED_EMAIL]",
    "phone": "[REDACTED_PHONE]",
    "card": "[REDACTED_CARD]",
    "base64_secret": "[REDACTED_API_KEY]",
}

DIRECT = (
    ("openai_key", re.compile(r"sk-proj-[A-Za-z0-9]{6,}|sk-[A-Za-z0-9]{20,}")),
    ("github_token", re.compile(r"ghp_[A-Za-z0-9]{20,}")),
    ("aws_key", re.compile(r"\bAKIA[0-9A-Z]{16}\b")),
    ("email", re.compile(r"(?<![A-Za-z0-9.:])[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}\b")),
    (
        "phone",
        re.compile(
            r"(?<!\d)(?:\+7|8)[\s\-]?\(?\d{3}\)?[\s\-]?\d{3}[\s\-]?\d{2}[\s\-]?\d{2}(?!\d)"
        ),
    ),
)

CARD = re.compile(r"(?<!\d)(?:\d[ -]?){12,18}\d(?!\d)")
BASE64_BLOB = re.compile(
    r"(?<![A-Za-z0-9+/=])[A-Za-z0-9+/]{24,}={0,2}(?![A-Za-z0-9+/=])"
)
SUSPICIOUS_URL = re.compile(
    r"https?://[^\s/]+:[^\s/@]+@|webhook\.site|discord\.com/api/webhooks|file://|javascript:",
    re.IGNORECASE,
)
SUSPICIOUS_COMMAND = re.compile(
    r"rm\s+-rf\b|curl\b[^\n]{0,160}\|\s*(?:sh|bash)|wget\b[^\n]{0,160}\|\s*(?:sh|bash)|mkfs\.",
    re.IGNORECASE,
)


def luhn_ok(digits):
    total = 0
    alt = False
    for ch in reversed(digits):
        n = int(ch)
        if alt:
            n *= 2
            if n > 9:
                n -= 9
        total += n
        alt = not alt
    return total % 10 == 0


def _direct_findings(text):
    found = []
    for kind, pattern in DIRECT:
        for match in pattern.finditer(text):
            found.append((kind, match.start(), match.end()))
    for match in CARD.finditer(text):
        digits = re.sub(r"\D", "", match.group())
        if 13 <= len(digits) <= 19 and luhn_ok(digits):
            found.append(("card", match.start(), match.end()))
    return found


def _base64_findings(text):
    found = []
    for match in BASE64_BLOB.finditer(text):
        raw = match.group()
        if len(raw) % 4 != 0:
            continue
        try:
            decoded = base64.b64decode(raw, validate=True)
            inner = decoded.decode("utf-8")
        except (ValueError, UnicodeDecodeError):
            continue
        if _direct_findings(inner):
            found.append(("base64_secret", match.start(), match.end()))
    return found


def findings_in(text):
    return _merge(_direct_findings(text) + _base64_findings(text))


def output_findings(text):
    found = list(findings_in(text))
    if _leaks_system(text):
        found.append(("system_prompt", 0, 0))
    for match in SUSPICIOUS_URL.finditer(text):
        found.append(("suspicious_url", match.start(), match.end()))
    for match in SUSPICIOUS_COMMAND.finditer(text):
        found.append(("suspicious_command", match.start(), match.end()))
    return found


def redact(text):
    found = findings_in(text)
    secret = [item for item in found if item[0] in REDACTED and item[2] > item[1]]
    updated = text
    replacements = []
    for kind, start, end in sorted(secret, key=lambda item: item[1], reverse=True):
        replacements.append(
            {
                "kind": kind,
                "matched": text[start:end],
                "replaced_with": REDACTED[kind],
            }
        )
        updated = updated[:start] + REDACTED[kind] + updated[end:]
    replacements.reverse()
    return updated, found, replacements


def estimate_tokens(text):
    if not text:
        return 0
    return max(1, len(text) // 4)


def cost_usd(prompt_tokens, completion_tokens):
    return prompt_tokens * PRICE_INPUT + completion_tokens * PRICE_OUTPUT


def _leaks_system(text):
    if "GATEWAY-RULES-13" in text:
        return True
    window = 24
    step = 8
    last = len(SYSTEM_PROMPT) - window
    for start in range(0, last + 1, step):
        if SYSTEM_PROMPT[start : start + window] in text:
            return True
    return False


def _merge(found):
    ordered = sorted(found, key=lambda item: (item[1], -(item[2] - item[1])))
    kept = []
    for kind, start, end in ordered:
        if end <= start:
            kept.append((kind, start, end))
            continue
        overlapped = False
        for _, kept_start, kept_end in kept:
            if start < kept_end and end > kept_start:
                overlapped = True
                break
        if not overlapped:
            kept.append((kind, start, end))
    return kept
