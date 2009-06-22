package org.apache.hadoop.io.compress;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Random;

import junit.framework.TestCase;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.DataInputBuffer;
import org.apache.hadoop.io.DataOutputBuffer;
import org.apache.hadoop.io.RandomDatum;
import org.apache.hadoop.util.ReflectionUtils;
import org.apache.hadoop.io.compress.CompressionOutputStream;

public class TestCodec extends TestCase {

  private static final Log LOG= 
    LogFactory.getLog("org.apache.hadoop.io.compress.TestCodec");
  
  public void testCodec() throws IOException {
    int count = 10000;
    int seed = new Random().nextInt();
    
    codecTest(seed, count, "org.apache.hadoop.io.compress.DefaultCodec");
  }
  
  private static void codecTest(int seed, int count, String codecClass) 
  throws IOException {
    
    // Create the codec
    Configuration conf = new Configuration();
    CompressionCodec codec = null;
    try {
      codec = (CompressionCodec)
        ReflectionUtils.newInstance(conf.getClassByName(codecClass), conf);
    } catch (ClassNotFoundException cnfe) {
      throw new IOException("Illegal codec!");
    }
    LOG.debug("Created a Codec object of type: " + codecClass);

    // Generate data
    DataOutputBuffer data = new DataOutputBuffer();
    RandomDatum.Generator generator = new RandomDatum.Generator(seed);
    for(int i=0; i < count; ++i) {
      generator.next();
      RandomDatum key = generator.getKey();
      RandomDatum value = generator.getValue();
      
      key.write(data);
      value.write(data);
    }
    DataInputBuffer originalData = new DataInputBuffer();
    DataInputStream originalIn = new DataInputStream(new BufferedInputStream(originalData));
    originalData.reset(data.getData(), 0, data.getLength());
    
    LOG.debug("Generated " + count + " records");
    
    // Compress data
    DataOutputBuffer compressedDataBuffer = new DataOutputBuffer();
    CompressionOutputStream deflateFilter = 
      codec.createOutputStream(compressedDataBuffer);
    DataOutputStream deflateOut = 
      new DataOutputStream(new BufferedOutputStream(deflateFilter));
    
    deflateFilter.resetState();
    compressedDataBuffer.reset();
    deflateOut.write(data.getData(), 0, data.getLength());
    deflateOut.flush();
    deflateFilter.finish();
    LOG.debug("Finished compressing data");
    
    // De-compress data
    DataInputBuffer deCompressedDataBuffer = new DataInputBuffer();
    CompressionInputStream inflateFilter = 
      codec.createInputStream(deCompressedDataBuffer);
    DataInputStream inflateIn = 
      new DataInputStream(new BufferedInputStream(inflateFilter));
    
    inflateFilter.resetState();
    deCompressedDataBuffer.reset(compressedDataBuffer.getData(), 0, 
        compressedDataBuffer.getLength());

    // Check
    for(int i=0; i < count; ++i) {
      RandomDatum k1 = new RandomDatum();
      RandomDatum v1 = new RandomDatum();
      k1.readFields(originalIn);
      v1.readFields(originalIn);
      
      RandomDatum k2 = new RandomDatum();
      RandomDatum v2 = new RandomDatum();
      k2.readFields(inflateIn);
      v2.readFields(inflateIn);
    }
    LOG.debug("SUCCESS! Completed checking " + count + " records");
  }
  
  public static void main(String[] args) {
    int count = 10000;
    String codecClass = "org.apache.hadoop.io.compress.DefaultCodec";

    String usage = "TestCodec [-count N] [-codec <codec class>]";
    if (args.length == 0) {
      System.err.println(usage);
      System.exit(-1);
    }

    try {
    for (int i=0; i < args.length; ++i) {       // parse command line
      if (args[i] == null) {
        continue;
      } else if (args[i].equals("-count")) {
        count = Integer.parseInt(args[++i]);
      } else if (args[i].equals("-codec")) {
        codecClass = args[++i];
      }
    }

    int seed = 0;
    codecTest(seed, count, codecClass);
    } catch (Exception e) {
      System.err.println("Caught: " + e);
      e.printStackTrace();
    }
    
  }

  public TestCodec(String name) {
    super(name);
  }

}