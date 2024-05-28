package crpt.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Deque;
import java.util.LinkedList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Класс для работы с API Честного знака
 * Поддерживает ограничение на количество запросов к API
 */
public class CrptApi {
    private final int requestLimit;
    private final long timeWindowMillis;
    private final Deque<Instant> requestTimes;
    private final Semaphore semaphore;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    /**
     * Конструктор класса CrptApi
     *
     * @param timeUnit     единица измерения времени для ограничения количества запросов
     * @param requestLimit максимальное количество запросов в указанное время
     */
    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.requestLimit = requestLimit;
        this.timeWindowMillis = timeUnit.toMillis(1);
        this.requestTimes = new LinkedList<>();
        this.semaphore = new Semaphore(requestLimit);
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Создать документ
     * Метод синхронизирован для thread-safety
     *
     * @param document              объект документа
     * @param signature             подпись
     * @throws InterruptedException прерывание потока
     * @throws IOException          исключение ввода-вывода
     */
    public synchronized void createDocument(Document document, String signature) throws InterruptedException, IOException {
        semaphore.acquire();
        Instant now = Instant.now();

        synchronized (requestTimes) {
            // Удалить все запросы, которые вышли за пределы временного окна (например, более минуты назад)
            while (!requestTimes.isEmpty() && now.minusMillis(timeWindowMillis).isAfter(requestTimes.peek())) {
                requestTimes.poll();
            }

            // Проверить текущий размер очереди лимит запросов
            if (requestTimes.size() < requestLimit) {
                // Если лимит не превышен, добавить текущий запрос в очередь
                requestTimes.add(now);
            } else {
                // Если лимит превышен, освободить захваченное разрешение семафора и выйти из метода
                semaphore.release();
                return;
            }
        }


        try {
            String requestBody = objectMapper.writeValueAsString(document);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://ismp.crpt.ru/api/v3/lk/documents/create"))
                    .header("Content-Type", "application/json")
                    .header("Signature", signature)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Failed to create document: " + response.body());
            }
        } finally {
            semaphore.release();
        }
    }

    @Data
    @NoArgsConstructor
    public static class Document {
        @JsonProperty("description")
        private Description description;
        @JsonProperty("doc_id")
        private String docId;
        @JsonProperty("doc_status")
        private String docStatus;
        @JsonProperty("doc_type")
        private String docType;
        @JsonProperty("import_request")
        private boolean importRequest;
        @JsonProperty("owner_inn")
        private String ownerInn;
        @JsonProperty("participant_inn")
        private String participantInn;
        @JsonProperty("producer_inn")
        private String producerInn;
        @JsonProperty("production_date")
        private String productionDate;
        @JsonProperty("production_type")
        private String productionType;
        @JsonProperty("products")
        private Product[] products;
        @JsonProperty("reg_date")
        private String regDate;
        @JsonProperty("reg_number")
        private String regNumber;
    }

    @Data
    @NoArgsConstructor
    public static class Description {
        @JsonProperty("participantInn")
        private String participantInn;
    }

    @Data
    @NoArgsConstructor
    public static class Product {
        @JsonProperty("certificate_document")
        private String certificateDocument;
        @JsonProperty("certificate_document_date")
        private String certificateDocumentDate;
        @JsonProperty("certificate_document_number")
        private String certificateDocumentNumber;
        @JsonProperty("owner_inn")
        private String ownerInn;
        @JsonProperty("producer_inn")
        private String producerInn;
        @JsonProperty("production_date")
        private String productionDate;
        @JsonProperty("tnved_code")
        private String tnvedCode;
        @JsonProperty("uit_code")
        private String uitCode;
        @JsonProperty("uitu_code")
        private String uituCode;
    }

    public static class Main {
        public static void main(String[] args) {
            // Создать экземпляр CrptApi с лимитом 10 запросов в минуту
            CrptApi crptApi = new CrptApi(TimeUnit.MINUTES, 10);

            // Создать пример документа
            Document document = new Document();
            Description description = new Description();
            description.setParticipantInn("1234567890");
            document.setDescription(description);
            document.setDocId("doc_id_example");
            document.setDocStatus("doc_status_example");
            document.setDocType("LP_INTRODUCE_GOODS");
            document.setImportRequest(true);
            document.setOwnerInn("owner_inn_example");
            document.setParticipantInn("participant_inn_example");
            document.setProducerInn("producer_inn_example");
            document.setProductionDate("2024-01-01");
            document.setProductionType("production_type_example");
            document.setRegDate("2024-01-01");
            document.setRegNumber("reg_number_example");

            Product product = new Product();
            product.setCertificateDocument("certificate_document_example");
            product.setCertificateDocumentDate("2024-01-01");
            product.setCertificateDocumentNumber("certificate_document_number_example");
            product.setOwnerInn("owner_inn_example");
            product.setProducerInn("producer_inn_example");
            product.setProductionDate("2024-01-01");
            product.setTnvedCode("tnved_code_example");
            product.setUitCode("uit_code_example");
            product.setUituCode("uitu_code_example");

            Product[] products = { product };
            document.setProducts(products);

            // Пример подписи
            String signature = "example_signature";

            try {
                // Отправить запрос на создание документа
                crptApi.createDocument(document, signature);
                System.out.println("Document created successfully");
            } catch (Exception e) {
                // Если статус код отличный от 200, то документ не создался
                e.printStackTrace();
            }
        }
    }
}

