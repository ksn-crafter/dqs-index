package org.apache.lucene.queryparser.dqsIndexGeneration;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.store.MMapDirectory;
import org.apache.lucene.util.IOUtils;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

public class DqsIndexGenerator {

  public void generateIndex(String s3Key) throws IOException {
    Path tempIndexDir = null;
    S3Wrapper s3Wrapper = new S3Wrapper();
    Directory luceneDirectory = null;
    try (
        GZIPInputStream gzipInputStream = new GZIPInputStream(s3Wrapper.download(s3Key));
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(gzipInputStream))) {
      String baseFileName = s3Key.substring(s3Key.lastIndexOf("/") + 1);
      tempIndexDir = Files.createTempDirectory(baseFileName.substring(0, baseFileName.indexOf(".")) + "-index-");

      luceneDirectory = MMapDirectory.open(tempIndexDir);
      IndexWriterConfig config = new IndexWriterConfig(new StandardAnalyzer());
      IndexWriter writer = new IndexWriter(luceneDirectory, config);

      ObjectMapper objectMapper = new ObjectMapper();

      try (JsonParser parser = objectMapper.getFactory().createParser(bufferedReader)) {
        List<Document> documents = new ArrayList<>();
        while (parser.nextToken() == JsonToken.START_OBJECT) {
          JsonNode jsonNode = objectMapper.readTree(parser);
          documents.add(generateLuceneDocument(jsonNode));
        }
        if (!documents.isEmpty()) {
          writer.addDocuments(documents);
        }
      }
      writer.commit();

      System.out.println("$$$$$$$########## " + writer.segmentInfos.size());

      if (writer.segmentInfos.size() > 1) {
        System.out.println("$$$$$$$##########");
        //TODO: we will get a bunch of binary files here, upload them to s3

        List<IndexOutput> outputs = writer.segmentInfos.writeSeparateSegmentsNFiles(writer.directory);
        writer.directory.syncMetaData();
        writer.directory.sync(outputs.stream().map(IndexOutput::getName).collect(Collectors.toList()));
      }


      //uploadIndexToS3(outputFolderPath, tempZipFile, s3Client);

      IOUtils.rm(tempIndexDir);
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      if(luceneDirectory != null){
        luceneDirectory.close();
      }
    }
  }

  private static Document generateLuceneDocument(JsonNode jsonNode) throws JsonProcessingException {
    Document doc = new Document();

    doc.add(new TextField("id", jsonNode.get("id").toString(), Field.Store.YES));
    doc.add(new TextField("subject", jsonNode.get("subject").toString(), Field.Store.NO));
    doc.add(new TextField("body", jsonNode.get("body").toString(), Field.Store.NO));
    doc.add(new TextField("date", jsonNode.get("date").toString(), Field.Store.NO));
    doc.add(new TextField("from", jsonNode.get("from").toString(), Field.Store.NO));
    doc.add(new TextField("to", jsonNode.get("to").toString(), Field.Store.NO));
    doc.add(new TextField("cc", jsonNode.get("cc").toString(), Field.Store.NO));
    doc.add(new TextField("bcc", jsonNode.get("bcc").toString(), Field.Store.NO));

    return doc;
  }
}
