# Как запустить и протестировать pdfa-converter

## 1. Собрать (если jar ещё не собран)

```bash
cd /home/aze/StprojectPDF/untitled
mvn -B clean package -DskipTests
```
Результат: `target/pdfa-converter.jar`.

## 2. Запустить сервер

```bash
java -jar /home/aze/StprojectPDF/untitled/target/pdfa-converter.jar serve 8080
```
Проверить, что живой:
```bash
curl -s http://localhost:8080/healthz
```
Ответ вида `{"status":"ok","tesseractAvailable":false}` - нормально (см. п.5 про OCR).

## 3. Загрузить свой файл и получить PDF/A

Эндпойнт: `POST /api/v1/convert/scan`, `multipart/form-data`.

```bash
curl -X POST http://localhost:8080/api/v1/convert/scan \
  -F "page=@/путь/к/твоему/файлу.png" \
  -F "flavour=1B" \
  -F "ocrEnabled=false" \
  -F "title=Мой документ" \
  -F "author=Я" \
  -o result.pdf
```

Поля формы:
- `page` - **обязательное**, сам файл (PNG/JPEG/TIFF/BMP/PDF). Можно несколько раз `-F "page=@..."` -
  многостраничный документ, порядок сохраняется.
- `flavour` - `1B` / `2B` / `3B` (PDF/A-1b/2b/3b). По умолчанию `2B`.
- `ocrEnabled` - `false` обязательно в этом окружении (см. п.5, реальный Tesseract не работает).
- `title`, `author`, `subject`, `sourceSystem`, `documentType`, `documentDate` (формат `YYYY-MM-DD`) -
  опциональные текстовые поля метаданных, пишутся синхронно в docinfo и XMP.
- `signature` - опциональный файл подписи (`-F "signature=@sig.bin"`) - поднимает профиль до PDF/A-3b
  автоматически.

Результат - готовый PDF/A в `result.pdf` (бинарный ответ `application/pdf`), либо JSON с ошибкой при
сбое (400/500).

## 4. Проверить результат через veraPDF

```bash
java -jar /home/aze/StprojectPDF/untitled/target/pdfa-converter.jar validate result.pdf PDF_A_1B
```
Ответ: `{"compliant":true/false, "flavour":..., "totalAssertions":..., "failedAssertions":..., "failures":[...]}`.

Тот же эндпойнт доступен и по REST: `POST /api/v1/validate?flavour=1B` - тело запроса **сырые байты
PDF целиком** (не form-data!), флейвор - query-параметр.

## 5. Postman - готовые запросы

### Запрос 1: Конвертация (`POST /api/v1/convert/scan`)

- Method: `POST`
- URL: `http://localhost:8080/api/v1/convert/scan`
- Body → `form-data`:

| Key | Type | Value |
|---|---|---|
| `page` | **File** | выбрать свой файл (PNG/JPEG/TIFF/BMP/PDF) |
| `flavour` | Text | `1B` (или `2B`/`3B`) |
| `ocrEnabled` | Text | `false` (обязательно в этом окружении, см. п.6) |
| `title` | Text | например `Мой документ` |
| `author` | Text | например `Я` |
| `subject` | Text | опционально |
| `sourceSystem` | Text | опционально |
| `documentType` | Text | опционально |
| `documentDate` | Text | опционально, формат `YYYY-MM-DD` |
| `signature` | **File** | опционально - отдельный файл подписи, поднимает профиль до PDF/A-3b |

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

Ответ: `{"status":"ok","tesseractAvailable":false}`.

## 6. Про OCR (реальный Tesseract сейчас НЕ работает в этом окружении)

`tesseract-ocr` поставлен (`apt install tesseract-ocr tesseract-ocr-rus tesseract-ocr-eng`), но вызов
из Java падает: Ubuntu 24.04 даёт `liblept.so.5` версии 1.82.0, а Java-биндинг `lept4j 1.24.0`
(последняя версия, новее нет) жёстко требует функцию `pixBackgroundNormTo1MinMax`, которой в этой
версии leptonica уже нет - версионная несовместимость пакетов, не баг проекта. Поэтому:
- **Всегда передавай `ocrEnabled=false`** при загрузке через REST, иначе конвертация упадёт с
  ошибкой "Tesseract OCR недоступен" (так и должно быть - DECISIONS.md требует OCR всегда, флаг
  это admin override только для сред без рабочего Tesseract).
- Без OCR невидимый текстовый слой не пишется, но PDF/A валидным быть обязан - так и есть, проверено.

## 7. CLI без сервера (тот же результат, без curl/Postman)

```bash
java -jar /home/aze/StprojectPDF/untitled/target/pdfa-converter.jar convert result.pdf /путь/к/файлу.png 1B
```
(CLI-режим `convert` не принимает `ocrEnabled`/метаданные - всегда пытается включить OCR; в этом
окружении упадёт с той же ошибкой Tesseract, см. п.6. Для тестов без OCR используй REST-путь п.3-5,
либо `mvn test`, где тесты сами передают `ocrEnabled=false`.)

## 8. Остановить сервер

```bash
pkill -f 'pdfa-converter.jar serve'
```