package com.itegra.auditcom.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itegra.auditcom.domain.NotaFiscalEntradaDTO;
import com.itegra.auditcom.service.QueueInputInterface;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.Result;
import io.minio.messages.Item;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
public class ProducerConfiguration {

    private final Logger log = LoggerFactory.getLogger(ProducerConfiguration.class);

    private static final String AUGMENTER = "notas-augmenter";

    private final QueueInputInterface queueInputInterface;

    public ProducerConfiguration(QueueInputInterface queueInputInterface) {
        this.queueInputInterface = queueInputInterface;
    }

    /**
     * https://docs.oracle.com/javase/8/docs/api/java/time/Duration.html?is-external=true#parse-java.lang.CharSequence-
     */
    @Scheduled(fixedDelayString = "PT15M")
    public void scheduleFixedDelayTask() {
        log.debug("Scheduled Fixed rate task - " + System.currentTimeMillis() / 1000);
        MinioClient minioClient = getBuild();
        Iterable<Result<Item>> results = minioClient.listObjects(ListObjectsArgs.builder().bucket(AUGMENTER).maxKeys(100).build());
        extractResults(results, minioClient);
    }

    private void extractResults(Iterable<Result<Item>> results, MinioClient minioClient) {
        for (Result<Item> result : results) {
            try {
                Item item = result.get();
                InputStream stream = minioClient.getObject(GetObjectArgs.builder().bucket(AUGMENTER).object(item.objectName()).build());
                byte[] buf = new byte[16384];
                int bytesRead;
                while ((bytesRead = stream.read(buf, 0, buf.length)) >= 0) {
                    String json = new String(buf, 0, bytesRead, StandardCharsets.UTF_8);
                    ObjectMapper mapper = new ObjectMapper();
                    NotaFiscalEntradaDTO notaFiscalEntradaDTO = mapper.readValue(json, NotaFiscalEntradaDTO.class);
                    queueInputInterface.addInput(notaFiscalEntradaDTO);
                }
            } catch (Exception exception) {
                exception.printStackTrace();
            }
        }
    }

    private MinioClient getBuild() {
        return MinioClient.builder().endpoint("http://127.0.0.1:9000").credentials("minioadmin", "minioadmin").build();
    }
}
