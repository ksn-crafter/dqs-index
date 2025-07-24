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
    // TODO: implement back pressure here
    s3Keys.forEach(
        s3Key -> {
          Thread.ofVirtual()
              .start(
                  () -> {
                    try {
                      dqsIndexGenerator.generateIndex(s3Key);
                    } catch (IOException e) {
                      throw new RuntimeException(e);
                    }
                  });
        });
  }
}
