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

package org.apache.hadoop.mapred;

import java.io.IOException;
import junit.framework.TestCase;

/**
 * A JUnit test to test min map-reduce cluster with local file system.
 *
 * @author Milind Bhandarkar
 */
public class TestMiniMRLocalFS extends TestCase {
  
    static final int NUM_MAPS = 10;
    static final int NUM_SAMPLES = 100000;
    
  public void testWithLocal() throws IOException {
      MiniMRCluster mr = null;
      try {
          mr = new MiniMRCluster(60030, 60040, 2, "local", false);
          double estimate = PiEstimator.launch(NUM_MAPS, NUM_SAMPLES, "localhost:60030", "local");
          double error = Math.abs(Math.PI - estimate);
          assertTrue("Error in PI estimation "+error+" exceeds 0.01", (error < 0.01));
      } finally {
          if (mr != null) { mr.shutdown(); }
      }
  }
}