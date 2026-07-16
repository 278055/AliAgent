package com.bn.aliagent.knowledge.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.InputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("database")
public final class MinioKnowledgeObjectStorage implements KnowledgeObjectStorage {
    private final MinioClient client;
    private final String bucket;

    public MinioKnowledgeObjectStorage(@Value("${knowledge.storage.endpoint}") String endpoint,
            @Value("${knowledge.storage.access-key}") String accessKey,
            @Value("${knowledge.storage.secret-key}") String secretKey,
            @Value("${knowledge.storage.bucket}") String bucket) {
        this.client = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
        this.bucket = bucket;
    }

    @Override
    public void put(String objectKey, InputStream input, long contentLength, String mediaType) throws Exception {
        ensureBucket();
        client.putObject(PutObjectArgs.builder().bucket(bucket).object(objectKey).stream(input, contentLength, -1)
                .contentType(mediaType).build());
    }

    @Override
    public InputStream get(String objectKey) throws Exception {
        return client.getObject(GetObjectArgs.builder().bucket(bucket).object(objectKey).build());
    }

    private void ensureBucket() throws Exception {
        if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }
}
