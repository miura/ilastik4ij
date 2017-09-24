/**
 * MIT License
 * 
 * Copyright (c) 2017 ilastik
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * 
 * Author: Carsten Haubold
 */

package org.ilastik.ilastik4ij;

import java.io.File;
import java.io.IOException;

import org.scijava.log.LogService;

import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;
import ch.systemsx.cisd.base.mdarray.MDByteArray;
import ch.systemsx.cisd.base.mdarray.MDFloatArray;
import ch.systemsx.cisd.base.mdarray.MDShortArray;
import ch.systemsx.cisd.hdf5.HDF5Factory;
import ch.systemsx.cisd.hdf5.IHDF5Reader;
import ch.systemsx.cisd.hdf5.hdf5lib.HDF5Constants;

import static ch.systemsx.cisd.hdf5.hdf5lib.H5P.H5Pset_chunk;
import static ch.systemsx.cisd.hdf5.hdf5lib.H5P.H5Pclose;
import static ch.systemsx.cisd.hdf5.hdf5lib.H5P.H5Pcreate;
import static ch.systemsx.cisd.hdf5.hdf5lib.H5P.H5Pset_deflate;
import static ch.systemsx.cisd.hdf5.hdf5lib.HDF5Constants.H5P_DATASET_CREATE;
import static ch.systemsx.cisd.hdf5.hdf5lib.H5S.H5Screate_simple;
import static ch.systemsx.cisd.hdf5.hdf5lib.H5S.H5Sclose;
import static ch.systemsx.cisd.hdf5.hdf5lib.H5D.H5Dcreate;
import static ch.systemsx.cisd.hdf5.hdf5lib.H5D.H5Dclose;
import static ch.systemsx.cisd.hdf5.hdf5lib.H5F.H5Fcreate;
import static ch.systemsx.cisd.hdf5.hdf5lib.H5F.H5Fclose;
import static ch.systemsx.cisd.hdf5.hdf5lib.H5D.H5Dwrite;
import static ch.systemsx.cisd.hdf5.hdf5lib.H5A.H5Acreate;
import static ch.systemsx.cisd.hdf5.hdf5lib.H5A.H5Awrite;
import static ch.systemsx.cisd.hdf5.hdf5lib.H5A.H5Aclose;
import static ch.systemsx.cisd.hdf5.hdf5lib.H5D.H5Dset_extent;
import static ch.systemsx.cisd.hdf5.hdf5lib.H5D.H5Dget_space;
import static ch.systemsx.cisd.hdf5.hdf5lib.H5S.H5Sselect_all;
import static ch.systemsx.cisd.hdf5.hdf5lib.H5S.H5Sselect_hyperslab;
import static ch.systemsx.cisd.hdf5.hdf5lib.H5S.H5Sget_simple_extent_dims;
import static ch.systemsx.cisd.hdf5.hdf5lib.HDF5Constants.H5F_ACC_TRUNC;
import static ch.systemsx.cisd.hdf5.hdf5lib.HDF5Constants.H5P_DEFAULT;
import static ch.systemsx.cisd.hdf5.hdf5lib.HDF5Constants.H5S_ALL;
import static ch.systemsx.cisd.hdf5.hdf5lib.HDF5Constants.H5T_NATIVE_UINT8;
import static ch.systemsx.cisd.hdf5.hdf5lib.HDF5Constants.H5T_NATIVE_UINT16;
import static ch.systemsx.cisd.hdf5.hdf5lib.HDF5Constants.H5T_NATIVE_FLOAT;
import static ch.systemsx.cisd.hdf5.hdf5lib.HDF5Constants.H5T_STD_I8BE;
import static ch.systemsx.cisd.hdf5.hdf5lib.HDF5Constants.H5T_STRING;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;

public class IlastikUtilities {
	/**
	 * Utility method to obtain a unique filename
	 * @param extension
	 * @return
	 * @throws IOException
	 */
	public static String getTemporaryFileName(String extension) throws IOException
	{
		File f = File.createTempFile("ilastik4j", extension, null);
		String filename = f.getAbsolutePath();
		f.delete();
		return filename;
	}
	
	public static ImagePlus readFloatHdf5VolumeIntoImage(String filename, String dataset, String axesorder)
	{
		IHDF5Reader reader = HDF5Factory.openForReading(filename);
		Hdf5DataSetConfig dsConfig = new Hdf5DataSetConfig(reader, dataset, axesorder);
		if(!dsConfig.typeInfo.equals("float32"))
		{
			throw new IllegalArgumentException("Dataset is not of float datatype!");
		}

		MDFloatArray rawdata = reader.float32().readMDArray(dataset);
		float[] flat_data = rawdata.getAsFlatArray();
		float maxGray = 1;

		ImagePlus image = IJ.createHyperStack(dataset, dsConfig.dimX, dsConfig.dimY, dsConfig.numChannels, dsConfig.dimZ, dsConfig.numFrames, 32);

		// TODO: make this work with hyperslabs instead of reading the full HDF5 volume into memory at once!
		for (int frame = 0; frame < dsConfig.numFrames; ++frame)
		{
			for( int lev = 0; lev < dsConfig.dimZ; ++lev)
			{
				for (int c = 0; c < dsConfig.numChannels; ++c)
				{
					ImageProcessor ip = image.getStack().getProcessor( image.getStackIndex(c +1, lev+1, frame+1));
					float[] destData = (float[])ip.getPixels();

					for (int x = 0; x < dsConfig.dimX; x++)
					{
						for (int y = 0; y < dsConfig.dimY; y++)
						{
							int scrIndex = c + lev * dsConfig.numChannels 
											 + y * dsConfig.dimZ * dsConfig.numChannels
											 + x * dsConfig.dimZ * dsConfig.dimY * dsConfig.numChannels 
											 + frame * dsConfig.dimZ * dsConfig.dimX * dsConfig.dimY * dsConfig.numChannels ;
							int destIndex = y*dsConfig.dimX + x;
							destData[destIndex] = flat_data[scrIndex];
						}
					}
				}
			}
		}

		// find largest value to automatically adjust the display range
		for (int i = 0; i < flat_data.length; ++i) {
			if (flat_data[i] > maxGray) maxGray = flat_data[i];
		}

		// configure options of image
		for( int c = 1; c <= dsConfig.numChannels; ++c)
		{
			image.setC(c);
			image.setDisplayRange(0, maxGray);
		}

		return image;
	}
}