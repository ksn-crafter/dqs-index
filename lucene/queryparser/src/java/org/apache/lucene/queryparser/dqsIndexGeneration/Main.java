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

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class Main {
  public static void main(String[] args) throws IOException {
    //writeIndex();
    search(Paths.get("C:\\D\\Caizin\\lucene_index"));
    //readSplitsAndWriteLuceneSegments();
    //independentSegmentSearch();
  }

  public static void writeIndex() throws IOException {
    String bucketName = "dqs-poc-data";
    String prefix = "128MB-chunks/Wiki/";
    S3Adapter s3Adapter = new S3Adapter();

    List<String> s3Keys = s3Adapter.getAllFileKeys(bucketName, prefix);
    DqsIndexGenerator dqsIndexGenerator = new DqsIndexGenerator();
    // TODO: implement back pressure here
//    s3Keys.forEach(
//        s3Key -> {
//          Thread.ofVirtual()
//              .start(
//                  () -> {
//                    try {
//                      dqsIndexGenerator.generateIndex(s3Key);
//                    } catch (IOException e) {
//                      throw new RuntimeException(e);
//                    }
//                  });
//        });

    try {
       dqsIndexGenerator.generateIndex(s3Keys.getFirst());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }


  private static void independentSegmentSearch() {
    search(Paths.get("C:\\D\\Caizin\\lucene-splits\\split_0_dir"));
    search(Paths.get("C:\\D\\Caizin\\lucene-splits\\split_1_dir"));
    search(Paths.get("C:\\D\\Caizin\\lucene-splits\\split_2_dir"));
    search(Paths.get("C:\\D\\Caizin\\lucene-splits\\split_3_dir"));
    search(Paths.get("C:\\D\\Caizin\\lucene-splits\\split_4_dir"));
    search(Paths.get("C:\\D\\Caizin\\lucene-splits\\split_5_dir"));
    search(Paths.get("C:\\D\\Caizin\\lucene-splits\\split_6_dir"));
    search(Paths.get("C:\\D\\Caizin\\lucene-splits\\split_7_dir"));
    search(Paths.get("C:\\D\\Caizin\\lucene-splits\\split_8_dir"));
  }

  private static void search(Path indexPath) {
    Directory indexDirectory = null;
    DirectoryReader reader = null;

    try {
      indexDirectory = FSDirectory.open(indexPath);
      reader = DirectoryReader.open(indexDirectory);

      IndexSearcher searcher = new IndexSearcher(reader);
      StandardAnalyzer analyzer = new StandardAnalyzer();

      QueryParser parser = new QueryParser("body", analyzer);
      String queryString = "amounts";

      Query query = parser.parse(queryString);
      System.out.println(
          "Searching for: '" + queryString + "' in 'body' field of index at " + indexPath.toAbsolutePath());

      TopDocs hits = searcher.search(query, 10);
      System.out.println("Found " + hits.totalHits.value() + " matching document(s):");

//      List<Document> documents = new ArrayList<>();
//      for (ScoreDoc scoreDoc : hits.scoreDocs) {
//        Document foundDocument = searcher.storedFields().document(scoreDoc.doc);
//        documents.add(foundDocument);
//
//        System.out.println(
//            foundDocument.get("id"));
//      }
//      System.out.println("\nTotal documents collected in list: " + documents.size());

    } catch (IOException e) {
      System.err.println("Error accessing Lucene index or performing search: " + e.getMessage());
    } catch (Exception e) {
      System.err.println("Error parsing query: " + e.getMessage());
    } finally {
      try {
        if (reader != null) {
          reader.close();
        }
        if (indexDirectory != null) {
          indexDirectory.close();
        }
      } catch (IOException e) {
        System.err.println("Error closing Lucene resources: " + e.getMessage());
      }
    }
  }

  private static void readSplitsAndWriteLuceneSegments() throws IOException {
    Path indexPath = Paths.get("C:\\D\\Caizin\\lucene-splits");
    try (Stream<Path> files = Files.list(indexPath)) {
      files.filter(path -> {
        String name = path.getFileName().toString();
        return name.startsWith("split");
      }).forEach(splitFilePath -> {
        try {
          String splitFileName = splitFilePath.getFileName().toString();

          Path outputDirectory = indexPath.resolve(splitFileName+ "_dir");
          Files.createDirectories(outputDirectory);

          readSplitAndWriteLuceneSegment(outputDirectory, splitFilePath);
        } catch (IOException e) {
          throw new UncheckedIOException("Failed to process split file: " + splitFilePath.getFileName().toString(), e);
        }
      });
    }
  }

  private static void readSplitAndWriteLuceneSegment(Path outputDirectory, Path splitFilePath) throws IOException {
    String splitFileName = splitFilePath.getFileName().toString();
    String segmentName = splitFileName.substring("split".length());

    try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(splitFilePath)))) {
      // Read and write cfeBytes
      int cfeLen = in.readInt();
      byte[] cfeBytes = in.readNBytes(cfeLen);
      Files.write(outputDirectory.resolve(segmentName + ".cfe"), cfeBytes);

      // Read and write cfsBytes
      int cfsLen = in.readInt();
      byte[] cfsBytes = in.readNBytes(cfsLen);
      Files.write(outputDirectory.resolve(segmentName + ".cfs"), cfsBytes);

      // Read and write siBytes
      int siLen = in.readInt();
      byte[] siBytes = in.readNBytes(siLen);
      Files.write(outputDirectory.resolve(segmentName + ".si"), siBytes);

      //Read generation
      long generation = in.readLong();

      // Read and write segmentsBytes
      int segLen = in.readInt();
      byte[] segmentsBytes = in.readNBytes(segLen);

      Files.write(outputDirectory.resolve("segments_" + generation), segmentsBytes);
    }
  }

}
