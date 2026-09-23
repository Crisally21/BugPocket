# Bug Evidence — implementation backlog

Источники: [spec.md](spec.md), [plan.md](plan.md), корневой AGENTS.md.
Обновлено после решений заказчика. **T-01 — DONE**; T-02–T-12 — **TODO**, их реализация не начата.
На текущем шаге изменяются только эти три SDD-документа. Указанные ниже изменения кода, тестов, README и конфигурации относятся к будущим отдельно запускаемым задачам.

## Принятые решения и оставшиеся вопросы

- **OQ-01 закрыт:** добавление/удаление вложений не меняет Bug.updatedAt. Редактирование relatedTaskUrl использует существующую логику Bug.
- **OQ-02 закрыт для суммарного размера:** сначала пофайловая проверка, затем проверка суммы допустимых файлов batch. Если сумма превышает оставшуюся ёмкость, отклоняется весь batch до сохранения любого вложения. Недопустимые файлы не расходуют квоту; если допустимые вместе помещаются, действует partial success.
- **OQ-03 закрыт:** оригинальное имя хранится в metadata и используется при скачивании; физический путь определяется независимым безопасным storageKey.

Бизнес-вопросы из spec §9:

| Вопрос | Что ещё требуется решить | Зависимые части |
| --- | --- | --- |
| Q-04 | 25 MB — 25 000 000 или 26 214 400 байт? | Границы размеров в T-05–07, UI-подсказки и приёмочные тесты. |
| Q-09 | Batch помещается по байтам, но не по числу свободных мест из 10: отклонить целиком или принять часть, и какую? | Count-overflow в T-06/07/09/10 и соответствующие тесты T-11. Не переносить на него правило размера без решения. |
| Q-06 | Допустимое видео не получило preview из-за сбоя генерации: отклонить файл или сохранить с явным состоянием ошибки/повтором? | Обработка такого сбоя в T-05–09 и интеграционные тесты. |

Эти вопросы не блокируют всю работу. Не закрывать зависимый сценарий произвольным поведением.
URI parser, storageKey, механизм блокировки, DTO и способ multipart-обработки выбираются технически в основных задачах. Бывшие Q-05/Q-07/Q-08 не сохраняются как вопросы заказчику. Для PUT сохраняется существующая замена редактируемых полей.

Наличие рабочего видеодекодера — техническая проверка T-05. В предыдущем анализе ffmpeg/ffprobe не обнаружены в PATH; доступность runtime надо проверить, а не заменять требуемое превью иконкой.

## Архитектурные ориентиры

Java 17 / Spring Boot 4.1.1, MVC → service → JPA, PostgreSQL, Liquibase; тесты MockMvc/H2. Фронтенд — статика с клиентским рендерингом, без framework. Новые changeSets добавляются после неизменённого 1-create-bug. BugResponse формируется явно; Bug.@PreUpdate управляет updatedAt. Список сортируется по createdAt DESC/id DESC.

Attachment API отделён от JSON POST/PUT bug. Существующий api() в app.js требует поддержки FormData и пустых ответов; выбранные File не должны попадать в JSON DTO. Изоляция файловых тестов обязательна; порядок очистки FK в BugApiTests нужно обновить. Публичные metadata не раскрывают storageKey/пути.

## Задачи

### T-01 — Related Task URL: backend и БД

- **Статус:** DONE (2026-09-23). Реализованы backend/БД и тесты T-01. Команда: `JAVA_HOME=C:\Program Files\Java\jdk-17` (переменная PowerShell), затем `.\mvnw.cmd -B -ntp test`: 34 теста, 0 ошибок, 0 пропусков; включая BugApiTests, contextLoads, URL API и upgrade Liquibase на H2. T-02 не начата.

- **Цель:** добавить одно необязательное поле URL через существующие JSON create/read/update операции.
- **Требования:** FR-12–17, FR-18 в части URL / AC-14–19, AC-23.
- **Компоненты/файлы:** Bug, BugRequest, BugResponse.from, BugService.apply, BugController, ApiExceptionHandler; новый Liquibase changeSet в db/changelog; URL normalizer/validator; BugApiTests.
- **Критерий завершения:** nullable related_task_url хранится и возвращается; HTTP/HTTPS сохраняются, адрес без схемы получает HTTPS, недопустимое значение даёт 400 с errors.relatedTaskUrl. Отсутствие/null/blank поддерживают очистку и существующую PUT-семантику. Старый JSON работает, status при PUT сохраняется; URL-редактирование использует обычный @PreUpdate. Нет запросов к внешнему трекеру и случайного бизнес-лимита из varchar(255).
- **Тесты:** omitted/null/blank, HTTP/HTTPS/без схемы, invalid scheme/URL, установка после создания, замена/очистка, cardinality, timestamps; миграция/contextLoads и старые BugApiTests.
- **Зависимости:** нет.

### T-02 — Related Task URL: frontend и тесты

- **Цель:** дать пользователю редактировать ссылку и переходить по ней из карточки.
- **Требования:** FR-12–17 / AC-14–19.
- **Компоненты/файлы:** app.js, ui.js, styles.css при необходимости; src/test/frontend/app.test.mjs и ui.test.mjs.
- **Критерий завершения:** поле есть в create/edit; нормализованный URL отображается безопасной ссылкой; очистка и серверные field errors работают; URL учитывается в dirty. Существующие поля и правила навигации сохранены.
- **Тесты:** DOM create/edit/clear/link/validation/escaping, несохранённые изменения; npm.cmd run check:ui и frontend tests.
- **Зависимости:** T-01.

### T-03 — BugAttachment: migration, entity, repository

- **Цель:** добавить metadata вложений с обязательным оригинальным именем и привязкой к bug.
- **Требования:** FR-02, FR-04, FR-09–11, FR-18/19 / AC-07, AC-10–13, AC-22/24.
- **Компоненты/файлы:** новые BugAttachment, AttachmentRepository, mediaKind/fileType; новый Liquibase changeSet с FK; backend repository tests и очистка данных BugApiTests.
- **Критерий завершения:** хранятся bugId, тип, MIME, sizeBytes, originalFilename, storageKey, createdAt и при необходимости preview reference; bytes в БД не хранятся. Работают list/count/sum и поиск по bugId+attachmentId. Равные имена не конфликтуют; mapping не вызывает обновление parent Bug при attachment-мутации.
- **Тесты:** миграция/H2 mapping, FK, count/sum, пустой список, чужой bug, одинаковые originalFilename с разными ключами; прежние API-тесты и contextLoads.
- **Зависимости:** T-01 для последовательного добавления changeSets.

### T-04 — Файловое хранилище

- **Цель:** хранить и читать оригиналы/превью по безопасным внутренним ключам.
- **Требования:** FR-09–11, FR-19 / AC-10–13, AC-24.
- **Компоненты/файлы:** новые AttachmentStorage и filesystem implementation; application.properties/test configuration, .gitignore; storage tests.
- **Критерий завершения:** настраиваемый root вне static, store/read/delete; оригинальное имя никогда не становится путём. Нет выхода за root, перезаписи из-за одинаковых имён и раскрытия физических путей. Тесты используют собственные временные каталоги; локальные вложения исключены из Git.
- **Тесты:** byte round-trip, delete, missing file, ошибка записи, traversal/абсолютные пути и link escape где применимо; изоляция тестового root.
- **Зависимости:** T-03 задаёт metadata/key contract.

### T-05 — Проверка типов/размеров и video preview

- **Цель:** реализовать пофайловую проверку содержимого и реальный preview всех поддерживаемых форматов.
- **Требования:** FR-03, FR-05–07, FR-09 / AC-01–04, AC-08, AC-10/11.
- **Компоненты/файлы:** AttachmentValidator, VideoPreviewGenerator, pom.xml при необходимости, runtime configuration; fixtures и backend tests.
- **Критерий завершения:** фактический формат проверяется на сервере; PNG/JPG/JPEG и пять видеоконтейнеров поддержаны. Изображение масштабируется в UI, для видео доступен настоящий кадр. Проверен способ поставки runtime, ограничены время/ресурсы декодирования, очищаются временные файлы. Байтовая граница едина; нет молчаливого сужения перечня форматов.
- **Тесты:** реальные fixtures каждого типа, поддельный extension/MIME, невалидное содержимое; B−1/B/B+1 на валидном медиа; preview decode, отсутствие runtime/timeout/ошибка генерации.
- **Зависимости:** T-04. **Частично BLOCKED:** Q-04 для точной границы, Q-06 для результата preview failure; выбор библиотеки и проверка runtime — часть этой задачи.

### T-06 — AttachmentService: квоты, batch и согласованность

- **Цель:** объединить проверенные компоненты в загрузку и удаление с правилами batch/partial success.
- **Требования:** FR-01–08, FR-11, FR-18/19 / AC-01–09, AC-13, AC-20–22, AC-24.
- **Компоненты/файлы:** AttachmentService, AttachmentRepository, BugRepository для выбранной блокировки, storage/preview; batch result DTO и service tests.
- **Критерий завершения:** до сохранения вложений вычисляется сумма всех прошедших пофайловую проверку файлов; при existing + batch > B весь batch отклоняется с нулём сохранённых вложений. Равенство допустимо; при проходе общей проверки invalid файл не отменяет valid файлы. Квота защищена от конкурентных запросов на весь этап admission/persistence, а не проверяется после каждого уже сохранённого файла. Политика count-overflow соответствует Q-09. Компенсация учитывает write/flush/commit/delete и оригинал/preview; attachment-операции не меняют Bug.updatedAt и не затирают timestamp параллельного bug edit.
- **Тесты:** два файла по отдельности помещаются, вместе нет → ноль сохранений; valid+valid+exe помещаются → два успеха/одна ошибка; invalid bytes не расходуют квоту; ровно B; 10-й/11-й; одинаковые имена; ошибки storage/commit/delete; timestamps и конкурентные uploads на PostgreSQL. Проверять отсутствие постоянных файлов и metadata после quota rejection.
- **Зависимости:** T-03–05. **Частично BLOCKED:** Q-04/Q-09/Q-06 для соответствующих правил.

### T-07 — Attachment REST API и ошибки

- **Цель:** открыть операции вложений отдельными endpoints, сохранив обычные JSON операции bug.
- **Требования:** FR-01–11, FR-18/19 / AC-01–13, AC-20–22, AC-24.
- **Компоненты/файлы:** AttachmentController, DTO, ApiExceptionHandler, multipart configuration; MockMvc и real-HTTP tests.
- **Критерий завершения:** GET list, POST multipart, GET content/download, DELETE работают по plan §7. Content отдаёт изображение или кадр, download — bytes оригинала с оригинальным именем и безопасным Content-Disposition. Whole-batch rejection отличим от per-file errors и form errors; missing/wrong-owner attachment даёт 404. Multipart handling учитывает overhead и не ломает partial success из-за простого лимита 25 MB на request; storageKey не раскрывается.
- **Тесты:** все endpoints, 404/принадлежность, MIME/bytes/Unicode filename, одинаковые имена, ProblemDetail/partial result; реальный HTTP для B-byte файла с overhead, oversized+valid и aggregate overflow. JSON POST/PUT bug не стали multipart.
- **Зависимости:** T-06; соответствующие нерешённые вопросы распространяются на API-приёмку.

### T-08 — Отображение вложений существующего бага

- **Цель:** добавить секцию с изображениями, видеокадрами и типами файлов.
- **Требования:** FR-09 / AC-10/11.
- **Компоненты/файлы:** app.js, styles.css; index.html при необходимости; frontend tests.
- **Критерий завершения:** loading/empty/error/list состояния, небольшие настоящие preview и file type, адаптивная раскладка. Поздний ответ другого bug и перерисовка после PATCH не ломают секцию. Иконка не подменяет видеопревью.
- **Тесты:** jsdom image/video src и состояния, stale response, PATCH re-render, escaping; реальный браузер desktop/mobile для изображений и всех видеоконтейнеров.
- **Зависимости:** T-07; обработка preview-error зависит от Q-06.

### T-09 — Upload, delete и download для существующего бага

- **Цель:** закончить управление вложениями в карточке.
- **Требования:** FR-01–08, FR-10/11, FR-18/19 / AC-01–09, AC-12/13, AC-20–22, AC-24.
- **Компоненты/файлы:** app.js: api()/состояние файлов, ui.js, styles.css; frontend tests.
- **Критерий завершения:** FormData отправляет выбранный batch одной операцией, не дробит его для обхода правила. UI различает полный quota rejection и частичный успех, показывает ошибки конкретных файлов даже при совпадающих именах. Download использует endpoint оригинала; delete обновляет список и квоту только после успеха. Поддержаны пустые ответы, network errors, dirty/saving и защита от повторной отправки; attachment-only действия не меняют отображаемый updatedAt.
- **Тесты:** mixed result, whole-batch error с нулём новых карточек, exact limit/count behavior, одноимённые файлы, пустой DELETE, ошибка удаления, double-click, повтор после сбоя без дублирования известных успехов; download bytes/name в браузере.
- **Зависимости:** T-08; Q-09/Q-04/Q-06 должны быть закрыты для соответствующей приёмки.

### T-10 — Вложения при создании бага

- **Цель:** выполнить JSON create, затем upload, сохраняя созданный bug при отказе вложений.
- **Требования:** FR-01, FR-04, FR-07/08, FR-12/14, FR-18 / AC-01/02/09/14–16, AC-20–22.
- **Компоненты/файлы:** app.js: renderForm/readValues/dirty/saving и переход в карточку; frontend tests.
- **Критерий завершения:** выбранные File хранятся отдельно от JSON DTO и учитываются dirty. После успешного POST upload использует полученный ID. При whole-batch rejection bug остаётся без новых вложений; при partial success сохраняются успешные вложения; ошибки остаются видимыми после навигации. Повтор загрузки не создаёт второй bug, а загрузка не меняет его updatedAt.
- **Тесты:** порядок запросов, POST failure без upload, создание без файлов, mixed/whole-batch failure, сохранность текста/выбора до POST, dirty/saving на обоих шагах, retry с прежним ID; live-сценарий на временном сервере.
- **Зависимости:** T-02, T-09.

### T-11 — Integration и regression tests

- **Цель:** проверить совместную работу функции и сохранение старых API/UI.
- **Требования:** FR-01–19 / AC-01–24; plan §17/18.
- **Компоненты/файлы:** BugApiTests, CodextestApplicationTests, новые backend tests; app.test.mjs/ui.test.mjs и live-сценарии; изолированные PostgreSQL/storage fixtures.
- **Критерий завершения:** миграции работают на свежей БД и upgrade со старым 1-create-bug; старые записи сохранены. Конкурентные uploads/delete не нарушают размер/число и правило whole-batch admission. End-to-end create → preview → add → download → delete → URL edit/clear работает; attachment timestamps неизменны, URL timestamp обычный.
- **Тесты:** полный Maven test; check:ui/test:ui и live-тест с BUGPOCKET_TEST_URL; реальные HTTP multipart и PostgreSQL concurrency/upgrade. Регрессия GET list/detail, JSON POST 201/Location, PUT, PATCH status, 400/404/errors, filters/search, сортировка createdAt DESC/id DESC, dirty/saving, stale responses. Визуальная проверка и реальные downloads отдельно от jsdom.
- **Зависимости:** T-01–10; для полной приёмки решения Q-04/Q-09/Q-06 получены. Пропущенную инфраструктурную проверку не считать пройденной.

### T-12 — README, проверка конфигурации и финальная верификация

- **Цель:** подготовить понятный запуск и убедиться, что итог соответствует документации.
- **Требования:** FR-01–19 / AC-01–24; plan §5/10/14/17/18.
- **Компоненты/файлы:** README.md, application.properties/test configuration, .gitignore; spec/plan/tasks в части финальных решений и статусов. Основная конфигурация вводится в T-04/05/07, здесь проверяется её согласованность.
- **Критерий завершения:** описаны storage root, preview runtime, byte boundary, batch и filename/timestamp semantics, API errors, тестовая среда, recovery и сохранение файлов вместе с БД. Команды воспроизводимы; отсутствуют пользовательские uploads, preview, секреты и сборки в Git. Итоговый отчёт содержит фактические результаты проверки и ограничения.
- **Тесты:** запуск по README в изолированной среде; проверка storage/preview и test root, git diff/status/ignore; подтверждение результатов T-11 на итоговой версии. При изменении конфигурации повторить затронутые проверки.
- **Зависимости:** T-11. Реализация считается законченной только после финальной проверки; этот документ её не запускает.

## Порядок и границы

URL — независимый вертикальный срез. Metadata и storage предшествуют валидации и сервису; сервис определяет корректные квоты и batch admission до REST API и UI. Сначала заканчивается управление существующим bug, затем добавляется двухшаговое создание. Общая интеграция и регрессия завершаются проверкой запуска и документации.

Compensation, multipart handling и concurrency включены в T-06/07/11, а не вынесены в отдельные этапы. У каждой задачи есть локальные тесты: откладывать всю проверку до T-11 нельзя. Зависимости задают безопасный порядок; нерешённый вопрос блокирует только затронутую часть. Для начала любой реализации нужна отдельная команда.
