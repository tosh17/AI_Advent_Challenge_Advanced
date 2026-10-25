const sessionKey = "work-session";
const themeKey = "work-theme";
const labels = {
  activities_log: "Журнал",
  activities_schedule: "План",
  activities_backlog: "Беклог",
  activities_add_to_sprint: "Спринт",
  activities_list_day: "Журнал за день",
  activities_list_range: "Журнал за период",
  activities_plan: "План на день",
  activities_list_sprint: "Текущий спринт",
  activities_find: "Поиск",
};

const log = document.querySelector("#log");
const hint = document.querySelector("#hint");
const form = document.querySelector("#composer");
const text = document.querySelector("#text");
const send = document.querySelector("#send");
const sprintBody = document.querySelector("#sprint-body");
const theme = document.querySelector("#theme");

applyTheme(localStorage.getItem(themeKey) || "classic");
theme?.addEventListener("change", () => applyTheme(theme.value));

loadSprint();

form.addEventListener("submit", (event) => {
  event.preventDefault();
  const message = text.value.trim();
  if (!message || send.disabled) return;
  text.value = "";
  post(message);
});

text.addEventListener("keydown", (event) => {
  if (event.key === "Enter" && !event.shiftKey) {
    event.preventDefault();
    form.requestSubmit();
  }
});

async function post(message) {
  hint?.remove();
  append(message, "user");
  const pending = append("Смотрю…", "assistant pending");
  setBusy(true);
  try {
    const body = await deliver(message);
    pending.classList.remove("pending");
    pending.replaceChildren();
    const reply = document.createElement("p");
    reply.textContent = body.reply || "Готово.";
    pending.append(reply);
    if (body.calls?.length) pending.append(calls(body.calls));
  } catch (error) {
    pending.classList.remove("pending");
    pending.classList.add("error");
    pending.textContent = error instanceof Error ? error.message : "Не получилось отправить сообщение";
  } finally {
    setBusy(false);
    text.focus();
  }
}

async function deliver(message) {
  let id = await openSession();
  let response = await sendMessage(id, message);
  if (response.status === 404) {
    sessionStorage.removeItem(sessionKey);
    id = await openSession();
    response = await sendMessage(id, message);
  }
  if (!response.ok) throw new Error(await errorText(response));
  return response.json();
}

async function openSession() {
  const saved = sessionStorage.getItem(sessionKey);
  if (saved) return saved;
  const response = await fetch("/api/sessions", { method: "POST" });
  if (!response.ok) throw new Error(await errorText(response));
  const body = await response.json();
  sessionStorage.setItem(sessionKey, body.id);
  return body.id;
}

function sendMessage(id, message) {
  return fetch("/api/sessions/" + encodeURIComponent(id) + "/messages", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ text: message }),
  });
}

async function errorText(response) {
  const raw = await response.text();
  try {
    const body = JSON.parse(raw);
    if (body.error) return body.error;
  } catch (ignore) {
    if (raw.trim()) return raw.trim();
  }
  return "Не получилось отправить сообщение";
}

function append(message, kind) {
  const item = document.createElement("li");
  item.className = "bubble " + kind;
  item.textContent = message;
  log.append(item);
  item.scrollIntoView({ block: "end" });
  return item;
}

function calls(items) {
  const list = document.createElement("ul");
  list.className = "calls";
  for (const call of items) {
    const item = document.createElement("li");
    const title = document.createElement("b");
    title.textContent = labels[call.tool] || call.tool;
    const result = document.createElement("span");
    result.textContent = call.result;
    item.append(title, result);
    list.append(item);
  }
  return list;
}

async function loadSprint() {
  if (!sprintBody) return;
  try {
    const response = await fetch("/api/sprint");
    if (!response.ok) throw new Error(await errorText(response));
    const data = await response.json();
    const items = data.items || [];
    if (!items.length) {
      sprintBody.textContent = "В текущем спринте пусто";
      return;
    }
    sprintBody.replaceChildren();
    const list = document.createElement("ul");
    for (const item of items) {
      const row = document.createElement("li");
      row.textContent = item.summary;
      list.append(row);
    }
    sprintBody.append(list);
  } catch (error) {
    sprintBody.textContent = error instanceof Error ? error.message : "Не получилось прочитать спринт";
  }
}

function applyTheme(name) {
  const chosen = name === "rick" || name === "simpsons" ? name : "classic";
  if (chosen === "classic") {
    delete document.body.dataset.theme;
  } else {
    document.body.dataset.theme = chosen;
  }
  if (theme) theme.value = chosen;
  localStorage.setItem(themeKey, chosen);
}

function setBusy(busy) {
  send.disabled = busy;
  text.disabled = busy;
}
