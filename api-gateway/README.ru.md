[English](./README.md) | **Русский**

# API Gateway

Spring Cloud Gateway MVC направляет внешние API-запросы во внутренние сервисы.

## Порядок обработки

1. RequestIdFilter устанавливает ID запроса и контекст логирования.
2. IdentityHeaderFilter удаляет все входящие X-User-Id и X-User-Roles,
   включая публичные маршруты.
3. RateLimitFilter проверяет лимит непосредственного клиента или доверенной цепочки proxy.
4. TokenIntrospectionFilter проверяет Bearer и устанавливает подтверждённые заголовки.
   Отсутствующий/недействующий токен даёт 401; недоступная интроспекция — 503.

Публичные пути ограничены HTTP-методом в application.yml. Поиск каталога публичный;
необязательная авторизация разрешена только для числового ID объявления.
Мои объявления и черновики требуют авторизации. Внутренние пути сервисов через
gateway не публикуются.

## Маршруты

| Префикс | Сервис |
| --- | --- |
| /api/v1/auth/**, /api/v1/profile/**, /api/v1/users/** | Auth (:8081) |
| /api/v1/listings/**, /api/v1/moderation/** | Listing (:8082) |
| /api/v1/passports/** | Passport (:8083) |
| /api/v1/match/** | Matching (:8084) |

## Настройка

AUTH_SERVICE_HOST, LISTING_SERVICE_HOST, PASSPORT_SERVICE_HOST и MATCHING_SERVICE_HOST
по умолчанию localhost; SERVER_PORT — 8080. Таймаут интроспекции — 3 секунды.

Лимит по IP: 60 запросов в секунду, максимальный burst 120. У auth-маршрутов отдельная
квота 1 запрос/секунду, burst 10. Карта ограничена 10 000 записями клиент/категория;
неактивные записи удаляются через 15 минут. Активные квоты не вытесняются:
при заполнении новые клиенты получают 429. Ответ содержит Retry-After.

X-Forwarded-For по умолчанию игнорируется. HVOSTID_RATE_LIMIT_TRUSTED_PROXIES —
явный список IP доверенных proxy через запятую. Edge должен перезаписывать
X-Forwarded-For адресом remote_addr. Gateway рассматривает цепочку справа налево
до первого недоверенного узла. При доверии proxy не открывайте gateway напрямую.
Лимиты локальны для процесса: используйте одну реплику gateway или общий
распределённый limiter до горизонтального масштабирования.

В production публикуйте только TLS edge. Actuator предназначен для внутреннего
мониторинга, nginx не должен публиковать его наружу.
Все значения свойств находятся в src/main/resources/application.yml.

## Запуск

```bash
docker compose up -d postgres minio minio-init auth-service listing-service passport-service matching-service
./gradlew :api-gateway:bootRun
./gradlew :api-gateway:test
```
