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
import org.apache.lucene.index.SegmentCommitInfo;
import org.apache.lucene.store.ByteBuffersIndexOutput;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.MMapDirectory;
import org.apache.lucene.util.IOUtils;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

public class DqsIndexGenerator {

  public void generateIndex(String s3Key) throws IOException {
    String fileName = s3Key.substring(s3Key.lastIndexOf("/") + 1);
    Path tempIndexDir = Files.createTempDirectory(fileName.substring(0, fileName.indexOf(".")));
    S3Adapter s3Adapter = new S3Adapter();

    try (GZIPInputStream gzipInputStream = new GZIPInputStream(s3Adapter.download("dqs-poc-data", s3Key));
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(gzipInputStream))) {
      writeIndex(bufferedReader, tempIndexDir);

      s3Adapter.uploadToS3(tempIndexDir, "dqs-indexes/" + tempIndexDir.getFileName().toString());
      IOUtils.rm(tempIndexDir);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void writeIndex(BufferedReader bufferedReader, Path outputIndexDir) throws IOException {
    Directory luceneDirectory = MMapDirectory.open(outputIndexDir);
    IndexWriterConfig config = new IndexWriterConfig(new StandardAnalyzer());
    IndexWriter writer = new IndexWriter(luceneDirectory, config);
    final int BATCH_SIZE = 1000;

    ObjectMapper objectMapper = new ObjectMapper();

    try (JsonParser parser = objectMapper.getFactory().createParser(bufferedReader)) {
      List<Document> documents = new ArrayList<>();
      while (parser.nextToken() == JsonToken.START_OBJECT) {
        JsonNode jsonNode = objectMapper.readTree(parser);
        documents.add(generateLuceneDocument(jsonNode));
        if (documents.size() >= BATCH_SIZE) {
          writer.addDocuments(documents);
          documents.clear();
        }
      }
      if (!documents.isEmpty()) {
        writer.addDocuments(documents);
      }
    } catch (Exception e) {
      System.out.println("Error while creating index for file: " + " " + e.getMessage());
    } finally {
      //TODO: see if this close messes up anything
      //luceneDirectory.close();
      writer.commit();
    }

    writeSplits(writer, outputIndexDir);
  }

  private void writeSplits(IndexWriter writer, Path directory) throws IOException {
    List<ByteBuffersIndexOutput> segmentBuffers = writer.segmentInfos.writeSeparateSegmentsInBuffer();
    assert segmentBuffers.size() == writer.segmentInfos.size();

    int index = 0;
    for (SegmentCommitInfo segmentCommitInfo : writer.segmentInfos) {
      String segmentName = segmentCommitInfo.info.name;
      Path outputPath = directory.resolve("split" + segmentName);

      try (DataOutputStream out =
          new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(outputPath.toAbsolutePath())))) {

        byte[] cfeBytes = Files.readAllBytes(directory.resolve(segmentName + ".cfe"));
        byte[] cfsBytes = Files.readAllBytes(directory.resolve(segmentName + ".cfs"));
        byte[] siBytes = Files.readAllBytes(directory.resolve(segmentName + ".si"));
        byte[] segmentsBytes = segmentBuffers.get(index).toArrayCopy();

        // Write cfeBytes
        out.writeInt(cfeBytes.length);
        out.write(cfeBytes);

        // Write cfsBytes
        out.writeInt(cfsBytes.length);
        out.write(cfsBytes);

        // Write siBytes
        out.writeInt(siBytes.length);
        out.write(siBytes);

        // Write segments's generation
        out.writeLong(writer.segmentInfos.getGeneration());

        // Write segmentsBytes
        out.writeInt(segmentsBytes.length);
        out.write(segmentsBytes);

        index += 1;
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
