package org.apache.lucene.queryparser.dqsIndexGeneration;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.SegmentCommitInfo;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.IndexOutput;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class Main {
  public static void main(String[] args) throws IOException {
    String inputFolderS3Path = "s3://dqs-poc-data/128MB-chunks/Wiki/";
    String outputFolderS3Path = "C:\\D\\Caizin\\dqs-index\\sample-dqs-index";//TODO: add s3 path here
    writeIndex(inputFolderS3Path,outputFolderS3Path);
  }

  public static void writeIndex(String inputFolderS3Path, String outputFolderS3Path) throws IOException {

    String bucketName = "dqs-poc-data";
    String prefix = "128MB-chunks/Wiki/";
    S3FileDownloader s3FileDownloader = new S3FileDownloader();

    List<String> s3Keys = s3FileDownloader.getAllFileKeys(bucketName, prefix);
    DqsIndexGenerator dqsIndexGenerator = new DqsIndexGenerator();
    s3Keys.forEach(s3Key -> {
      Thread.ofVirtual().start(()->{
          dqsIndexGenerator.generateIndex(s3Key);
      });
    });
  }

  //take input s3 folder path
  //list all files
  //for each file: parse it, create a lucene document
  private static void writeIndex() throws IOException {
    Path indexPath = Paths.get("C:\\D\\Caizin\\dqs-index\\sample-dqs-index");
    Directory indexDirectory = FSDirectory.open(indexPath);

    StandardAnalyzer analyzer = new StandardAnalyzer();

    IndexWriterConfig config = new IndexWriterConfig(analyzer);
    config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);

    IndexWriter writer = new IndexWriter(indexDirectory, config);

    addDoc(writer, "Lucene in Action", "Manning Publications", "2004");
    addDoc(writer, "Lucene for Dummies", "Wiley Publishing", "2006");
    addDoc(writer, "The Art of Search", "O'Reilly Media", "2010");
    addDoc(writer, "Java Programming", "Addison-Wesley", "2000");
    addDoc(writer, "Java Programming 2", "Addison-Wesley", "1998");

    writer.commit();

    System.out.println("$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$");
    List<SegmentCommitInfo> segments = writer.segmentInfos.segments;
    for (SegmentCommitInfo segmentCommitInfo : segments) {
      System.out.println("Id " + Arrays.toString(segmentCommitInfo.id));
      System.out.println("Name " + segmentCommitInfo.info.name);
      System.out.println("Directory " + segmentCommitInfo.info.dir);
      System.out.println("segmentCommitInfo.info.id " + Arrays.toString(segmentCommitInfo.info.id));
      Set<String> files = segmentCommitInfo.info.files();

      for (String file : files) {
        System.out.println("File = " + file);
      }
    }

    System.out.println("$$$$$$$########## " + writer.segmentInfos.size());

    if (writer.segmentInfos.size() > 1) {
      System.out.println("$$$$$$$##########");
      //we will get a bunch of binary files here
      //upload them to s3
      List<IndexOutput> outputs = writer.segmentInfos.writeSeparateSegmentsNFiles(writer.directory);
      writer.directory.syncMetaData();
      writer.directory.sync(outputs.stream().map(IndexOutput::getName).collect(Collectors.toList()));
    }

//        writer.close();
    System.out.println("Index created successfully on disk at: " + indexPath.toAbsolutePath());
  }

  private static void addDoc(IndexWriter writer, String title, String publisher, String year) throws IOException {
    Document doc = new Document();
    doc.add(new TextField("title", title, Field.Store.YES));
    doc.add(new StringField("publisher", publisher, Field.Store.YES));
    doc.add(new StringField("year", year, Field.Store.YES));

    writer.addDocument(doc);
  }
}
