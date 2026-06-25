# PDF/A Converter

Spring Boot 3 REST-сервис конвертации сканов документов (TIFF/JPEG/PNG/BMP/PDF) в PDF/A-1
(`1a`/`1b`) с произвольными метаданными заказчика и обязательной валидацией veraPDF.

Стек: Java 17, Spring Boot 3, Apache PDFBox, veraPDF, Maven.

## Сборка и запуск

```bash
mvn -B clean package -DskipTests
java -jar target/pdfa-converter.jar
```

Сервис слушает порт `8080` (`server.port` в `application.yml`). Или через Docker:

```bash
docker build -t pdfa-converter .
docker run -p 8080:8080 pdfa-converter
```

## REST API

- `POST /api/v1/convert/scan` — конвертация одного документа (multipart: `page`(s) + метаданные).
- `POST /api/v1/convert/batch` — батч-конвертация многих документов (async, `202 Accepted`),
  плюс `GET .../{jobId}` (статус), `GET .../{jobId}/result` (ZIP), `GET .../{jobId}/doc/{docId}`.
- `POST /api/v1/validate?flavour=1a|1b` — проверка готового PDF через veraPDF.
- `GET /healthz` — health-check.

Подробные примеры запросов (curl, Postman) — `RUNBOOK.md`. Технические требования к PDF/A-1 —
`PDFA1_REQUIREMENTS.md`.
