package org.area515.resinprinter.slice;
/*
   Copyright 2001  Universidad del Pais Vasco (UPV/EHU)
   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at
       http://www.apache.org/licenses/LICENSE-2.0
   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.io.Reader;
import java.nio.ByteBuffer;
// New from JDK 1.4 for endian related problems
import java.nio.ByteOrder;
import java.util.Collection;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Title:         STL Loader
 * Description:   STL files loader (Supports ASCII and binary files) for Java3D
 *                Needs JDK 1.4 due to endian problems
 * 
 * Company:       Universidad del Pais Vasco (UPV/EHU)
 * @author:       Carlos Pedrinaci Godoy
 * 
 * Company:       National Aeronautics and Space Administration, Inlets and Nozzles (NASA/RTE0)
 * @author:       Avinash Devalla
 * 
 * @version:      1.0
 *
 * Contact: xenicp@yahoo.es
 * Contact:	avinash.s.devalla@gmail.com
 */

public abstract class StlFile<T,P> {
  private static final Logger logger = LogManager.getLogger();

  //private int flag;                         // Needed cause implements Loader
  private boolean Ascii = true;             // File type Ascii -> true o binary -> false
  private boolean rewriteNormalsWithRightHandRule = false;
  
  protected Collection<T> triangles;
  protected double zmin = Double.MAX_VALUE;
  protected double zmax = -Double.MAX_VALUE;
  protected double xmin = Double.MAX_VALUE;
  protected double xmax = -Double.MAX_VALUE;
  protected double ymin = Double.MAX_VALUE;
  protected double ymax = -Double.MAX_VALUE;
  
  private String objectName = new String("Not available");
  
  /**
  *  Constructor
  */
  public StlFile()
  {
  }

  /**
   * Method that reads the EOL
   * Needed for verifying that the file has a correct format
   *
   * @param parser The file parser. An instance of StlFileParser.
   */
  private void readEOL(StlFileParser parser) throws IOException {
	try {
		do {
			parser.nextToken();
		} while (parser.ttype != StlFileParser.TT_EOL);
	} catch (IOException e) {
	    throw new IOException("Error getting next token:" + parser, e);
	}
  }

  /**
   * Method that reads the word "solid" and stores the object name.
   * It also detects what kind of file it is
   * TO-DO:
   *    1.- Better way control of exceptions?
   *    2.- Better way to decide between Ascii and Binary?
   *
   * @param parser The file parser. An instance of StlFileParser.
   */
	private void readSolid(StlFileParser parser) throws IOException {
		if (parser.sval == null || !parser.sval.equals("solid")) {
			logger.warn("Expecting solid on line:{}", parser.lineno());
			// If the first word is not "solid" then we consider the file is
			// binary
			// Can give us problems if the comment of the binary file begins by
			// "solid"
			this.setAscii(false);
		} else { // It's an ASCII file
			try {
				parser.nextToken();
			} catch (IOException e) {
				throw new IOException("IO Error on line " + parser.lineno() + ": " + e.getMessage());
			}
			if (parser.ttype != StlFileParser.TT_WORD) {
				// Is the object name always provided???
				throw new IOException("Format Error:expecting the object name on line " + parser.lineno());
			} else {
				// Store the object Name
				this.setObjectName(new String(parser.sval));
				this.readEOL(parser);
			}
		}
	}// End of readSolid


  /**
   * Method that reads tokens and allows iteration through facet definition
   * Written by adevalla
   * 
   * @param parser - file parser, instance of StlFileParser
   * @param parseKey - string "outer", "loop", "endloop", or "endfacet"
   * */
  
  private void readToken(StlFileParser parser, String parseKey) throws IOException {
		if (parser.ttype != StlFileParser.TT_WORD || !parser.sval.equals(parseKey)) {
			throw new IOException("Format Error:expecting " + parseKey + " on line " + parser.lineno());
	    } else {
	    	if (parseKey.equals("outer")) {
	    		try {
					parser.nextToken();
				} catch (IOException e) {
					throw new IOException("Expected next token after outer", e);
				}
	    	}
	    	
	    	readEOL(parser);
	    }
  }
  
  /**
   * Method that reads normal, vertex coordinates; stores them as Point3f.
   * Written by adevalla
   * 
   * @param parser - file parser, instance of StlFileParser
   * @param parseKey - string "normal", "vertex"
   * */
  
  	private double[] read3d(StlFileParser parser, String parseKey, boolean buildPointForNormal) throws IOException {
		double x, y, z;

		if (!(parser.ttype == StlFileParser.TT_WORD && parser.sval.equals(parseKey))) {
			throw new IOException("Format Error:expecting '" + parseKey + "' on line " + parser.lineno());
		}
		
		if (!parser.getNumber()) {
			throw new IOException("Format Error: expecting coordinate on line " + parser.lineno());
		}
		x = parser.nval;
		
		if (!parser.getNumber()) {
			throw new IOException("Format Error: expecting coordinate on line " + parser.lineno());
		}
		y = parser.nval;
		
		if (!parser.getNumber()) {
			throw new IOException("Format Error: expecting coordinate on line " + parser.lineno());
		}
		z = parser.nval;
		
		readEOL(parser);
		
		return new double[]{x, y, z};
	}
  
  /**
   * Method that reads a face of the object. 
   * (Cares about the format).
   *
   * @param parser The file parser. An instance of StlFileParser.
   */
  private void readFacet(StlFileParser parser) throws IOException {
		if (parser.ttype != StlFileParser.TT_WORD || !parser.sval.equals("facet")) {
			throw new IOException("Format Error:expecting 'facet' on line " + parser.lineno());
		}
		
		parser.nextToken();
		double[] normal = read3d(parser, "normal", true);
		parser.nextToken();
		readToken(parser, "outer");
		
		double triangle[][] = new double[3][];
		for (int i = 0; i < 3; i++) {
			parser.nextToken();
			triangle[i] = read3d(parser, "vertex", false);
		}

		parser.nextToken();
		readToken(parser, "endloop");

		parser.nextToken();
		readToken(parser, "endfacet");
		
		fixNormalIfBadSTLFile(normal, triangle[0], triangle[1], triangle[2]);
		buildTriangle(
				buildPoint(triangle[0][0], triangle[0][1], triangle[0][2]), 
				buildPoint(triangle[1][0], triangle[1][1], triangle[1][2]), 
				buildPoint(triangle[2][0], triangle[2][1], triangle[2][2]), normal);
  }// End of readFacet

  protected abstract void buildTriangle(P point1, P point2, P point3, double[] normal);
  protected abstract P buildPoint(double x, double y, double z);
  protected abstract Collection<T> createSet();
  protected abstract T getFirstTriangle();

  private void fixNormalIfBadSTLFile(double[] normal, double[] p1, double[] p2, double[] p3) {
		if ((normal[0] == 0 && normal[1] == 0 && normal[2] == 0) || rewriteNormalsWithRightHandRule) {
			/*normal[0] = (p3[1] - p2[1]) * (p2[2] - p1[2]) - (p3[2] - p2[2]) * (p2[1] - p1[1]);
			normal[1] = (p3[2] - p2[2]) * (p2[0] - p1[0]) - (p3[0] - p2[0]) * (p2[2] - p1[2]);
			normal[2] = (p3[0] - p2[0]) * (p2[1] - p1[1]) - (p3[1] - p2[1]) * (p2[0] - p1[0]);*/
			normal[0] = (p3[2] - p2[2]) * (p2[1] - p1[1]) - (p3[1] - p2[1]) * (p2[2] - p1[2]);
			normal[1] = (p3[0] - p2[0]) * (p2[2] - p1[2]) - (p3[2] - p2[2]) * (p2[0] - p1[0]);
			normal[2] = (p3[1] - p2[1]) * (p2[0] - p1[0]) - (p3[0] - p2[0]) * (p2[1] - p1[1]);
		}
  }
  
  /**
   * Method that reads a face in binary files
   * All binary versions of the methods end by 'B'
   * As in binary files we can read the number of faces, we don't need
   * to use coordArray and normArray (reading binary files should be faster)
   *
   * @param in The ByteBuffer with the data of the object.
   * @param index The facet index
   *
   * @throws IOException
   */
  public void readFacetB(ByteBuffer dataBuffer, int index) throws IOException {
	    // Read the Normal
		double normal[] = {
			dataBuffer.getFloat(), 
			dataBuffer.getFloat(), 
			dataBuffer.getFloat()};

	    // Read vertex1
		double p1[] = new double[]{dataBuffer.getFloat(), dataBuffer.getFloat(), dataBuffer.getFloat()};
		double p2[] = new double[]{dataBuffer.getFloat(), dataBuffer.getFloat(), dataBuffer.getFloat()};
		double p3[] = new double[]{dataBuffer.getFloat(), dataBuffer.getFloat(), dataBuffer.getFloat()};
		
		P point1 = buildPoint(p1[0], p1[1], p1[2]);
		P point2 = buildPoint(p2[0], p2[1], p2[2]);
		P point3 = buildPoint(p3[0], p3[1], p3[2]);
		
		fixNormalIfBadSTLFile(normal, p1, p2, p3);
		
		buildTriangle(point1, point2, point3, normal);
		
		//TODO: After each facet there are 2 bytes that can be used for color information, we should add those two bytes to the triangle.
        dataBuffer.get();
        dataBuffer.get();
	  }
  
  /**
   * Method for reading binary files
   * Execution is completly different
   * It uses ByteBuffer for reading data and ByteOrder for retrieving the machine's endian
   * (Needs JDK 1.4)
   *
   * TO-DO:
   *  1.-Be able to read files over Internet
   *  2.-If the amount of data expected is bigger than what is on the file then
   *  the program will block forever
   *
   * @param file The name of the file
   *
   * @throws IOException
   */
  private void readBinaryFile(InputStream data) throws IOException {
    ByteBuffer dataBuffer;                // For reading in the correct endian
    byte[] Info=new byte[80];             // Header data
    byte[] Array_number= new byte[4];     // Holds the number of faces
    byte[] Temp_Info;                     // Intermediate array

    int Number_faces; // First info (after the header) on the file


      // First 80 bytes aren't important
      if(80 != data.read(Info)) { // File is incorrect
    	logger.error("Format Error: 80 bytes expected");
        throw new IOException("STL Format Error: 80 bytes expected");
      } else { // We must first read the number of faces -> 4 bytes int
        // It depends on the endian so..

        data.read(Array_number);                      // We get the 4 bytes
        dataBuffer = ByteBuffer.wrap(Array_number);   // ByteBuffer for reading correctly the int
        dataBuffer.order(ByteOrder.nativeOrder());    // Set the right order
        Number_faces = dataBuffer.getInt();

        Temp_Info = new byte[50*Number_faces];        // Each face has 50 bytes of data

        data.read(Temp_Info);                         // We get the rest of the file

        dataBuffer = ByteBuffer.wrap(Temp_Info);      // Now we have all the data in this ByteBuffer
        dataBuffer.order(ByteOrder.nativeOrder());

        // We can create that array directly as we know how big it's going to be
        //coordArray = new Point3f[Number_faces*3]; // Each face has 3 vertex
        //normArray = new Vector3f[Number_faces];
        //stripCounts = new int[Number_faces];

        for(int i=0;i<Number_faces;i++) {
          //stripCounts[i]=3;
          try {
            readFacetB(dataBuffer,i);
            
          } catch (IOException e) {
            // Quitar
            logger.error("Format Error: iteration number " + i, e);
            throw new IOException("Format Error: iteration number " + i, e);
          }
        }//End for
      }// End file reading
  }// End of readBinaryFile

  private void readASCIIFile(InputStream inputStream) throws IOException {
		setAscii(true);
		
		Reader reader = new InputStreamReader(inputStream);
		StlFileParser parser = new StlFileParser(reader);
		
		try {
			parser.nextToken();
		} catch (IOException e) {
			throw new IOException("File seems to be empty. IO Error on line " + parser.lineno() + ": " + e.getMessage(), e);
		}
		
		// Here we try to detect what kind of file it is (see readSolid)
		readSolid(parser);
		parser.nextToken();

		// Read all the facets of the object
		while (parser.ttype != StlFileParser.TT_EOF && !parser.sval.equals("endsolid")) {
			readFacet(parser);
				parser.nextToken();
		}// End while

		// Why are we out of the while?: EOF or endsolid
		if (parser.ttype == StlFileParser.TT_EOF) {
			throw new IOException("Format Error:expecting 'endsolid', line " + parser.lineno());
		}
  }
  
  private boolean isASCIIFile(PushbackInputStream pushStream, int determinantSize) throws IOException {
		byte sampleSize[] = new byte[determinantSize];
		int bytesRead = pushStream.read(sampleSize);
		pushStream.unread(sampleSize);
		
		//If less than 80 bytes, that breaks the binary spec
		if (bytesRead < 80) {
			return true;
		}
		
		/*int faceBytes = ( 32 / 8 * 3 ) + ( ( 32 / 8 * 3 ) * 3 ) + ( 16 / 8 );
        ByteBuffer dataBuffer = ByteBuffer.wrap(stlHeader);
        dataBuffer.position(80);
        dataBuffer.order(ByteOrder.nativeOrder());    // Set the right order
        int expectedFaceCount = dataBuffer.getInt();
        int expectedByteCount = 80 + ( 32 / 8 ) + ( expectedFaceCount * faceBytes );
		if (expectedByteCount == filesize)*/
		
		for (int t = 0; t < sampleSize.length; t++) {
			int currentByte = sampleSize[t] & 0xFF;
			if (currentByte > 127) {
				return false;
			}
			
			if (currentByte == 10) {
				String facet = "facet";
				int letter = 0;
				for (; t < sampleSize.length; t++) {
					if ((sampleSize[t] > 8 && sampleSize[t] < 14) || sampleSize[t] == 32) {
						continue;
					}
					
					if (facet.charAt(letter) == (char)sampleSize[t]) {
						letter++;
						if (letter == facet.length()) {
							return true;
						}
						continue;
					}
					
					return false;
				}
			}
		}
		
		logger.warn("Falling back on using 'solid' identifier to determine ASCII/Binary stl file type");
		return new String(sampleSize, 0, 20).trim().toLowerCase().startsWith("solid");
  }
  /** Entry point for all STL file types */
  public void load(InputStream inputStream, boolean rewriteNormalsWithRightHandRule) throws IOException {
	this.rewriteNormalsWithRightHandRule = rewriteNormalsWithRightHandRule;
	int determinantSize = 2048;
	PushbackInputStream pushStream = new PushbackInputStream(inputStream, determinantSize);
	triangles = createSet();
	
	try {
		if (isASCIIFile(pushStream, determinantSize)) {
			readASCIIFile(pushStream);
		} else {
			readBinaryFile(pushStream);
		}
	} finally {
		pushStream.close();
	}
  }

  public boolean getAscii()
  {
    return this.Ascii;
  }

  public void setAscii(boolean tipo)
  {
    this.Ascii = tipo;
  }

  public String getObjectName()
  {
    return this.objectName;
  }

  public void setObjectName(String name)
  {
    this.objectName = name;
  }
	
	public Collection<T> getTriangles() {
		return triangles;
	}
	
	public double getZmin() {
		return zmin;
	}
	
	public double getZmax() {
		return zmax;
	}
	
	public double getXmin() {
		return xmin;
	}
	
	public double getXmax() {
		return xmax;
	}
	
	public double getYmin() {
		return ymin;
	}
	
	public double getYmax() {
		return ymax;
	}

	public double getWidth() {
		return xmax - xmin;
	}

	public double getHeight() {
		return ymax - ymin;
	}
} // End of package stl_loader