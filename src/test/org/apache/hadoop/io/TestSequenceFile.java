/**
 * Copyright 2005 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.io;

import java.io.*;
import java.util.*;
import junit.framework.TestCase;

import org.apache.commons.logging.*;

import org.apache.hadoop.fs.*;
import org.apache.hadoop.io.SequenceFile.CompressionType;
import org.apache.hadoop.conf.*;


/** Support for flat files of binary key/value pairs. */
public class TestSequenceFile extends TestCase {
  private static Log LOG = SequenceFile.LOG;

  private static Configuration conf = new Configuration();
  
  public TestSequenceFile(String name) { super(name); }

  /** Unit tests for SequenceFile. */
  public void testSequenceFile() throws Exception {
    int count = 1024 * 10;
    int megabytes = 1;
    int factor = 5;
    Path file = new Path(System.getProperty("test.build.data",".")+"/test.seq");
    Path recordCompressedFile = 
      new Path(System.getProperty("test.build.data",".")+"/test.rc.seq");
    Path blockCompressedFile = 
      new Path(System.getProperty("test.build.data",".")+"/test.bc.seq");
 
    int seed = new Random().nextInt();
    LOG.info("Seed = " + seed);

    FileSystem fs = new LocalFileSystem(conf);
    try {
        //LOG.setLevel(Level.FINE);

        // SequenceFile.Writer
        writeTest(fs, count, seed, file, CompressionType.NONE);
        readTest(fs, count, seed, file);

        sortTest(fs, count, megabytes, factor, false, file);
        checkSort(fs, count, seed, file);

        sortTest(fs, count, megabytes, factor, true, file);
        checkSort(fs, count, seed, file);

        mergeTest(fs, count, seed, file, CompressionType.NONE, false, 
            factor, megabytes);
        checkSort(fs, count, seed, file);

        mergeTest(fs, count, seed, file, CompressionType.NONE, true, 
            factor, megabytes);
        checkSort(fs, count, seed, file);
        
        // SequenceFile.RecordCompressWriter
        writeTest(fs, count, seed, recordCompressedFile, CompressionType.RECORD);
        readTest(fs, count, seed, recordCompressedFile);

        sortTest(fs, count, megabytes, factor, false, recordCompressedFile);
        checkSort(fs, count, seed, recordCompressedFile);

        sortTest(fs, count, megabytes, factor, true, recordCompressedFile);
        checkSort(fs, count, seed, recordCompressedFile);

        mergeTest(fs, count, seed, recordCompressedFile, 
            CompressionType.RECORD, false, factor, megabytes);
        checkSort(fs, count, seed, recordCompressedFile);

        mergeTest(fs, count, seed, recordCompressedFile, 
            CompressionType.RECORD, true, factor, megabytes);
        checkSort(fs, count, seed, recordCompressedFile);
        
        // SequenceFile.BlockCompressWriter
        writeTest(fs, count, seed, blockCompressedFile, CompressionType.BLOCK);
        readTest(fs, count, seed, blockCompressedFile);

        sortTest(fs, count, megabytes, factor, false, blockCompressedFile);
        checkSort(fs, count, seed, blockCompressedFile);

        sortTest(fs, count, megabytes, factor, true, blockCompressedFile);
        checkSort(fs, count, seed, blockCompressedFile);

        mergeTest(fs, count, seed, blockCompressedFile, CompressionType.BLOCK, 
            false, factor, megabytes);
        checkSort(fs, count, seed, blockCompressedFile);

        mergeTest(fs, count, seed, blockCompressedFile, CompressionType.BLOCK, 
            true, factor, megabytes);
        checkSort(fs, count, seed, blockCompressedFile);

        } finally {
        fs.close();
    }
  }

  private static void writeTest(FileSystem fs, int count, int seed, Path file, 
      CompressionType compressionType)
    throws IOException {
    fs.delete(file);
    LOG.info("creating " + count + " records with " + compressionType +
              " compression");
    SequenceFile.Writer writer = 
      SequenceFile.createWriter(fs, conf, file, 
          RandomDatum.class, RandomDatum.class, compressionType);
    RandomDatum.Generator generator = new RandomDatum.Generator(seed);
    for (int i = 0; i < count; i++) {
      generator.next();
      RandomDatum key = generator.getKey();
      RandomDatum value = generator.getValue();

      writer.append(key, value);
    }
    writer.close();
  }

  private static void readTest(FileSystem fs, int count, int seed, Path file)
    throws IOException {
    LOG.debug("reading " + count + " records");
    SequenceFile.Reader reader = new SequenceFile.Reader(fs, file, conf);
    RandomDatum.Generator generator = new RandomDatum.Generator(seed);

    RandomDatum k = new RandomDatum();
    RandomDatum v = new RandomDatum();
    DataOutputBuffer rawKey = new DataOutputBuffer();
    SequenceFile.ValueBytes rawValue = reader.createValueBytes();
    
    for (int i = 0; i < count; i++) {
      generator.next();
      RandomDatum key = generator.getKey();
      RandomDatum value = generator.getValue();

      try {
        if ((i%5) == 10) {
          // Testing 'raw' apis
          rawKey.reset();
          reader.nextRaw(rawKey, rawValue);
        } else {
          // Testing 'non-raw' apis 
          if ((i%2) == 0) {
            reader.next(k);
            reader.getCurrentValue(v);
          } else {
            reader.next(k, v);
          }
          // Sanity check
          if (!k.equals(key))
            throw new RuntimeException("wrong key at " + i);
          if (!v.equals(value))
            throw new RuntimeException("wrong value at " + i);
        }
      } catch (IOException ioe) {
        LOG.info("Problem on row " + i);
        LOG.info("Expected value = " + value);
        LOG.info("Expected len = " + value.getLength());
        LOG.info("Actual value = " + v);
        LOG.info("Actual len = " + v.getLength());
        LOG.info("Key equals: " + k.equals(key));
        LOG.info("value equals: " + v.equals(value));
        throw ioe;
      }

    }
    reader.close();
  }


  private static void sortTest(FileSystem fs, int count, int megabytes, 
                               int factor, boolean fast, Path file)
    throws IOException {
    fs.delete(new Path(file+".sorted"));
    SequenceFile.Sorter sorter = newSorter(fs, fast, megabytes, factor);
    LOG.debug("sorting " + count + " records");
    sorter.sort(file, file.suffix(".sorted"));
    LOG.info("done sorting " + count + " debug");
  }

  private static void checkSort(FileSystem fs, int count, int seed, Path file)
    throws IOException {
    LOG.info("sorting " + count + " records in memory for debug");
    RandomDatum.Generator generator = new RandomDatum.Generator(seed);
    SortedMap map = new TreeMap();
    for (int i = 0; i < count; i++) {
      generator.next();
      RandomDatum key = generator.getKey();
      RandomDatum value = generator.getValue();
      map.put(key, value);
    }

    LOG.debug("checking order of " + count + " records");
    RandomDatum k = new RandomDatum();
    RandomDatum v = new RandomDatum();
    Iterator iterator = map.entrySet().iterator();
    SequenceFile.Reader reader =
      new SequenceFile.Reader(fs, file.suffix(".sorted"), conf);
    for (int i = 0; i < count; i++) {
      Map.Entry entry = (Map.Entry)iterator.next();
      RandomDatum key = (RandomDatum)entry.getKey();
      RandomDatum value = (RandomDatum)entry.getValue();

      reader.next(k, v);

      if (!k.equals(key))
        throw new RuntimeException("wrong key at " + i);
      if (!v.equals(value))
        throw new RuntimeException("wrong value at " + i);
    }

    reader.close();
    LOG.debug("sucessfully checked " + count + " records");
  }

  private static void mergeTest(FileSystem fs, int count, int seed, Path file, 
                                CompressionType compressionType,
                                boolean fast, int factor, int megabytes)
    throws IOException {

    LOG.debug("creating "+factor+" files with "+count/factor+" records");

    SequenceFile.Writer[] writers = new SequenceFile.Writer[factor];
    Path[] names = new Path[factor];
    Path[] sortedNames = new Path[factor];
    
    for (int i = 0; i < factor; i++) {
      names[i] = file.suffix("."+i);
      sortedNames[i] = names[i].suffix(".sorted");
      fs.delete(names[i]);
      fs.delete(sortedNames[i]);
      writers[i] = SequenceFile.createWriter(fs, conf, names[i], 
          RandomDatum.class, RandomDatum.class, compressionType);
    }

    RandomDatum.Generator generator = new RandomDatum.Generator(seed);

    for (int i = 0; i < count; i++) {
      generator.next();
      RandomDatum key = generator.getKey();
      RandomDatum value = generator.getValue();

      writers[i%factor].append(key, value);
    }

    for (int i = 0; i < factor; i++)
      writers[i].close();

    for (int i = 0; i < factor; i++) {
      LOG.debug("sorting file " + i + " with " + count/factor + " records");
      newSorter(fs, fast, megabytes, factor).sort(names[i], sortedNames[i]);
    }

    LOG.info("merging " + factor + " files with " + count/factor + " debug");
    fs.delete(new Path(file+".sorted"));
    newSorter(fs, fast, megabytes, factor)
      .merge(sortedNames, file.suffix(".sorted"));
  }

  private static SequenceFile.Sorter newSorter(FileSystem fs, 
                                               boolean fast,
                                               int megabytes, int factor) {
    SequenceFile.Sorter sorter = 
      fast
      ? new SequenceFile.Sorter(fs, new RandomDatum.Comparator(),RandomDatum.class, conf)
      : new SequenceFile.Sorter(fs, RandomDatum.class, RandomDatum.class, conf);
    sorter.setMemory(megabytes * 1024*1024);
    sorter.setFactor(factor);
    return sorter;
  }


  /** For debugging and testing. */
  public static void main(String[] args) throws Exception {
    int count = 1024 * 1024;
    int megabytes = 1;
    int factor = 10;
    boolean create = true;
    boolean rwonly = false;
    boolean check = false;
    boolean fast = false;
    boolean merge = false;
    String compressType = "NONE";
    Path file = null;
    int seed = new Random().nextInt();

    String usage = "Usage: SequenceFile (-local | -dfs <namenode:port>) " +
        "[-count N] " + 
        "[-seed #] [-check] [-compressType <NONE|RECORD|BLOCK>] " +
        "[[-rwonly] | {[-megabytes M] [-factor F] [-nocreate] [-fast] [-merge]}] " +
        " file";
    if (args.length == 0) {
        System.err.println(usage);
        System.exit(-1);
    }
    
    FileSystem fs = FileSystem.parseArgs(args, 0, conf);      
    try {
      for (int i=0; i < args.length; ++i) {       // parse command line
          if (args[i] == null) {
              continue;
          } else if (args[i].equals("-count")) {
              count = Integer.parseInt(args[++i]);
          } else if (args[i].equals("-megabytes")) {
              megabytes = Integer.parseInt(args[++i]);
          } else if (args[i].equals("-factor")) {
            factor = Integer.parseInt(args[++i]);
          } else if (args[i].equals("-seed")) {
            seed = Integer.parseInt(args[++i]);
          } else if (args[i].equals("-rwonly")) {
              rwonly = true;
          } else if (args[i].equals("-nocreate")) {
              create = false;
          } else if (args[i].equals("-check")) {
              check = true;
          } else if (args[i].equals("-fast")) {
              fast = true;
          } else if (args[i].equals("-merge")) {
              merge = true;
          } else if (args[i].equals("-compressType")) {
              compressType = args[++i];
          } else {
              // file is required parameter
              file = new Path(args[i]);
          }
        }
        LOG.info("count = " + count);
        LOG.info("megabytes = " + megabytes);
        LOG.info("factor = " + factor);
        LOG.info("create = " + create);
        LOG.info("seed = " + seed);
        LOG.info("rwonly = " + rwonly);
        LOG.info("check = " + check);
        LOG.info("fast = " + fast);
        LOG.info("merge = " + merge);
        LOG.info("compressType = " + compressType);
        LOG.info("file = " + file);

        if (rwonly && (!create || merge || fast)) {
          System.err.println(usage);
          System.exit(-1);
        }

        CompressionType compressionType = 
          CompressionType.valueOf(compressType);

        if (rwonly || (create && !merge)) {
            writeTest(fs, count, seed, file, compressionType);
            readTest(fs, count, seed, file);
        }

        if (!rwonly) {
          if (merge) {
            mergeTest(fs, count, seed, file, compressionType, 
                fast, factor, megabytes);
          } else {
            sortTest(fs, count, megabytes, factor, fast, file);
          }
        }
    
        if (check) {
            checkSort(fs, count, seed, file);
        }
      } finally {
          fs.close();
      }
  }
}