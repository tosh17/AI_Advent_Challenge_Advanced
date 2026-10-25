# Инструменты MCP

Список снят с `workServer` в `server/src/main/kotlin/app/work/server/mcp/WorkMcp.kt`. Jira и Confluence не вызываются. Вид активности задаёт инструмент, не текст человека.

| Инструмент | Обязательные поля | Что делает |
| --- | --- | --- |
| `activities_log` | `summary`, `on_date` | Пишет `ActivityKind.Log` |
| `activities_schedule` | `summary`, `on_date` | Пишет `ActivityKind.Schedule` |
| `activities_backlog` | `summary` | Пишет `ActivityKind.Backlog` без даты |
| `activities_add_to_sprint` | `summary` | Пишет `ActivityKind.Sprint` без даты |
| `activities_list_day` | `date` | Читает журнал (`ActivityKind.Log`) за день |
| `activities_plan` | `date` | Читает план (`ActivityKind.Schedule`) за день |
| `activities_list_sprint` | нет | Читает `ActivityKind.Sprint` |
| `activities_find` | `query` | Ищет по уже записанному: журнал, план, беклог и спринт |

Номер задачи `issue_key` и ссылка `link` необязательны там, где инструмент их принимает: у записи журнала, плана, беклога и спринта.
