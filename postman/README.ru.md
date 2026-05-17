# Postman-коллекции

Пять Postman-коллекций (v2.1) плюс environment-файл `hvostid-local` для
ручной проверки API через gateway и для smoke-тестов через Newman в CI.

| Файл | Назначение |
| --- | --- |
| `COL-01-public-api.json` | Smoke-тест публичного API: register, login, refresh, profile, листинг каталога с фильтрами и без. |
| `COL-02-listing-passport.json` | Сценарий продавца: создание паспорта, загрузка фото и сертификата прививки, проверка trust score, создание объявления, перевод в MODERATION, одобрение модератором. |
| `COL-03-matching.json` | Matching покупателя: upsert анкеты, расчёт совместимости, рекомендации, негативные кейсы (нет анкеты, невалидный listingId). |
| `COL-04-passport-files.json` | Документы паспорта в MinIO: загрузка, список, presigned URL, trust score, удаление. |
| `COL-05-frontend-e2e.json` | Сквозной flow SPA: register -> login -> каталог -> просмотр объявления -> match score -> попытка создать объявление как BUYER (403) -> logout. |
| `hvostid-local.postman_environment.json` | Environment с `baseUrl`, тестовыми учётками и автоматически заполняемыми переменными для токенов и id. |

## Импорт в Postman (один клик)

1. Поднять стек локально:

    ```bash
    docker compose up -d
    ```

   Gateway должен отвечать на `http://localhost:8080`.

2. В Postman: **File -> Import** и выбрать все `COL-*.json` плюс
   `hvostid-local.postman_environment.json` из этой папки.
3. Выпадающий список environment'ов справа сверху -> выбрать
   **`hvostid-local`**.
4. Перед запуском заполнить `buyerPassword`, `sellerPassword`,
   `moderatorPassword` (по умолчанию там плейсхолдеры `<REPLACE_ME>` --
   реальные пароли в репозиторий не коммитим).
5. Запустить коллекцию через Runner или открывать запросы по одному и
   жать *Send*.

## Что коллекции делают за вас

- **Login сохраняет токены.** Test-скрипт у запроса login пишет
  `accessToken` и `refreshToken` в environment.
- **Все остальные запросы** авторизуются через
  `Authorization: Bearer {{accessToken}}` на уровне коллекции.
- **Авторефреш при 401.** В каждой коллекции есть collection-level
  `test`-hook: если запрос вернул 401 и есть `refreshToken`, hook
  асинхронно вызывает `POST /api/v1/auth/refresh`, пишет новые токены
  в environment, и следующий запрос подхватывает их автоматически.
  Сбой refresh логируется через `console.error`.
- **ID переезжают между запросами.** `passportId`, `listingId`,
  `documentId` записываются ранними запросами, поздние их читают.

Коллекции, которым нужна нестандартная роль (SELLER для COL-02 /
COL-04, MODERATOR для шага approve в COL-02), предполагают, что
аккаунты с указанными email уже существуют. Если нет -- либо измените
`sellerEmail` / `moderatorEmail` в environment, либо создайте их через
auth-service до запуска.

## CLI через Newman

```bash
# один раз установить
npm install -g newman

# прогон всех COL-*.json
./postman/run-newman.sh

# прогон одной коллекции
./postman/run-newman.sh COL-01-public-api

# подменить URL gateway без правки env
BASE_URL=http://localhost:8082 ./postman/run-newman.sh
```

`run-newman.sh` возвращает ненулевой exit-код при любом провале
ассертов и пишет JSON-отчёт по каждой коллекции в
`postman/.newman-<collection>.json` -- удобно сохранять как артефакт
CI.

### Опциональный smoke в CD

`COL-01-public-api.json` -- самая лёгкая из пяти и подходит для
post-deploy проверки (T26). Пример вызова в CD-шаге:

```bash
BASE_URL=https://staging.hvostid.example ./postman/run-newman.sh COL-01-public-api
```

## Заметки

- COL-02 и COL-04 ожидают, что вы приложите реальный файл в поле
  `file` формы перед отправкой. Если файла нет, запрос возвращает 400
  и test-скрипт принимает это как допустимый исход.
- Токены в environment помечены как `secret` -- Postman прячет их в
  UI, но в экспортированном файле они всё равно лежат в открытом
  виде, не делитесь экспортом с реальными значениями.
- Пароли в дефолтном env -- плейсхолдеры `<REPLACE_ME>`, тоже из
  соображений безопасности.
