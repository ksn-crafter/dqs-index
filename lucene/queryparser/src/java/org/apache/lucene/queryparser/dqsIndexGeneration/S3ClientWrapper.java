package org.apache.lucene.queryparser.dqsIndexGeneration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;
import java.util.ArrayList;
import java.util.List;

public class S3ClientWrapper {

  public List<String> getAllFileKeys(String bucketName, String folderPrefix) {

    List<String> filePaths = new ArrayList<>();
    String continuationToken = null;

    try (S3Client s3Client = createS3Client()) {
      do {
        // Build the request
        ListObjectsV2Request.Builder requestBuilder = ListObjectsV2Request.builder()
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

          System.out.println("Fetched " + response.contents().size() +
              " objects. Total so far: " + filePaths.size());

        } catch (Exception e) {
          System.err.println("Error fetching objects from S3: " + e.getMessage());
          throw new RuntimeException("Failed to fetch S3 objects", e);
        }

      } while (continuationToken != null);
    } catch (Exception e) {
      System.out.println("Error while fetching files from s3: "  + e.getMessage());
    }// Continue if there are more results

    return filePaths;
  }

  public ResponseInputStream<GetObjectResponse> download(String s3Key) {
    GetObjectRequest getObjectRequest = GetObjectRequest.builder().bucket("dqs-poc-data").key(s3Key).build();
    try (S3Client s3Client = createS3Client()) {
      return s3Client.getObject(getObjectRequest);
    } catch (Exception e) {
      System.out.println("Error downloading file from S3: " + e.getMessage());
    }

    return null;
  }

  private S3Client createS3Client() {
    String accessKey = System.getenv("AWS_ACCESS_KEY");
    String secretKey = System.getenv("AWS_SECRET_KEY");

    return S3Client.builder()
        .region(Region.US_EAST_1)
        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
        .build();
  }


}
