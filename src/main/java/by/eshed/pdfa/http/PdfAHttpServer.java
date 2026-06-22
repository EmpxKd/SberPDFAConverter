package by.eshed.pdfa.http;

import by.eshed.pdfa.ocr.OcrEngine;
import by.eshed.pdfa.pipeline.ScanToPdfAConverter;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * REST-обёртка на встроенном com.sun.net.httpserver - без Spring/Jetty: лишний фреймворк не
 * нужен для трёх маленьких эндпойнтов, а стек должен оставаться минимальным и чисто OSS
 * (DECISIONS.md, "Рекомендация: собственный сервис-конвертер").
 */
public final class PdfAHttpServer {

    private final HttpServer server;

    public PdfAHttpServer(int port, ScanToPdfAConverter converter, OcrEngine ocrEngine) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/v1/convert/scan", new ConvertScanHandler(converter));
        server.createContext("/api/v1/validate", new ValidateHandler());
        server.createContext("/healthz", new HealthHandler(ocrEngine));
        int threads = Math.max(2, Runtime.getRuntime().availableProcessors());
        server.setExecutor(Executors.newFixedThreadPool(threads));
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
    }

    public int port() {
        return server.getAddress().getPort();
    }
}
