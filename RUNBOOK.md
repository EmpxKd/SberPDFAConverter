# Как запустить и протестировать pdfa-converter

## 1. Собрать (если jar ещё не собран)

```bash
cd /home/aze/StprojectPDF/untitled
mvn -B clean package -DskipTests
```
Результат: `target/pdfa-converter.jar` (Spring Boot repackaged jar). Нужен JDK 17+.

## 2. Запустить сервер

```bash
java -jar /home/aze/StprojectPDF/untitled/target/pdfa-converter.jar
```
Порт по умолчанию — `8080` (`server.port` в `application.yml`, переопределяется `SERVER_PORT`).
Проверить, что живой:
```bash
curl -s http://localhost:8080/healthz
```
Ответ вида `{"status":"ok"}`. Бонус
Spring Boot Actuator: `curl -s http://localhost:8080/actuator/health` (общий health-чек фреймворка,
не подменяет `/healthz` — это старый контракт, его уже знают клиенты/Postman-коллекции).

## 3. Загрузить свой файл и получить PDF/A

Эндпойнт: `POST /api/v1/convert/scan`, `multipart/form-data`.

```bash
curl -X POST http://localhost:8080/api/v1/convert/scan \
  -F "page=@/путь/к/твоему/файлу.png" \
  -F "flavour=1B" \
  -F "title=Мой документ" \
  -F "author=Я" \
  -F "meta=НомерДела=12345" \
  -F "meta=Отдел=Бухгалтерия" \
  -o result.pdf
```

Поля формы:
- `page` - **обязательное**, сам файл (PNG/JPEG/TIFF/BMP/PDF). Можно несколько раз `-F "page=@..."` -
  многостраничный документ, порядок сохраняется.
- `flavour` - `1A` / `1B` (PDF/A-1a/1b). По умолчанию `1B`.
- `title`, `author`, `subject`, `sourceSystem`, `documentType`, `documentDate` (формат `YYYY-MM-DD`) -
  опциональные текстовые поля метаданных, пишутся синхронно в docinfo и XMP.
- `meta` - **повторяемое**, произвольное свойство заказчика в формате `Ключ=Значение`
  (например `-F "meta=НомерДела=12345"`). Пишется в PDF/A extension schema (namespace `cust`) +
  дублируется в `/Info`. Имя ключа не может совпадать со стандартным полем метаданных
  (title/author/subject/keywords/creator/producer/creationdate/moddate) — такой запрос отклоняется
  с 400.
- `signature` - файл подписи **не поддерживается**: целевой формат PDF/A-1 запрещает вложенные
  файлы, запрос с этим полем отклоняется с 400.

Результат - готовый PDF/A в `result.pdf` (бинарный ответ `application/pdf`), либо JSON с ошибкой при
сбое (400/422/500).

## 4. Проверить результат через veraPDF

```bash
curl -X POST "http://localhost:8080/api/v1/validate?flavour=1B" \
  -H "Content-Type: application/octet-stream" \
  --data-binary @result.pdf
```
Тело запроса - **сырые байты PDF целиком** (не form-data!), флейвор - query-параметр (`1A`/`1B`).
Ответ: `{"compliant":true/false, "flavour":..., "totalAssertions":..., "failedAssertions":..., "failures":[...]}`.

## 5. Postman - готовые запросы

### Запрос 1: Конвертация (`POST /api/v1/convert/scan`)

- Method: `POST`
- URL: `http://localhost:8080/api/v1/convert/scan`
- Body → `form-data`:

| Key | Type | Value |
|---|---|---|
| `page` | **File** | выбрать свой файл (PNG/JPEG/TIFF/BMP/PDF) |
| `flavour` | Text | `1B` (или `1A`) |
| `title` | Text | например `Мой документ` |
| `author` | Text | например `Я` |
| `subject` | Text | опционально |
| `sourceSystem` | Text | опционально |
| `documentType` | Text | опционально |
| `documentDate` | Text | опционально, формат `YYYY-MM-DD` |
| `meta` | Text | `Ключ=Значение` — можно добавить несколько строк с таким же key `meta` |

Несколько страниц - добавь ещё одну строку с key `page` (тип File), Postman разрешает повторяющиеся
ключи в form-data, порядок сохраняется.

Ответ - бинарный PDF (`Content-Type: application/pdf`). В Postman: кнопка "Send and Download" (или
"Save Response" → "Save to a file") рядом с Send, иначе тело будет нечитаемо как текст.

### Запрос 2: Валидация (`POST /api/v1/validate`)

- Method: `POST`
- URL: `http://localhost:8080/api/v1/validate?flavour=1B`
- Body → **`binary`** (НЕ form-data!) → выбрать PDF-файл целиком как тело запроса.

Ответ - JSON: `{"compliant":true/false,"flavour":...,"totalAssertions":...,"failedAssertions":...,"failures":[...]}`.

### Запрос 3: Health-check (`GET /healthz`)

- Method: `GET`
- URL: `http://localhost:8080/healthz`
- без тела.

Ответ: `{"status":"ok"}`.

## 6. Батч-конвертация многих документов

Эндпойнт приёма: `POST /api/v1/convert/batch`, `multipart/form-data` — повторяемые файловые части
`page` + параллельный повторяемый текстовый параметр `docId` (i-й файл ↔ i-й `docId`: страницы с
одинаковым `docId` собираются в один документ). Ответ — `202 Accepted` сразу, без ожидания
конвертации; обработка идёт в фоне пулом воркеров с ограниченной очередью.

```bash
curl -X POST http://localhost:8080/api/v1/convert/batch \
  -F "page=@/путь/scan_A_1.png" -F "docId=A" \
  -F "page=@/путь/scan_A_2.png" -F "docId=A" \
  -F "page=@/путь/scan_B_1.png" -F "docId=B" \
  -F "author=Я" -F "meta=НомерДела=12345"
```
Ответ: `{"jobId":"...", "documents":2, "statusUrl":"/api/v1/convert/batch/<jobId>"}`. Документ `A` —
2 страницы (один многостраничный PDF), документ `B` — 1 страница; `title` каждого документа =
его `docId` (общие метаданные применяются к обоим одинаково, `title` отдельно не передаётся).

Опрос статуса:
```bash
curl -s http://localhost:8080/api/v1/convert/batch/<jobId>
```
Ответ: `{"jobId":..., "jobStatus":"RUNNING"/"DONE", "total":2, "pending":0, "running":1, "done":1,
"failed":0, "documents":[{"docId":"A","status":"DONE","compliant":true,"error":null}, ...]}`.

Скачивание результата:
```bash
# Все готовые PDF + report.json одним архивом (можно скачивать повторно по мере готовности —
# незавершённый батч отдаёт то, что уже готово, а jobStatus честно показывает RUNNING):
curl -s http://localhost:8080/api/v1/convert/batch/<jobId>/result -o batch.zip

# Один документ по его docId:
curl -s http://localhost:8080/api/v1/convert/batch/<jobId>/doc/A -o A.pdf
```
Неизвестный `jobId`/`docId` → `404`; запрошен документ, который ещё не `DONE` (или упал) → `409` с
причиной в теле ответа.

**Backpressure.** Если очередь воркеров (`pdfa.batch.queue-capacity`, дефолт 1000) не может вместить
весь батч целиком — сервер отвечает `429 Too Many Requests` с заголовком `Retry-After: 5`; батч не
регистрируется частично, повторите запрос позже.

**Изоляция сбоев.** Один битый файл валит только свой документ (`status:"FAILED"`, `error` с
причиной) — остальные документы батча обрабатываются и попадают в ZIP/report.json как обычно.

**Конфигурация** (`application.yml`, секция `pdfa.batch`): `workers` (дефолт — число ядер CPU),
`queue-capacity` (дефолт 1000), `result-dir` (дефолт `${java.io.tmpdir}/pdfa-batch`),
`result-ttl-hours` (дефолт 24 — TTL-уборка результатов с диска), `delete-on-download` (дефолт
`false` — удалить каталог батча сразу после скачивания ZIP завершённого батча).

**Память/диск.** Готовые PDF лежат на диске (`pdfa.batch.result-dir`), не в памяти процесса —
следите за свободным местом на этом разделе при батчах на тысячи документов. Рекомендуемый `-Xmx`
для батч-нагрузки: не меньше `-Xmx1g`, точная цифра зависит от размера и параллелизма исходных
файлов (`pdfa.batch.workers` одновременно декодируют по одному файлу каждый).

**Реальный лимит на приём — `max-request-size`, не `queue-capacity`.** Spring MVC читает все части
multipart-запроса (`page=@...`) в память контроллера целиком и только потом — внутри
`BatchJobService.submit` — проверяется свободное место в очереди воркеров. Для батча из тысяч
файлов это разовый синхронный всплеск памяти на HTTP-потоке ещё до какой-либо проверки
`pdfa.batch.queue-capacity` — `queue-capacity` гасит перегруз *задач* в очереди, а не перегруз
*приёма* одного запроса. Фактический предохранитель на размер одного батч-запроса —
`spring.servlet.multipart.max-request-size`/`server.tomcat.max-http-form-post-size`
(`application.yml`, дефолт 2GB) — для очень крупных батчей режьте его осознанно под доступную
память сервера.

**TTL-уборка во время скачивания.** `ResultCleanupTask` (фоновый поток, раз в час) и скачивание
ZIP/PDF не синхронизированы между собой. Если TTL батча истекает ровно во время чтения файлов на
диске, `ResultStore.deleteJob` может удалить их посреди отдачи — клиент получит обрыв соединения/
ошибку вместо штатного ответа. При дефолтном `result-ttl-hours: 24` вероятность пересечения низкая;
если в вашем деплое TTL короткий (минуты), учитывайте этот edge case при выборе значения.

**v1-ограничение.** Реестр заданий — in-memory (`ConcurrentHashMap`, не БД). Рестарт сервиса теряет
все незавершённые (`PENDING`/`RUNNING`) задания вместе с их статусом — уже сохранённые на диске PDF
завершённых документов остаются, но сам `jobId` из реестра пропадает. Не подходит для батчей, где
обязательна гарантия доживания до перезапуска — это явно отложено на будущую итерацию (durable-
очередь/БД), не реализовано в текущей версии.

## 7. Docker

```bash
docker build -t pdfa-converter .
docker run -p 8080:8080 pdfa-converter
```
Или через compose:
```bash
docker compose up --build
```
Сервис слушает `8080` снаружи контейнера так же, как локальный jar — все curl/Postman-примеры
выше работают без изменений, достаточно убедиться, что Docker Desktop проксирует порт `8080`
(по умолчанию проксирует).

## 8. Остановить сервер

```bash
pkill -f 'pdfa-converter.jar'
# или для контейнера:
docker stop $(docker ps -q --filter ancestor=pdfa-converter)
```
