package org.apache.lucene.queryparser.dqsIndexGeneration;

import java.io.IOException;
import java.util.List;

public class Main {
  public static void main(String[] args) throws IOException {
    writeIndex();
  }

  public static void writeIndex() throws IOException {
    String bucketName = "dqs-poc-data";
    String prefix = "128MB-chunks/Wiki/";
    S3Adapter s3Adapter = new S3Adapter();

    List<String> s3Keys = s3Adapter.getAllFileKeys(bucketName, prefix);
    DqsIndexGenerator dqsIndexGenerator = new DqsIndexGenerator();
    //TODO: implement back pressure here
    s3Keys.forEach(s3Key -> {
      Thread.ofVirtual().start(()->{
        try {
          dqsIndexGenerator.generateIndex(s3Key);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      });
    });

  }
}
