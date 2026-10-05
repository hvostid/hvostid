# Аудит HvostID — 30 сентября 2026

Проверен репозиторий на коммите `2d942a301cb0bd109ec0b48785a8dc7d0b660a2b`, ветка `codex/fix-issues-175-177`. Охвачены пять backend-сервисов, common, frontend, миграции, тесты, Docker/Compose, CI/CD, скрипты, Postman/k6 и документация. В снимке — 491 отслеживаемый файл. Это аудит кода и конфигурации с выборочными воспроизведениями; он не означает проверки каждого возможного поведения приложения.

Результаты исправлений ведутся отдельно в [карте A01–A42](repository-audit-resolution-2026-09-30.ru.md). Этот документ сохраняет состояние до исправлений.

Собрано **42 пункта: 7 P1, 27 P2, 8 P3**. Пункты включают дефекты, незавершённые функции и предложения по улучшению; они не равнозначны 42 воспроизведённым багам.

- **P1** — исправить до публичного запуска: обход авторизации, потеря целостности данных, ненадёжная работа сессий или блокирующий запуск стека.
- **P2** — следующий цикл разработки: сломанные сценарии, некорректные результаты, устойчивость, проверки релиза.
- **P3** — дальнейшее развитие: эксплуатация, удобство, сопровождение и функции, объём которых нужно определить.
- **Воспроизведено** — есть исполняемая проверка текущего поведения.
- **По коду** — прослежена конкретная цепочка в реализации, без полного браузерного/сетевого сценария.
- **Риск** — есть предпосылки, но нужен отдельный эксперимент.
- **Недоработка / улучшение** — требуется завершить функцию или согласовать требуемое поведение.

## Состояние существующих задач

Открыты [#175 — поиск](https://github.com/hvostid/hvostid/issues/175), [#176 — потеря формы при создании паспорта](https://github.com/hvostid/hvostid/issues/176), [#177 — удаление паспорта](https://github.com/hvostid/hvostid/issues/177). Их основные исправления уже находятся в [PR #185](https://github.com/hvostid/hvostid/pull/185), который ещё открыт и ожидает обязательного ревью. На проверенном HEAD все 11 проверок PR успешны.

Эти три исходных бага не добавлены повторно. При этом обнаружены **остаточный обход защиты #177 через формат ID** (A06), межсервисная гонка (A12) и ограничения сохранения черновика (A39). Успешный CI #185 не проверяет эти сценарии и не подтверждает запуск всего Compose-стека.

## P1 — первоочередные исправления

### A01. Входящие заголовки позволяют выдать себя за другого пользователя

**Воспроизведено на gateway-фильтре; последствия прослежены по коду downstream.** На optional-auth маршруте запрос без Bearer-токена проходит с исходными `X-User-Id` и `X-User-Roles`. Шаблон `GET /api/v1/listings/*` захватывает также `/my` и `/draft`. Сервисы доверяют этим заголовкам как установленной gateway личности. Это позволяет подставить владельца или роль при чтении объявлений и черновиков.

**Где:** [TokenIntrospectionFilter.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/api-gateway/src/main/java/ru/hvostid/gateway/filter/TokenIntrospectionFilter.java#L120), [api-gateway/src/main/resources/application.yml](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/api-gateway/src/main/resources/application.yml#L84), [GatewayPreAuthentication.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/common/src/main/java/ru/hvostid/common/security/GatewayPreAuthentication.java#L35), [ListingService.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/listing-service/src/main/java/ru/hvostid/listing/service/ListingService.java#L86).

**Что сделать / готовность:** удалять все входящие заголовки идентификации до выбора публичного/защищённого маршрута, устанавливать их только после успешной интроспекции; сузить optional-auth до нужных URL. Сквозной тест через gateway должен подтвердить, что поддельные роли/ID не открывают чужие `my`, `draft` и неопубликованные объявления.

### A02. Свежий запуск Compose зависит от недоступных образов MinIO

**Воспроизведено обращением к registry.** В dev/prod Compose остаются `minio/minio` и `minio/mc` без фиксированного тега. Проверка обоих `:latest` вернула `pull access denied / repository does not exist / insufficient_scope`. Тестовый образ, добавленный в #185, используется тестами и не заменяет эти зависимости. На машине без локального кеша это блокирует штатный запуск; CD smoke также наследует базовый Compose.

**Где:** [docker-compose.yml](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/docker-compose.yml#L23), [docker-compose.prod.yml](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/docker-compose.prod.yml#L23), [scripts/seed-minio.sh](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/scripts/seed-minio.sh), [docker/minio-test/README.md](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/docker/minio-test/README.md), [.github/workflows/cd-main.yml](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/.github/workflows/cd-main.yml#L268). Upstream описывает [поставку из исходников](https://github.com/minio/minio#source-only-distribution).

**Что сделать / готовность:** выбрать поддерживаемую поставку S3-хранилища и клиента, зафиксировать версии/образы, обновить seed и healthcheck. Проверить полный `compose pull/up` без заранее скачанных образов, загрузку и чтение файла. Наличие старого кешированного образа не считается прохождением.

### A03. Ограничение частоты обходится через X-Forwarded-For

**Воспроизведено.** Три запроса с одного remote address проходят ограничитель ёмкостью 1 при подстановке трёх значений `X-Forwarded-For`. Берётся первый IP из пользовательского заголовка; nginx дописывает адрес в его конец. Дополнительно карта IP → bucket не имеет удаления старых ключей: рост памяти и отдельные лимиты на репликах остаются рисками.

**Где:** [RateLimitFilter.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/api-gateway/src/main/java/ru/hvostid/gateway/filter/RateLimitFilter.java#L39), [frontend/nginx.conf](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/frontend/nginx.conf#L13).

**Что сделать / готовность:** определить доверенные прокси, перезаписывать/проверять цепочку адресов, ограничить размер и время жизни хранилища лимитов; выделить бюджет для auth-маршрутов. Подмена заголовка с одного соединения не должна давать новый бюджет; нагрузочная проверка должна показать ограниченное потребление памяти.

### A04. Очистка сессий удаляет ещё действующие refresh-токены

**Воспроизведено с PostgreSQL.** Планировщик раз в 15 минут удаляет сессии по `expiresAt` access-токена. Его TTL — 30 минут, refresh — 7 дней. Поэтому простаивающая сессия исчезает примерно через 30–45 минут, хотя refresh ещё валиден.

**Где:** [SessionCleanupService.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/auth-service/src/main/java/ru/hvostid/auth/service/SessionCleanupService.java#L27), [SessionRepository.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/auth-service/src/main/java/ru/hvostid/auth/repository/SessionRepository.java#L18), [auth-service/src/main/resources/application.yml](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/auth-service/src/main/resources/application.yml#L23).

**Что сделать / готовность:** очищать по истечению refresh-токена, проверить индекс и граничные времена. После очистки expired-access + valid-refresh должен успешно обновляться, expired-refresh — удаляться.

### A05. Объявление можно опубликовать с непроверенным паспортом

**Воспроизведено с PostgreSQL.** Объявление с несуществующим `passportId=9223372036854775807` прошло DRAFT → MODERATION → PUBLISHED. Ни создание, ни переходы не проверяют существование и владельца паспорта. Аналогично по коду можно указать чужой ID. Проверка «есть опубликованное объявление» используется для доступа к паспортным данным/медиа, поэтому поддельная связь влияет и на границы доступа. Прямой тест раскрытия чужих данных не выполнялся.

**Где:** [ListingService.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/listing-service/src/main/java/ru/hvostid/listing/service/ListingService.java#L61), [ListingService.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/listing-service/src/main/java/ru/hvostid/listing/service/ListingService.java#L177), [ListingRequest.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/listing-service/src/main/java/ru/hvostid/listing/dto/ListingRequest.java#L43), [PassportDocumentService.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/passport-service/src/main/java/ru/hvostid/passport/service/PassportDocumentService.java#L119).

**Что сделать / готовность:** канонический числовой ID, проверка существования и владельца при привязке и переходе к публикации, запрет ссылок на удалённые паспорта. Определить допустимость нескольких активных объявлений на один паспорт и обеспечить правило на сервере. Проверить чужой, отсутствующий, удалённый и повторно используемый паспорт.

### A06. Защита удаления паспорта пропускает ID с ведущими нулями

**Воспроизведено с PostgreSQL.** DTO принимает `"00042"`. Объявление с таким ID переводится в MODERATION, но `hasActiveListingForPassport(42)` возвращает false: поиск проверяет каноническую строку и legacy-формат. Следовательно, новая защита из #177 пропускает этот вариант.

**Где:** [ListingRequest.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/listing-service/src/main/java/ru/hvostid/listing/dto/ListingRequest.java#L43), [ListingService.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/listing-service/src/main/java/ru/hvostid/listing/service/ListingService.java#L151), [PassportService.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/passport-service/src/main/java/ru/hvostid/passport/service/PassportService.java#L120).

**Что сделать / готовность:** нормализовать ID при записи и мигрировать существующие значения; не полагаться на перебор строковых вариантов. Проверить `42`, `00042`, `passport-42`, границы типа и некорректные значения. У всех допустимых представлений должно быть одно поведение удаления.

### A07. Изменения опубликованного животного обходят повторную модерацию

**Воспроизведено для объявления; по коду для паспорта и документов.** После публикации можно заменить название и вид животного, сохранив PUBLISHED. В паспортном API проверяется владелец, но не состояние связанного объявления: ограничения формы обходятся прямым запросом, включая изменения документов. Это делает одобренное содержимое изменяемым после проверки.

**Где:** [ListingService.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/listing-service/src/main/java/ru/hvostid/listing/service/ListingService.java#L115), [PassportService.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/passport-service/src/main/java/ru/hvostid/passport/service/PassportService.java#L98), [PassportDocumentService.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/passport-service/src/main/java/ru/hvostid/passport/service/PassportDocumentService.java#L69), [PassportFormPage.jsx](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/frontend/src/pages/PassportFormPage.jsx#L133).

**Что сделать / готовность:** согласовать поля, которые требуют повторной проверки, и закрепить правило на сервере; варианты — возврат в MODERATION или публикация одобренной версии до нового решения. Изменение вида/идентичности/документов должно соблюдать это правило; изменение цены можно отдельно разрешить по продуктовой политике.

## P2 — авторизация, данные и API

### A08. Параллельное обновление токенов разлогинивает пользователя

**Воспроизведено на реальном коде frontend с имитацией HTTP.** Два одновременных 401 запускают два refresh со старым токеном. Первый сохраняет новую пару, второй получает отказ после ротации и удаляет уже новые токены, выполняя переход на login.

**Где:** [client.js](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/frontend/src/api/client.js#L23), [AuthService.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/auth-service/src/main/java/ru/hvostid/auth/service/AuthService.java#L137).

**Что сделать / готовность:** один общий refresh-запрос на вкладку, ожидание его результата всеми исходными запросами; определить координацию между вкладками. Проверка двух и более одновременных 401 должна дать один refresh и успешный повтор запросов.

### A09. Временная недоступность сервисов выглядит как невалидная сессия

**По коду.** Ошибка сети/5xx интроспекции превращается в empty и затем 401. AuthContext очищает токены при любой ошибке загрузки профиля, interceptor — при любой ошибке refresh. Краткий сбой сервера заставляет пользователя входить заново.

**Где:** [IntrospectionClient.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/api-gateway/src/main/java/ru/hvostid/gateway/client/IntrospectionClient.java#L35), [TokenIntrospectionFilter.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/api-gateway/src/main/java/ru/hvostid/gateway/filter/TokenIntrospectionFilter.java#L150), [AuthContext.jsx](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/frontend/src/context/AuthContext.jsx#L27), [client.js](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/frontend/src/api/client.js#L41).

**Что сделать / готовность:** отличать отказ авторизации от недоступности зависимости, возвращать корректный 503, сохранять сессию при временном сбое и показывать возможность повторить. Проверить отдельно invalid token, timeout, 500 и offline.

### A10. Ограничения auth DTO расходятся с хранилищем и password encoder

**Пароль воспроизведён; остальные несоответствия — по коду.** DTO обещает 8–128 символов, BCrypt принимает не более 72 байт. Регистрация с 73 ASCII-символами вернула 400 с внутренним текстом encoder; для Unicode граница наступает раньше. Для email нет согласованного ограничения под VARCHAR(255) и явной политики пробелов/регистра.

**Где:** [RegisterRequest.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/auth-service/src/main/java/ru/hvostid/auth/dto/RegisterRequest.java#L14), [SecurityConfig.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/auth-service/src/main/java/ru/hvostid/auth/config/SecurityConfig.java#L44), [AuthService.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/auth-service/src/main/java/ru/hvostid/auth/service/AuthService.java#L59), [auth-service/src/main/resources/db/migration/V1__create_users_and_sessions.sql](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/auth-service/src/main/resources/db/migration/V1__create_users_and_sessions.sql).

**Что сделать / готовность:** согласовать контракт, UI и алгоритм хранения паролей, валидировать до encoder; выбрать и применить политику email при регистрации/входе/уникальности. Проверить Unicode, граничные размеры и нормализованные дубликаты; не обрезать пароль молча.

### A11. Валидация и обновление объявлений не соответствуют контракту

**Частично воспроизведено, остальное — по коду.** `passportId` присутствует в update DTO, но сервис его игнорирует (проверено тестом). Заголовок из пробелов проходит Size, затем нормализуется в пустую строку; species/city можно очистить; ограничения длины некоторых полей не совпадают с VARCHAR(255). Предварительная проверка дубликата заголовка регистрозависима, а уникальный индекс использует LOWER(title); конфликт может дойти до общего 500 вместо 409.

**Где:** [ListingUpdateRequest.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/listing-service/src/main/java/ru/hvostid/listing/dto/ListingUpdateRequest.java#L11), [ListingService.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/listing-service/src/main/java/ru/hvostid/listing/service/ListingService.java#L115), [ListingRepository.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/listing-service/src/main/java/ru/hvostid/listing/repository/ListingRepository.java), [listing-service/src/main/resources/db/migration/V2__add_indexes_and_status_features.sql](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/listing-service/src/main/resources/db/migration/V2__add_indexes_and_status_features.sql#L11).

**Что сделать / готовность:** единые правила нормализации/непустоты/длины, явная неизменяемость либо безопасная смена паспорта, перевод ожидаемых конфликтов БД в 409. Тесты на пробелы, длины, регистровые дубликаты, update паспорта и конкурентное создание.

### A12. Межсервисные операции и модерация требуют проверки гонок

**Риск; конкурентное воспроизведение не выполнялось.** Удаление паспорта проверяет listing-service, затем удаляет локальную запись; публикация может произойти между действиями. Черновик также можно сохранить, удалить его паспорт и потом опубликовать (связано с A05). Обычные смены статуса объявления выполняются read → validate → write без версии. При одновременных жалобах каждая транзакция может увидеть меньше пороговых трёх записей. У жалоб уже есть атомарный переход статуса, защищающий от двойной истории, но он не устраняет риск видимости счётчика.

**Где:** [PassportService.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/passport-service/src/main/java/ru/hvostid/passport/service/PassportService.java#L120), [ListingService.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/listing-service/src/main/java/ru/hvostid/listing/service/ListingService.java#L177), [ListingFlagService.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/listing-service/src/main/java/ru/hvostid/listing/service/ListingFlagService.java#L77), [AuthService.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/auth-service/src/main/java/ru/hvostid/auth/service/AuthService.java#L137).

**Что сделать / готовность:** согласованный протокол удаления/публикации, optimistic locking или условные обновления, сериализация/перепроверка порога жалоб; отдельно проверить refresh-ротацию. Параллельные интеграционные тесты должны исключать висячую связь, потерянную смену статуса и пропуск порога.

## P2 — паспорта, медиа и TrustScore

### A13. Через штатный nginx не проходят заявленные файлы до 10 MB

**По конфигурации и документации nginx.** Passport-service допускает файл 10 MB и запрос 20 MB, но frontend nginx не задаёт `client_max_body_size`. Его [значение по умолчанию — 1m](https://nginx.org/en/docs/http/ngx_http_core_module.html#client_max_body_size), поэтому более крупный multipart-запрос отклоняется раньше. На gateway также нужно явно проверить/согласовать multipart-ограничения: [Spring Boot по умолчанию задаёт 1 MB на файл](https://docs.spring.io/spring-boot/4.0/api/java/org/springframework/boot/servlet/autoconfigure/MultipartProperties.html).

**Где:** [frontend/nginx.conf](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/frontend/nginx.conf#L9), [passport-service/src/main/resources/application.yml](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/passport-service/src/main/resources/application.yml#L22), [PassportDocumentValidator.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/passport-service/src/main/java/ru/hvostid/passport/service/PassportDocumentValidator.java#L15).

**Что сделать / готовность:** единый лимит всей цепочки с запасом на multipart overhead, понятный 413 в UI. Сквозные загрузки 1, 5, 10 и более 10 MB через frontend URL, без обращения к сервису напрямую.

### A14. Форма редактирует прививки, которые API не сохраняет

**По коду; сквозная проверка браузером не выполнялась.** PassportFormPage позволяет добавить/удалить прививку, отправляет `vaccinations` и сообщает об успешном сохранении паспорта. В Create/UpdatePassportRequest поля нет, в PassportService записи прививок не обновляются. Редактирование не имеет серверной реализации, хотя чтение и demo-данные есть.

**Где:** [PassportFormPage.jsx](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/frontend/src/pages/PassportFormPage.jsx#L258), [PassportFormPage.jsx](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/frontend/src/pages/PassportFormPage.jsx#L284), [UpdatePassportRequest.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/passport-service/src/main/java/ru/hvostid/passport/dto/UpdatePassportRequest.java#L11), [PassportService.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/passport-service/src/main/java/ru/hvostid/passport/service/PassportService.java#L98).

**Что сделать / готовность:** реализовать владельческие операции с прививками, валидацию дат и отдельные права подтверждения. После сохранения и перезагрузки добавление, изменение и удаление должны сохраняться; продавец не должен сам выставлять verified.

### A15. TrustScore содержит недоступные составляющие и незавершённый UI документов

**Недоработка, подтверждена кодом.** DefaultSellerSignalsProvider всегда возвращает пустые рейтинг/продажи (20 баллов недоступны). Нет штатного потока установки moderated (5 баллов) и записи прививок (10 баллов, A14). Для нового паспорта доступный максимум через имеющийся API — 65/100, поэтому High trust (>70) недостижим без внешнего заполнения данных. Через форму документов возможности ещё уже: PDF всегда OTHER, изображения PHOTO; VACCINATION_CERT и VET_RECORD не выбираются.

**Где:** [DefaultSellerSignalsProvider.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/passport-service/src/main/java/ru/hvostid/passport/service/DefaultSellerSignalsProvider.java#L13), [TrustScoreCalculator.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/passport-service/src/main/java/ru/hvostid/passport/service/TrustScoreCalculator.java#L16), [TrustBadge.jsx](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/frontend/src/components/TrustBadge.jsx#L19), [PassportFormPage.jsx](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/frontend/src/pages/PassportFormPage.jsx#L339).

**Что сделать / готовность:** подключить реальные источники сигналов и подтверждение, добавить выбор типа документа либо пересмотреть отображаемую модель баллов. Обычный пользователь должен видеть честное объяснение недостающих данных и иметь рабочий путь получения каждого обещанного сигнала.

### A16. Файлы проверяются по имени и заявленному MIME, без проверки содержимого

**По коду.** Validator проверяет расширение, Content-Type и размер, но не байты файла и не соответствие DocumentType. API позволяет передать PDF как PHOTO; это влияет на обложку и наличие фото в TrustScore.

**Где:** [PassportDocumentValidator.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/passport-service/src/main/java/ru/hvostid/passport/service/PassportDocumentValidator.java#L29), [PassportDocumentService.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/passport-service/src/main/java/ru/hvostid/passport/service/PassportDocumentService.java#L69).

**Что сделать / готовность:** проверять сигнатуру/декодирование и совместимость с типом документа, ограничивать размеры изображения и объём файлов владельца. Тесты на подменённый MIME, невалидное изображение и PHOTO+PDF. Необходимость антивирусной проверки определить по принимаемым форматам и условиям эксплуатации.

### A17. PostgreSQL и объектное хранилище могут расходиться после ошибки

**Риск по последовательности операций.** Удаление документа удаляет объект внутри DB-транзакции до её успешного завершения: откат может вернуть запись без файла. При удалении паспорта запись удаляется раньше очистки объектов; неудачная очистка только логируется, долговечного повторного задания нет. Компенсация upload также не покрывает все ошибки на стадии commit.

**Где:** [PassportDocumentService.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/passport-service/src/main/java/ru/hvostid/passport/service/PassportDocumentService.java#L191), [PassportDocumentService.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/passport-service/src/main/java/ru/hvostid/passport/service/PassportDocumentService.java#L210), [PassportService.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/passport-service/src/main/java/ru/hvostid/passport/service/PassportService.java#L130).

**Что сделать / готовность:** durable outbox/очередь очистки, идемпотентный повтор и сборщик сирот либо другой явный протокол согласованности. Fault-injection проверки: недоступный MinIO, rollback/commit failure, перезапуск между шагами.

## P2 — matching-service

### A18. Кеш не сбрасывается после изменения анкеты или объявления

**По коду.** Match score кешируется по user+listing, рекомендации — по user, оба на 10 минут. Сохранение анкеты не инвалидирует кеш. Изменение бюджета/аллергии/условий жизни может оставлять старый результат; проданные/архивированные объявления и результаты временной деградации также остаются до TTL.

**Где:** [CacheConfig.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/matching-service/src/main/java/ru/hvostid/matching/config/CacheConfig.java#L23), [QuestionnaireService.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/matching-service/src/main/java/ru/hvostid/matching/service/QuestionnaireService.java#L24), [MatchScoreService.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/matching-service/src/main/java/ru/hvostid/matching/service/MatchScoreService.java#L50), [MatchRecommendationsService.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/matching-service/src/main/java/ru/hvostid/matching/service/MatchRecommendationsService.java#L89).

**Что сделать / готовность:** инвалидировать оба кеша при изменении анкеты, учитывать версии/состояния связанных данных, не кешировать деградацию как полноценный результат. Проверить изменение анкеты, продажу объявления и восстановление passport-service без ожидания 10 минут.

### A19. Circuit breaker passport-service не видит обычные сетевые ошибки

**По коду.** Метод с @CircuitBreaker перехватывает RestClientException и возвращает Optional.empty. Вызов заканчивается нормально, поэтому механизм не учитывает эти ошибки как отказы и не открывает цепь по их частоте.

**Где:** [PassportServiceClient.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/matching-service/src/main/java/ru/hvostid/matching/client/PassportServiceClient.java#L23), [PassportServiceClient.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/matching-service/src/main/java/ru/hvostid/matching/client/PassportServiceClient.java#L42).

**Что сделать / готовность:** оставить 404 отдельным результатом, а недоступность передавать механизму отказоустойчивости; возвращать fallback после учёта ошибки. Тест с настоящим CircuitBreakerRegistry должен показать CLOSED → OPEN после серии 5xx/timeout и восстановление через HALF_OPEN.

### A20. Часть анкеты не влияет на рекомендации

**Недоработка по коду.** preferredSpecies, preferredBreed и readyForAdaptation сохраняются, но не используются при выборе кандидатов, расчёте или построении плана. Выбор кошки не исключает собак, а отказ от адаптации не меняет план. Темперамент дополнительно распознаётся по ограниченным английским подстрокам, хотя интерфейс принимает русский свободный текст.

**Где:** [QuestionnaireService.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/matching-service/src/main/java/ru/hvostid/matching/service/QuestionnaireService.java#L61), [CompatibilityScoreCalculator.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/matching-service/src/main/java/ru/hvostid/matching/service/CompatibilityScoreCalculator.java#L104), [AdaptationPlanBuilder.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/matching-service/src/main/java/ru/hvostid/matching/service/AdaptationPlanBuilder.java), [QuestionnairePage.jsx](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/frontend/src/pages/QuestionnairePage.jsx#L38).

**Что сделать / готовность:** определить поля-фильтры и предпочтения, подключить их либо явно обозначить пока неиспользуемые поля. Для характеристик предпочесть структурированные значения и согласованные локализованные словари; проверить влияние каждого поля на результат.

### A21. Рекомендации ограничены первыми 200 объявлениями и создают большой поток запросов

**Ограничение по коду; нагрузочный риск не измерен.** Берутся первые 200 опубликованных кандидатов; более старое подходящее объявление вне выборки не попадёт в результат. Каждый промах кеша запускает до 200 параллельных оценок с HTTP-запросами к паспортам. Virtual threads уменьшают стоимость ожидания, но не ограничивают общую нагрузку нескольких пользователей и одинаковых cache misses.

**Где:** [MatchRecommendationsService.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/matching-service/src/main/java/ru/hvostid/matching/service/MatchRecommendationsService.java#L35), [MatchRecommendationsService.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/matching-service/src/main/java/ru/hvostid/matching/service/MatchRecommendationsService.java#L117), [ListingServiceClient.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/matching-service/src/main/java/ru/hvostid/matching/client/ListingServiceClient.java).

**Что сделать / готовность:** отбирать кандидатов по предпочтениям до оценки, явно описать полноту выдачи, добавить общий лимит параллелизма и объединение одинаковых вычислений. Нагрузочные проверки холодного кеша и каталог >200 должны измерять задержки/ошибки и показывать ожидаемое качество отбора.

### A22. Большой номер страницы переполняет int при пагинации рекомендаций

**По коду.** `page * size` вычисляется в int, page имеет только нижнюю границу. Например, page=2147483647 и size=20 дают отрицательный offset, после чего subList получает недопустимый индекс и возможен 500.

**Где:** [MatchRecommendationsService.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/matching-service/src/main/java/ru/hvostid/matching/service/MatchRecommendationsService.java#L68), [MatchRecommendationsController.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/matching-service/src/main/java/ru/hvostid/matching/controller/MatchRecommendationsController.java).

**Что сделать / готовность:** безопасное вычисление в long/проверка диапазона до умножения, согласованный ответ на страницу вне выдачи. Проверить максимальный int, отрицательные значения и пустой результат.

## P2 — frontend и пользовательские сценарии

### A23. Публичная карточка объявления инициирует защищённые запросы и отправляет гостя на login

**По коду, без браузерного воспроизведения.** Маршрут объявления публичный, но эффект без проверки авторизации запрашивает паспорт, trust и документы. Для гостя эти запросы получают 401; общий interceptor сразу меняет location на login.

**Где:** [App.jsx](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/frontend/src/App.jsx#L38), [ListingDetailPage.jsx](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/frontend/src/pages/ListingDetailPage.jsx#L116), [client.js](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/frontend/src/api/client.js#L23).

**Что сделать / готовность:** разделить публичные данные/обложку и авторизованные дополнительные запросы. Прямая ссылка на опубликованное объявление должна работать без сессии, а недоступные действия — предлагать вход без принудительного перенаправления.

### A24. Legacy passport-ID поддерживается не во всех слоях

**По коду.** Backend допускает `passport-N`, matching его разбирает, но страницы передают строку напрямую в паспортные endpoint с Long ID. Сравнение занятости паспорта также выполняется по необработанным строкам. Это даёт 400 или неверное определение связи на старых данных.

**Где:** [ListingRequest.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/listing-service/src/main/java/ru/hvostid/listing/dto/ListingRequest.java#L43), [MatchScoreService.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/matching-service/src/main/java/ru/hvostid/matching/service/MatchScoreService.java), [ListingDetailPage.jsx](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/frontend/src/pages/ListingDetailPage.jsx#L118), [MyListingsPage.jsx](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/frontend/src/pages/MyListingsPage.jsx#L191), [CreateListingPage.jsx](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/frontend/src/pages/CreateListingPage.jsx#L61).

**Что сделать / готовность:** общий канонический контракт и миграция данных, временный адаптер на границе API. Одинаково проверить старые и новые ID во всех страницах, обложках и защитах удаления.

### A25. Каталог сбрасывает страницу из URL при первом открытии

**По коду.** Начальная page читается из query, но debounce-эффект через 300 ms безусловно вызывает сброс в 0, даже когда поисковый текст не изменился. Ссылка `?page=3` не удерживает нужную страницу. Изменение searchParams навигацией также не становится новым состоянием фильтров, которые читаются только при инициализации.

**Где:** [CatalogPage.jsx](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/frontend/src/pages/CatalogPage.jsx#L61), [CatalogPage.jsx](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/frontend/src/pages/CatalogPage.jsx#L73).

**Что сделать / готовность:** сбрасывать страницу только при реальном изменении фильтра, синхронизировать URL и состояние без циклов. Проверить прямую ссылку, reload, Back/Forward и переход между сохранёнными фильтрами.

### A26. Личный кабинет и выбор паспорта молча обрезают данные до 100 записей

**По коду.** MyListings и CreateListing запрашивают только первую страницу объявлений/паспортов, без перехода к следующей. Занятые паспорта определяются по неполному списку; при этом в used-набор попадают и неактивные статусы, что мешает повторно использовать архивный паспорт.

**Где:** [listings.js](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/frontend/src/api/listings.js#L14), [MyListingsPage.jsx](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/frontend/src/pages/MyListingsPage.jsx#L182), [MyListingsPage.jsx](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/frontend/src/pages/MyListingsPage.jsx#L225), [CreateListingPage.jsx](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/frontend/src/pages/CreateListingPage.jsx#L43).

**Что сделать / готовность:** пагинация/поиск в UI и серверный запрос допустимых паспортов с согласованными статусами. Пользователь с 101+ записью должен видеть и обрабатывать все данные без неверной блокировки выбора.

### A27. Действия UI расходятся с переходами отклонённых и архивных объявлений

**По коду.** Для REJECTED интерфейс предлагает сразу отправить объявление в MODERATION, но сервер разрешает из REJECTED только переход в DRAFT: кнопка повторной модерации ведёт к ошибке. Рабочий обход через «Вернуть в черновики» в UI есть. EditListing также запрещает прямое редактирование REJECTED, хотя API его разрешает. У ARCHIVED в меню есть удаление, но нет возврата в DRAFT, хотя такой переход предусмотрен и текст архивации обещает восстановление.

**Где:** [EditListingPage.jsx](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/frontend/src/pages/EditListingPage.jsx#L71), [MyListingsPage.jsx](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/frontend/src/pages/MyListingsPage.jsx#L18), [StatusTransitionValidator.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/listing-service/src/main/java/ru/hvostid/listing/service/StatusTransitionValidator.java).

**Что сделать / готовность:** согласовать матрицу статусов/действий между сервером и UI. Проверить «отклонено → исправлено → повторная модерация» и «архив → черновик → публикация».

### A28. Ошибка сохранения объявления убирает форму и введённые данные

**По коду.** Ошибка update записывается в тот же error, что и ошибка загрузки. Условие `if (error || !listing)` заменяет форму целиком экраном ошибки; локальное состояние ListingForm теряется.

**Где:** [EditListingPage.jsx](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/frontend/src/pages/EditListingPage.jsx#L42), [EditListingPage.jsx](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/frontend/src/pages/EditListingPage.jsx#L59).

**Что сделать / готовность:** раздельные ошибки загрузки/сохранения, сохранение введённых значений и повтор отправки на месте. Проверить 400, 409, 500 и offline после редактирования нескольких полей.

### A29. Асинхронные формы и списки могут показывать устаревший результат

**По коду; порядок реальных сетевых ответов не воспроизводился.** RecommendationsPage не отменяет предыдущие запросы и не проверяет их поколение; старый ответ может перезаписать новый фильтр/список. Ошибка не очищается при успешном повторе. QuestionnairePage при любом сбое загрузки оставляет редактируемую форму с defaults: пользователь может перезаписать существующую анкету. `childrenAgeMin || ''` и `monthlyBudget || ''` теряют допустимый ноль.

**Где:** [RecommendationsPage.jsx](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/frontend/src/pages/RecommendationsPage.jsx#L45), [QuestionnairePage.jsx](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/frontend/src/pages/QuestionnairePage.jsx#L78), [MyListingsPage.jsx](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/frontend/src/pages/MyListingsPage.jsx#L182).

**Что сделать / готовность:** отмена/игнорирование устаревших ответов, различение 404 и временной ошибки, `??` для допустимых нулей. Проверить ответы в обратном порядке, повтор после ошибки и загрузку нулевых значений.

### A30. После заполнения анкеты теряется возврат к выбранному животному

**По коду.** ListingDetail передаёт `?return=...`, QuestionnairePage читает `returnUrl`. При отсутствии анкеты путь к результату совместимости теряется после её сохранения.

**Где:** [ListingDetailPage.jsx](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/frontend/src/pages/ListingDetailPage.jsx#L533), [QuestionnairePage.jsx](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/frontend/src/pages/QuestionnairePage.jsx#L56).

**Что сделать / готовность:** единое имя параметра и проверка допустимого внутреннего маршрута. Первый расчёт совместимости → заполнение анкеты → результат именно исходного объявления.

## P2 — зависимости, поставка и завершённость продукта

### A31. Обновления зависимостей и security findings требуют отдельного разбора

**Воспроизведено npm audit на lockfile.** Найдено 11 затронутых записей зависимостей: 8 high, 2 moderate, 1 low, 0 critical; среди них axios, react-router/react-router-dom, vite и postcss. Это не означает 11 доступных для эксплуатации уязвимостей в приложении: часть относится к инструментам разработки, Node/SSR и транзитивным сценариям.

Последний просмотренный завершившийся [Security Scan от 27 сентября](https://github.com/hvostid/hvostid/actions/runs/36307770734) завершился ошибкой OWASP dependency-check; более новые запуски были отменены. Успешный PR CI не заменяет разбор этого отчёта.

**Где:** [frontend/package-lock.json](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/frontend/package-lock.json), [gradle/libs.versions.toml](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/gradle/libs.versions.toml), [.github/workflows/security-scan.yml](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/.github/workflows/security-scan.yml), [tmp/repository-audit/npm-audit.json] (локальный артефакт аудита, не включён в Git).

**Что сделать / готовность:** разнести runtime/dev и применимые/неприменимые advisory, обновить с проверкой поведения; для исключений фиксировать причину и срок. Использовать существующие [PR #180](https://github.com/hvostid/hvostid/pull/180), [#184](https://github.com/hvostid/hvostid/pull/184), [#179](https://github.com/hvostid/hvostid/pull/179), не создавать дубликаты. Повторить оба сканирования после обновления.

### A32. Frontend и межсервисные сценарии практически не защищены CI

**По конфигурации.** У frontend нет test-script/набора тестов; PR проверяет его сборку, но не запускает npm lint/audit и сценарии пользователя. Текущий lint: 0 ошибок, 10 предупреждений, включая зависимости эффектов. Backend-тестов много, но проверки из A01–A30 отсутствуют или не проверяют требуемое поведение. JaCoCo формирует отчёт, обязательного локального порога покрытия в Gradle нет; наличие настроенного внешнего Sonar quality gate не подтверждено.

**Где:** [frontend/package.json](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/frontend/package.json), [.github/workflows/ci-pr.yml](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/.github/workflows/ci-pr.yml#L126), [build.gradle.kts](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/build.gradle.kts#L93), [tmp/repository-audit/frontend-lint.log] (локальный артефакт аудита, не включён в Git).

**Что сделать / готовность:** обязательный lint, компонентные тесты критических форм/interceptor, браузерные сценарии гостя/продавца/модератора и API-тесты через gateway. Включить значимые проверки A01/A04/A05/A08/A14/A23/A28; пороги покрытия вводить для важных ветвей, а не ради числа.

### A33. Тег latest публикуется раньше сканирования и smoke-проверки

**По workflow.** CD сначала публикует SHA и latest, потом запускает scan и smoke. При провале этих стадий latest уже указывает на проблемные образы. Smoke проверяет пять health endpoint, но не frontend, загрузку файлов, вход, права или путь объявления. Postman и k6 не входят в этот workflow.

**Где:** [.github/workflows/cd-main.yml](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/.github/workflows/cd-main.yml#L108), [.github/workflows/cd-main.yml](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/.github/workflows/cd-main.yml#L139), [.github/workflows/cd-main.yml](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/.github/workflows/cd-main.yml#L268), [postman/README.ru.md](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/postman/README.ru.md), [k6/README.ru.md](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/k6/README.ru.md).

**Что сделать / готовность:** сначала публиковать кандидат по SHA, проверять его, затем продвигать latest/релиз. Добавить запуск Compose и минимальный бизнес-сценарий до продвижения, предусмотреть откат. Провал smoke/scan не должен менять стабильный тег.

### A34. Нет завершённого сценария связи покупателя с продавцом

**Продуктовая недоработка.** SellerCard показывает только `User #id`; действия — совместимость и жалоба. В профиле есть контактные поля, но в просмотренном UI/API нет рабочего пути связи после выбора животного.

**Где:** [ListingDetailPage.jsx](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/frontend/src/pages/ListingDetailPage.jsx#L509), [ListingDetailPage.jsx](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/frontend/src/pages/ListingDetailPage.jsx#L558), [ProfileController.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/auth-service/src/main/java/ru/hvostid/auth/controller/ProfileController.java).

**Что сделать / готовность:** определить минимальный путь контакта (например, заявка или согласованное раскрытие контакта авторизованному покупателю) и правила приватности. Пользователь должен пройти «нашёл животное → связался с продавцом» без ручного поиска по ID. Полноценный чат не является обязательным решением этого пункта.

## P3 — дальнейшее развитие и сопровождение

### A35. Жизненный цикл аккаунта и хранение сессий требуют развития

**Недоработка / усиление защиты.** Есть регистрация, вход, refresh/logout и профиль, но нет восстановления пароля, подтверждения email и управления всеми активными сессиями. Access/refresh хранятся открыто в DB, frontend — в localStorage. Само по себе это не доказательство доступной атаки, но увеличивает последствия утечки БД или XSS. AuthService пишет email в INFO/WARN, хотя документация обещает не логировать PII.

**Где:** [AuthController.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/auth-service/src/main/java/ru/hvostid/auth/controller/AuthController.java), [Session.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/auth-service/src/main/java/ru/hvostid/auth/entity/Session.java#L19), [AuthService.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/auth-service/src/main/java/ru/hvostid/auth/service/AuthService.java#L70), [client.js](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/frontend/src/api/client.js).

**Что сделать / готовность:** определить требования к восстановлению/верификации/отзыву сессий, убрать лишний email из логов, рассмотреть хеширование opaque-токенов и модель хранения браузерной сессии с учётом CSRF. Проверить отзыв всех сессий после значимых изменений безопасности аккаунта.

### A36. Диалоги и формы недостаточно доступны с клавиатуры и screen reader

**По коду.** ConfirmDialog не задаёт role/aria-modal, не управляет фокусом, Escape и возвратом фокуса; overlay может закрыть диалог во время запроса. Input не связывает ошибку через aria-describedby/aria-invalid. Нужна проверка подписей, порядка фокуса и состояний ошибок.

**Где:** [ConfirmDialog.jsx](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/frontend/src/components/ConfirmDialog.jsx), [Input.jsx](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/frontend/src/components/Input.jsx).

**Что сделать / готовность:** доступный базовый диалог и поля, автоматические a11y-проверки плюс ручной проход клавиатурой. Пользователь должен завершать формы и подтверждения без мыши и понимать сообщение об ошибке.

### A37. Не завершены локализация, fallback-экраны и общие UI-компоненты

**По коду / улучшение.** Русские и английские тексты смешаны; наборы видов/статусов повторяются в страницах. Нет catch-all страницы неизвестного маршрута и общего ErrorBoundary. PassportCard обращается к `/def.svg`, хотя в public есть `def.png`, а соответствующего SVG нет: резервная картинка также может оказаться битой.

**Где:** [PassportCard.jsx](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/frontend/src/components/PassportCard.jsx#L7), [PassportCard.jsx](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/frontend/src/components/PassportCard.jsx#L60), [App.jsx](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/frontend/src/App.jsx), [CatalogPage.jsx](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/frontend/src/pages/CatalogPage.jsx), [CreatePassportPage.jsx](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/frontend/src/pages/CreatePassportPage.jsx).

**Что сделать / готовность:** единый язык или i18n-слой, общие справочники, 404/ErrorBoundary, существующий fallback-asset. Проверить неизвестный URL, отказ lazy/компонента и отсутствующее фото.

### A38. Списки паспортов создают лишние запросы к DB и HTTP

**Риск масштабирования по коду, без замера профиля.** Пагинированный запрос паспортов не загружает vaccinations заранее, а DTO читает коллекцию у каждой записи. Это потенциальный N+1 внутри рабочей транзакции, не LazyInitializationException. Frontend дополнительно получает документы и ticket для каждого паспорта, до двух дополнительных HTTP-вызовов на карточку.

**Где:** [PetPassportRepository.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/passport-service/src/main/java/ru/hvostid/passport/repository/PetPassportRepository.java#L18), [PassportResponse.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/passport-service/src/main/java/ru/hvostid/passport/dto/PassportResponse.java#L43), [MyListingsPage.jsx](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/frontend/src/pages/MyListingsPage.jsx#L116).

**Что сделать / готовность:** измерить SQL/HTTP на страницу, применить batch-fetch/projection и пакетные обложки/готовые summary DTO, ограничить параллелизм. Проверка 100 записей должна иметь предсказуемое число запросов без загрузки всех файлов.

### A39. Сохранение черновика не покрывает reload и несколько вкладок

**Ограничение текущей реализации, не повтор исходного #176.** Сохраняется одна форма продавца при переходе к следующему шагу; это не непрерывное автосохранение ввода. До сохранения шага reload может потерять изменения; несколько вкладок используют одну запись, последнее сохранение побеждает, успешное создание очищает общий черновик. Выбор паспорта не входит в тот же сохранённый сценарий.

**Где:** [CreateListingPage.jsx](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/frontend/src/pages/CreateListingPage.jsx), [ListingDraftService.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/listing-service/src/main/java/ru/hvostid/listing/service/ListingDraftService.java), [listing-service/src/main/resources/db/migration/V6__add_listing_form_drafts.sql](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/listing-service/src/main/resources/db/migration/V6__add_listing_form_drafts.sql).

**Что сделать / готовность:** решить, нужен один черновик или несколько, добавить версию/идентификатор формы, автосохранение с debounce и понятный индикатор. Проверить reload до/после перехода, две вкладки и возврат после создания нового паспорта.

### A40. Для эксплуатации не хватает проверяемого восстановления и наблюдаемости

**Улучшение готовности к production, не утверждение о текущей внешней инфраструктуре.** В репозитории нет полноценного backup/restore runbook для PostgreSQL+объектов и регулярного теста восстановления. Compose предоставляет HTTP без встроенного TLS termination; внешний ingress может решать это, но его конфигурация не представлена. Сервисы используют доверие к внутренним заголовкам; нужно явно описать сетевую границу. Логи/health есть, сквозные метрики задержек/ошибок, алерты и tracing в репозитории не доведены до эксплуатационного сценария.

**Где:** [docker-compose.prod.yml](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/docker-compose.prod.yml), [docker/init-databases.sql](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/docker/init-databases.sql), [docs/architecture.ru.md](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/docs/architecture.ru.md), [GatewayPreAuthentication.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/common/src/main/java/ru/hvostid/common/security/GatewayPreAuthentication.java).

**Что сделать / готовность:** документировать TLS/секреты/изоляцию сервисов, раздельные DB-права, RPO/RTO, резервные копии и восстановление, dashboard/alert на ключевые операции. Приёмка — восстановление чистого окружения с данными и обнаружение специально внесённого отказа. Ограничения памяти в prod Compose уже есть; их отсутствие не является находкой этого аудита.

### A41. Сборки зависят от плавающих внешних компонентов

**Улучшение воспроизводимости.** Есть плавающие Docker-теги, milestone/snapshot repositories и зависимости предварительных выпусков. Для Gradle не обнаружены dependency verification metadata/lockfiles. Обновления actions/зависимостей уже частично открыты Dependabot, но их нужно завершать согласованно.

**Где:** [build.gradle.kts](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/build.gradle.kts#L37), [gradle/libs.versions.toml](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/gradle/libs.versions.toml), [frontend/Dockerfile](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/frontend/Dockerfile), [docker-compose.yml](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/docker-compose.yml), [.github/dependabot.yml](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/.github/dependabot.yml).

**Что сделать / готовность:** фиксировать критичные runtime-образы и целевые версии, ограничить нестабильные repositories реальной необходимостью, рассмотреть verification/locking и SBOM. Проверять воспроизводимость сборки в чистом окружении и обновлять фиксации регулярным процессом.

### A42. README и архитектура расходятся с реализацией

**По сопоставлению файлов.** README указывает React 18/Router 6 и JUnit 5 при фактических 19/7 и 6; некоторые уже реализованные функции всё ещё описаны как план. Архитектурное описание расходится с порядком фильтров и текущими лимитами, наличием circuit breaker и обогащением listing паспортом. Примеры анкеты в OpenAPI-аннотациях с SOME/REMOTE не соответствуют текущим enum BEGINNER/HOME. Описание ошибок не везде отражает ProblemDetails; устройство общих Testcontainers также изменилось.

**Где:** [README.md](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/README.md), [README.ru.md](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/README.ru.md), [docs/architecture.md](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/docs/architecture.md), [docs/architecture.ru.md](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/docs/architecture.ru.md), [QuestionnaireRequest.java](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/matching-service/src/main/java/ru/hvostid/matching/dto/QuestionnaireRequest.java#L46), [gradle/libs.versions.toml](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/gradle/libs.versions.toml), [frontend/package.json](https://github.com/hvostid/hvostid/blob/2d942a301cb0bd109ec0b48785a8dc7d0b660a2b/frontend/package.json).

**Что сделать / готовность:** синхронизировать обе языковые версии и примеры с текущими контрактами; исполнять примеры запросов/quick start в CI, где это практично. Новый разработчик должен запускать стек и проходить примеры без ручного исправления команд и enum.

## Что проверено исполнением

| Проверка | Результат и предел доказательства |
| --- | --- |
| 2 gateway-проверки | Поддельные identity-заголовки проходят optional-auth; замена X-Forwarded-For обходит лимит. Это тесты настоящих фильтров, не полный сетевой gateway → service сценарий. |
| 2 auth-проверки с PostgreSQL | Удаляется сессия с живым refresh; пароль 73 ASCII, разрешённый DTO, отвергается BCrypt с HTTP 400. |
| 2 listing-проверки с PostgreSQL | Не найден активный passport alias 00042; опубликован несуществующий passport-ID, изменён вид после публикации, переданный в update новый passport-ID проигнорирован. |
| Frontend refresh harness | Два refresh, один fulfilled и один rejected, после обоих storage пуст и redirect=/login?redirect=%2Fprofile. Загружается реальный client.js, HTTP/storage имитируются. |
| npm lint | 0 ошибок, 10 предупреждений. |
| npm audit | 11 записей: 8 high / 2 moderate / 1 low. Применимость каждой advisory отдельно не доказана. |
| Проверка registry | minio/minio:latest и minio/mc:latest недоступны из текущего окружения. Полный Compose не поднимался. |
| GitHub PR #185 | OPEN, REVIEW_REQUIRED, 11 успешных checks на проверенном HEAD. |

**Шесть дополнительных Java-тестов успешны потому, что проверяют наличие текущих дефектов. Это characterization probes, а не доказательство исправления.** Полный штатный backend-набор из 532 тестов был успешно выполнен на этом же HEAD в предыдущем этапе работы над PR; в рамках аудита дополнительно выполнялись перечисленные точечные проверки. Браузерные сценарии и полноценный production pentest не выполнялись.

Локальные материалы:

- [Gradle init-script для изолированных проверок] (локальный артефакт аудита, не включён в Git)
- [Gateway probes] (локальный артефакт аудита, не включён в Git)
- [Auth probes] (локальный артефакт аудита, не включён в Git)
- [Listing probes] (локальный артефакт аудита, не включён в Git)
- [Воспроизведение гонки refresh] (локальный артефакт аудита, не включён в Git)
- [Первый Gradle-прогон] (локальный артефакт аудита, не включён в Git), [Дополнительный Gradle-прогон] (локальный артефакт аудита, не включён в Git)
- [Полный npm audit] (локальный артефакт аудита, не включён в Git), [Полный lint] (локальный артефакт аудита, не включён в Git)

Материалы в tmp игнорируются Git и остаются локальными. Для повторения Java-проверок из корня репозитория:

```powershell
$env:GRADLE_USER_HOME = 'C:\Users\alex\.gradle'
.\gradlew.bat -I tmp/repository-audit/audit.init.gradle :api-gateway:test --tests '*AuditGatewayEvidenceTest' :auth-service:test --tests '*AuditAuthEvidenceTest' :listing-service:test --tests '*AuditListingEvidenceTest'
node tmp/repository-audit/frontend-refresh.cjs
```

## Предлагаемая последовательность работ

1. **Граница доверия gateway:** A01 + A03, сквозные проверки подделанных заголовков.
2. **Сессии:** A04 + A08 + A09, затем A10; тесты времени и параллельных запросов.
3. **Связь объявления с паспортом и модерация:** A05 + A06 + A07 + A11 + A12 + A24. Эти изменения согласовать по общей модели ID/статусов, чтобы не исправлять каждый обход отдельно.
4. **Запуск и поставка:** A02 + A13 + A31 + A32 + A33. Проверять чистый Compose и реальные запросы через nginx.
5. **Сохранение паспортных данных:** A14 + A15 + A16 + A17.
6. **Корректность matching:** A18 + A19 + A20 + A22, затем измерения A21.
7. **Сценарии frontend:** A23 + A25–A30; отдельно определить минимальный контакт с продавцом A34.
8. **Эксплуатация и сопровождение:** A35–A42 с приоритетом восстановления данных и обработки сессий перед внешним запуском.

В ходе этого аудита production-код не изменялся, новые GitHub issues не публиковались и существующий PR не обновлялся. Добавлен этот локальный отчёт; воспроизведения сохранены отдельно в tmp.
