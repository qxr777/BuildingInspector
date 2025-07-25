package edu.whut.cs.bi.biz.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.errors.*;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

@Configuration
public class MinioConfig {

  @Value("${minio.endpoint}")
  private String endpoint;

  @Value("${minio.access-key}")
  private String accessKey;

  @Value("${minio.secret-key}")
  private String secretKey;

  @Value("${minio.bucket-name}")
  private String bucketName;

  @Value("${minio.url}")
  private String url;

  @Bean
  public MinioClient minioClient()
      throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException,
      InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {

    // 自定义OkHttpClient，增加连接池大小
    OkHttpClient customHttpClient = new OkHttpClient.Builder()
            .connectionPool(new ConnectionPool(
                    20,         // 最大空闲连接数
                    10,         // 连接保持时间（分钟）
                    TimeUnit.MINUTES
            ))
            .connectTimeout(30, TimeUnit.SECONDS)     // 连接超时
            .readTimeout(5, TimeUnit.MINUTES)        // 读取超时
            .writeTimeout(60, TimeUnit.MINUTES)       // 写入超时
            .build();

    MinioClient build = MinioClient.builder()
            .endpoint(endpoint)
            .credentials(accessKey, secretKey)
            .region("cn-north-1")
            .httpClient(customHttpClient)  // 使用自定义的HttpClient
            .build();

    boolean isExist = build.bucketExists(
            BucketExistsArgs.builder().bucket(bucketName).build());
    if (!isExist) {
      build.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
    }
    return build;
  }

  public String getUrl() {
    return url;
  }

  public void setEndpoint(String endpoint) {
    this.endpoint = endpoint;
  }

  public String getAccessKey() {
    return accessKey;
  }

  public void setAccessKey(String accessKey) {
    this.accessKey = accessKey;
  }

  public String getSecretKey() {
    return secretKey;
  }

  public void setSecretKey(String secretKey) {
    this.secretKey = secretKey;
  }

  public String getBucketName() {
    return bucketName;
  }

  public void setBucketName(String bucketName) {
    this.bucketName = bucketName;
  }
}