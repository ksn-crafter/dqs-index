/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.lucene.queryparser.dqsIndexGeneration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

public class S3Adapter {

  public List<String> getAllFileKeys(String bucketName, String folderPrefix) {

    List<String> filePaths = new ArrayList<>();
    String continuationToken = null;

    try (S3Client s3Client = createS3Client()) {
      do {
        // Build the request
        ListObjectsV2Request.Builder requestBuilder =
            ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(folderPrefix)
                .maxKeys(1000); // Maximum allowed per request

        // Add continuation token if we have one (for pagination)
        if (continuationToken != null) {
          requestBuilder.continuationToken(continuationToken);
        }

        ListObjectsV2Request request = requestBuilder.build();

        try {
          // Execute the request
          ListObjectsV2Response response = s3Client.listObjectsV2(request);

          // Extract file paths from the response
          for (S3Object s3Object : response.contents()) {
            String key = s3Object.key();

            // Skip if it's a folder (ends with /)
            if (!key.endsWith("/")) {
              filePaths.add(key);
            }
          }

          // Get continuation token for next batch
          continuationToken = response.nextContinuationToken();

          //          System.out.println(
          //              "Fetched "
          //                  + response.contents().size()
          //                  + " objects. Total so far: "
          //                  + filePaths.size());

        } catch (Exception e) {
          throw e;
        }

      } while (continuationToken != null);
    }

    return filePaths;
  }

  public ResponseInputStream<GetObjectResponse> download(String bucketName, String s3Key) {
    GetObjectRequest getObjectRequest =
        GetObjectRequest.builder().bucket(bucketName).key(s3Key).build();
    try (S3Client s3Client = createS3Client()) {
      return s3Client.getObject(getObjectRequest);
    } catch (Exception e) {
      //      System.out.println("Error downloading file from S3: " + e.getMessage());
      throw e;
    }
  }

  private S3Client createS3Client() {
    String accessKey = System.getenv("AWS_ACCESS_KEY");
    String secretKey = System.getenv("AWS_SECRET_KEY");

    return S3Client.builder()
        .region(Region.US_EAST_1)
        .credentialsProvider(
            StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
        .build();
  }

  public void uploadToS3(Path directoryPath, String s3Prefix) throws IOException {
    try (Stream<Path> paths = Files.walk(directoryPath)) {
      paths
          .filter(Files::isRegularFile)
          .filter(filePath -> filePath.getFileName().toString().startsWith("split"))
          .forEach(
              filePath -> {
                try {
                  uploadFile(directoryPath, filePath, "dqs-poc-indexes", s3Prefix);
                } catch (Exception e) {
                  throw new RuntimeException(e);
                  // System.err.println("Failed to upload " + filePath + ": " + e.getMessage());
                  //                  e.printStackTrace();
                }
              });
    }
  }

  private void uploadFile(Path sourceDir, Path filePath, String bucketName, String s3Prefix)
      throws IOException {
    // Calculate relative path from source directory
    Path relativePath = sourceDir.relativize(filePath);

    // Convert to S3 key (use forward slashes)
    String s3Key = s3Prefix + relativePath.toString().replace("\\", "/");

    try (S3Client s3Client = createS3Client()) {
      // Detect content type - use binary for Lucene index files

      String contentType = "application/octet-stream";

      // Create put request
      PutObjectRequest putRequest =
          PutObjectRequest.builder().bucket(bucketName).key(s3Key).contentType(contentType).build();

      // Upload file
      s3Client.putObject(putRequest, RequestBody.fromFile(filePath));
      // System.out.println("Uploaded: " + filePath + " -> s3://" + bucketName + "/" + s3Key);

    } catch (S3Exception e) {
      throw new RuntimeException("Failed to upload file to S3: " + e.getMessage(), e);
    }
  }
}
