package org.texttechnologylab.services;

import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.jcas.JCas;
import org.apache.uima.util.CasIOUtils;
import org.apache.uima.util.CasLoadMode;
import org.texttechnologylab.config.CommonConfig;
import org.texttechnologylab.models.util.HealthStatus;
import org.texttechnologylab.utils.SystemStatus;
import io.minio.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@Service
public class S3Storage {
    private CommonConfig config;
    private MinioClient minioClient;

    public S3Storage() {
        try {
            this.config = new CommonConfig();
            this.minioClient = MinioClient.builder()
                    .endpoint(config.getMinioEndpoint())
                    .credentials(config.getMinioKey(), config.getMinioSecret())
                    .build();

            // Check and create bucket
            boolean bucketExists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(config.getMinioBucket()).build());
            if (!bucketExists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(config.getMinioBucket()).build());
            }

            SystemStatus.S3StorageStatus = new HealthStatus(true, "", null);
        } catch (Exception ex) {
            SystemStatus.S3StorageStatus = new HealthStatus(false, "Couldn't init the MinioS3UtilityService", ex);
        }
    }

    /**
     * Uploads an InputStream to MinIO.
     * Buffers the InputStream to determine its size for upload.
     *
     * @param inputStream The InputStream containing data (e.g., XMI)
     * @param objectName  The object name in MinIO (e.g., "document1.xmi")
     * @param metadata    Optional metadata for the object
     * @throws Exception If an error occurs during upload
     */
    public void uploadInputStream(InputStream inputStream, String objectName, Map<String, String> metadata)
            throws Exception {
        // Buffer InputStream content to memory
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            baos.write(buffer, 0, bytesRead);
        }
        byte[] data = baos.toByteArray();

        // Upload to MinIO
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data)) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(config.getMinioBucket())
                            .object(objectName)
                            .stream(bais, data.length, -1)
                            .contentType("application/xml")
                            .userMetadata(metadata != null ? metadata : new HashMap<>())
                            .build());
        }
    }

    /**
     * Downloads an object from MinIO as an InputStream.
     *
     * @param objectName The object name in MinIO
     * @return InputStream of the object's content
     * @throws Exception If an error occurs
     */
    public InputStream downloadObject(String objectName) throws Exception {
        return minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(config.getMinioBucket())
                        .object(objectName)
                        .build());
    }

    /**
     * Downloads an XMI object from MinIO and loads it into a JCas.
     *
     * @param objectName The object name in MinIO
     * @return JCas populated with the XMI data
     * @throws Exception If an error occurs during download or CAS loading
     */
    public JCas downloadAndLoadXmiToCas(String objectName) throws Exception {
        // Create a new JCas
        JCas jCas = JCasFactory.createJCas();

        // Download InputStream from MinIO
        try (InputStream inputStream = downloadObject(objectName)) {
            // Load XMI into JCas
            CasIOUtils.load(inputStream, null, jCas.getCas(), CasLoadMode.LENIENT);
        }

        return jCas;
    }

    /**
     * Deletes an object from MinIO.
     *
     * @param objectName The object name in MinIO
     * @throws Exception If an error occurs
     */
    public void deleteObject(String objectName) throws Exception {
        minioClient.removeObject(
                RemoveObjectArgs.builder()
                        .bucket(config.getMinioBucket())
                        .object(objectName)
                        .build());
    }
}