package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

public class CrptApi {
    private final OkHttpClient client;
    private final ObjectMapper objectMapper;
    private final Semaphore semaphore;
    private final ScheduledExecutorService scheduler;
    private final ReentrantLock lock = new ReentrantLock();

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.client = new OkHttpClient();
        this.objectMapper = new ObjectMapper();
        this.semaphore = new Semaphore(requestLimit);
        this.scheduler = Executors.newScheduledThreadPool(1);

        long interval = timeUnit.toMillis(1);
        this.scheduler.scheduleAtFixedRate(() -> {
            lock.lock();
            try {
                semaphore.release(requestLimit - semaphore.availablePermits());
            } finally {
                lock.unlock();
            }
        }, interval, interval, TimeUnit.MILLISECONDS);
    }

    public void createDocument(Document document, String signature) throws InterruptedException, IOException {
        semaphore.acquire();

        String json = objectMapper.writeValueAsString(document);

        RequestBody body = RequestBody.create(json, MediaType.get("application/json; charset=utf-8"));

        Request request = new Request.Builder()
                .url("https://ismp.crpt.ru/api/v3/lk/documents/create")
                .addHeader("Signature", signature)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }
            System.out.println(response.body().string());
        }
    }

    public void shutdown() {
        scheduler.shutdown();
    }

    public static class Document {
        public String doc_id;
        public String doc_status;
        public String doc_type;
        public String description;
        public String owner_inn;
        public String participant_inn;
        public String producer_inn;
        public String production_date;
        public String production_type;
        public String reg_date;
        public String reg_number;
        public boolean importRequest;
        public Product[] products;
    }

    public static class Product {
        public String certificate_document;
        public String certificate_document_date;
        public String certificate_document_number;
        public String owner_inn;
        public String producer_inn;
        public String production_date;
        public String tnved_code;
        public String uit_code;
        public String uitu_code;
    }

    public static void main(String[] args) throws InterruptedException, IOException {
        CrptApi api = new CrptApi(TimeUnit.MINUTES, 10);

        Document doc = new Document();
        doc.doc_id = "123";
        doc.doc_status = "NEW";
        doc.doc_type = "LP_INTRODUCE_GOODS";
        doc.description = "Sample document";
        doc.owner_inn = "1234567890";
        doc.participant_inn = "0987654321";
        doc.producer_inn = "1234567890";
        doc.production_date = "2023-01-01";
        doc.production_type = "TYPE";
        doc.reg_date = "2023-01-01";
        doc.reg_number = "REG123";
        doc.importRequest = true;
        doc.products = new Product[1];
        doc.products[0] = new Product();
        doc.products[0].certificate_document = "CERT123";
        doc.products[0].certificate_document_date = "2023-01-01";
        doc.products[0].certificate_document_number = "123456";
        doc.products[0].owner_inn = "1234567890";
        doc.products[0].producer_inn = "0987654321";
        doc.products[0].production_date = "2023-01-01";
        doc.products[0].tnved_code = "CODE123";
        doc.products[0].uit_code = "UIT123";
        doc.products[0].uitu_code = "UITU123";

        api.createDocument(doc, "signature_example");

        api.shutdown();
    }
}
