[English](./README.md) | **Русский**

# Matching Service

Владеет анкетой покупателя и оценкой совместимости владельца и
питомца.

## Зоны ответственности

- CRUD анкеты покупателя с upsert-семантикой (одна анкета на
  покупателя; повторный submit перезаписывает).
- Расчёт оценки совместимости через **RestClient** к Listing Service и
  Passport Service (internal read). В ответе могут быть `degraded` и
  `degradedReason` при неполных данных паспорта.
- Возврат отсортированных рекомендаций покупателю.

## Эндпоинты

| Метод  | Путь                                   | Auth   | Заметки                          |
|--------|----------------------------------------|--------|----------------------------------|
| GET    | `/api/v1/match/questionnaire`          | buyer  | Прочитать ответы текущего покупателя |
| POST   | `/api/v1/match/questionnaire`          | buyer  | Upsert                           |
| GET    | `/api/v1/match/recommendations`        | buyer  | Отсортированные объявления       |
| POST   | `/api/v1/match/score`                  | buyer  | Оценка для одного объявления     |

`POST /api/v1/match/score` возвращает оценку, комментарии по факторам,
резюме, советы и 14-дневный план адаптации (`summary`, `tips`,
`adaptationPlan`).

```json
{
  "score": 78,
  "level": "GOOD",
  "factors": [
    { "name": "living_space", "score": 18, "maxScore": 20, "comment": "..." }
  ],
  "summary": "Good match overall. ...",
  "tips": ["Consider enrolling in a pet training course before adoption"],
  "adaptationPlan": [
    {
      "dayRange": "1-3",
      "title": "Getting to know each other",
      "tasks": ["Set up a quiet corner", "..."]
    }
  ],
  "degraded": false,
  "degradedReason": null
}
```

Полная спецификация: http://localhost:8084/swagger-ui.html.

## Переменные окружения

| Имя                     | Default             | Описание              |
|-------------------------|---------------------|-----------------------|
| `SERVER_PORT`           | `8084`              | HTTP-порт             |
| `DB_HOST`               | `localhost`         | Хост PostgreSQL       |
| `DB_NAME`               | `hvostid_matching`  | Имя базы              |
| `DB_USER`               | `hvostid`           | Пользователь БД       |
| `DB_PASSWORD`           | `hvostid`           | Пароль БД             |
| `LISTING_SERVICE_HOST`  | `localhost`         | Хост Listing Service  |
| `PASSPORT_SERVICE_HOST` | `localhost`         | Хост Passport Service |

## Локальный запуск

```bash
docker compose up -d postgres listing-service passport-service
./gradlew :matching-service:bootRun
```

## Зависимости

- **Обязательно:** PostgreSQL (база `hvostid_matching`).
- **Обязательно в рантайме:** Listing Service и Passport Service для
  рекомендаций и расчёта оценки.

## Актуальность рекомендаций

Готовые результаты не кешируются: новый запрос читает актуальные анкету, каталог и
паспорта. Одновременные запросы одной версии анкеты объединяются только на время
вычисления. Изменение анкеты начинает отдельный расчёт. Результат недоступности
паспорта перепроверяется при следующем запросе с учётом circuit breaker.

Обрабатываются все страницы каталога, включая объявления после первых 200.
Предпочтения по виду и породе отбирают кандидатов до обращения к паспортам; порода
сравнивается точно без учёта регистра. Результаты сортируются по оценке и затем ID.
При изменении каталога во время постраничного обхода состав следующей страницы
может измениться; новый запрос снова читает актуальное состояние.

Общий пул ограничен `MATCHING_PARALLELISM` (по умолчанию 16), а число разных расчётов —
`MATCHING_MAX_COMPUTATIONS` (8). Перегрузка очереди возвращает повторяемый HTTP 503.
Большой номер страницы безопасно возвращает пустой массив. При
`readyForAdaptation=false` API предлагает подготовку до принятия животного вместо
14-дневного плана приезда. Характер использует общий словарь английских и русских
описаний и коды `FRIENDLY`, `NERVOUS`, `CHALLENGING`, `ACTIVE`.

Тесты `MatchingFreshnessTest` проверяют 251 кандидата, предел параллелизма,
объединение запросов, изменение анкеты, удаление объявления и восстановление
паспортов. `PassportCircuitBreakerTest` проверяет открытие цепи после HTTP 500,
обработку 404 и восстановление через HALF_OPEN.
Вычисление рекомендаций ограничено `MATCHING_MAX_SCANNED_LISTINGS=10000`
просмотренных строк каталога и `MATCHING_COMPUTATION_TIMEOUT=30s`. Превышение
любого лимита возвращает HTTP 503 без частичного рейтинга: повторите позднее
или используйте фильтры каталога. Оба ограничения проверяются регрессионными тестами.
