package org.area515.resinprinter.job;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.Future;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.area515.resinprinter.exception.SliceHandlingException;
import org.area515.resinprinter.job.render.CurrentImageRenderer;
import org.area515.resinprinter.job.render.RenderingContext;
import org.area515.resinprinter.printer.BuildDirection;
import org.area515.resinprinter.slice.CloseOffMend;
import org.area515.resinprinter.slice.StlError;
import org.area515.resinprinter.slice.ZSlicer;
import org.area515.resinprinter.stl.Triangle3d;
import org.area515.util.Log4jUtil;

public class STLFileProcessor extends AbstractPrintFileProcessor<Iterator<Triangle3d>, Set<StlError>> implements Previewable {
	public static String STL_OVERHEAD = "stlOverhead";
	public static final String TOO_LARGE = "This file is too large for Photonic3D to load:";

	private static final Logger logger = LogManager.getLogger();

	@Override
	public String[] getFileExtensions() {
		return new String[]{"stl"};
	}

	@Override
	public CurrentImageRenderer createRenderer(DataAid aid, Object imageIndexToBuild) {
		return new STLImageRenderer(aid, this, imageIndexToBuild, false);
	}

	@Override
	public boolean acceptsFile(File processingFile) {
		return processingFile.getName().toLowerCase().endsWith("stl");
	}
	
	@Override
	public DataAid createDataAid(PrintJob printJob) throws JobManagerException {
		return new STLDataAid(printJob);
	}

	@Override
	public JobStatus processFile(PrintJob printJob) throws Exception {
		JobStatus status = printJob.getStatus();
		STLDataAid dataAid = null;
		boolean footerAttempted = false;
		try {
			dataAid = (STLDataAid)initializeJobCacheWithDataAid(printJob);
			boolean overrideNormals = dataAid.configuration.getMachineConfig().getOverrideModelNormalsWithRightHandRule() == null?false:dataAid.configuration.getMachineConfig().getOverrideModelNormalsWithRightHandRule();
			ZSlicer slicer = new ZSlicer(dataAid.customizer.getZScale(), 
					dataAid.xPixelsPerMM / dataAid.customizer.getZScale(), 
					dataAid.yPixelsPerMM / dataAid.customizer.getZScale(), 
					dataAid.sliceHeight, 
					dataAid.sliceHeight / 2, 
					true, 
					overrideNormals,
					new CloseOffMend());
			dataAid.slicer = slicer;
			dataAid.slicer.loadFile(new FileInputStream(printJob.getJobFile()), null, null);
			printJob.setTotalSlices(slicer.getZMaxIndex() - slicer.getZMinIndex());
			
			//Get the slicer queued up for the first image;
			int startPoint = dataAid.slicingProfile.getDirection() == BuildDirection.Bottom_Up?(slicer.getZMinIndex() + 1 + dataAid.customizer.getNextSlice()): (slicer.getZMaxIndex() + 1);
			int endPoint = dataAid.slicingProfile.getDirection() == BuildDirection.Bottom_Up?(slicer.getZMaxIndex() + 1 - dataAid.customizer.getNextSlice()): (slicer.getZMinIndex() + 1);
			dataAid.slicer.setZIndex(startPoint);
			Boolean nextRenderingPointer = (Boolean)dataAid.cache.getCurrentRenderingPointer();
			Future<RenderingContext> currentImage = startImageRendering(dataAid, nextRenderingPointer);
			//renderingImage
			//Everything needs to be setup in the dataByPrintJob before we start the header
			performHeader(dataAid);
			for (int z = startPoint; dataAid.slicingProfile.getDirection().isSliceAvailable(z, endPoint) && dataAid.printer.isPrintActive(); z += dataAid.slicingProfile.getDirection().getVector()) {
				
				//Performs all of the duties that are common to most print files
				status = performPreSlice(dataAid, dataAid.currentlyRenderingImage.getScriptEngine(), slicer.getStlErrors());
				if (status != null) {
					return status;
				}
				
				logger.info("SliceOverheadStart:{}", ()->Log4jUtil.startTimer(STL_OVERHEAD));
				
				//Wait until the image has been properly rendered. Most likely, it's already done though...
				RenderingContext renderedData = currentImage.get();
				
				logger.info("SliceOverhead:{}", ()->Log4jUtil.completeTimer(STL_OVERHEAD));
				
				//Now that the image has been rendered, we can make the switch to use the pointer that we were using while we were rendering
				dataAid.cache.setCurrentRenderingPointer(nextRenderingPointer);
				
				//Get the next pointer in line to start rendering the image into
				nextRenderingPointer = !nextRenderingPointer;
				
				//Render the next image while we are waiting for the current image to cure
				if (z < slicer.getZMaxIndex() + 1) {
					slicer.setZIndex(z);
					currentImage = startImageRendering(dataAid, nextRenderingPointer);
				}
				
				//Performs all of the duties that are common to most print files
				status = printImageAndPerformPostProcessing(dataAid, renderedData.getScriptEngine(), renderedData.getPrintableImage());
				if (status != null) {
					return status;
				}
			}
			
			try {
				return performFooter(dataAid);
			} finally {
				footerAttempted = true;
			}
		} finally {
			try {
				if (!footerAttempted && dataAid != null) {
					performFooter(dataAid);
				}
			} finally {
				clearDataAid(printJob);
			}
		}
	}
	
	@Override
	public BufferedImage renderPreviewImage(DataAid aid) throws SliceHandlingException {
		try {
			STLDataAid dataAid = (STLDataAid)aid;
			boolean overrideNormals = dataAid.configuration.getMachineConfig().getOverrideModelNormalsWithRightHandRule() == null?false:dataAid.configuration.getMachineConfig().getOverrideModelNormalsWithRightHandRule();
			//dataAid.slicer = new ZSlicer(1, dataAid.xPixelsPerMM, dataAid.yPixelsPerMM, dataAid.sliceHeight, dataAid.sliceHeight / 2, true, overrideNormals, new CloseOffMend());
			dataAid.slicer = new ZSlicer(aid.customizer.getZScale(), 
					dataAid.xPixelsPerMM / aid.customizer.getZScale(), 
					dataAid.yPixelsPerMM / aid.customizer.getZScale(), 
					dataAid.sliceHeight, 
					dataAid.sliceHeight / 2, 
					true, 
					overrideNormals,
					new CloseOffMend());
			dataAid.slicer.loadFile(new FileInputStream(dataAid.printJob.getJobFile()), null, null);
			dataAid.printJob.setTotalSlices(dataAid.slicer.getZMaxIndex() - dataAid.slicer.getZMinIndex());
			//Get the slicer queued up for the first image;
			dataAid.slicer.setZIndex(dataAid.slicer.getZMinIndex() + dataAid.customizer.getNextSlice());
			Object nextRenderingPointer = dataAid.cache.getCurrentRenderingPointer();
			STLImageRenderer renderer = new STLImageRenderer(dataAid, this, nextRenderingPointer, true);
			return renderer.call().getPrintableImage();
		} catch (IOException | JobManagerException e) {
			throw new SliceHandlingException(e);
		} catch (OutOfMemoryError e) {
			throw new SliceHandlingException(TOO_LARGE + aid.printJob.getJobFile(), e);
		}
	}

	@Override
	public void prepareEnvironment(File processingFile, PrintJob printJob) throws JobManagerException {
	}

	@Override
	public void cleanupEnvironment(File processingFile) throws JobManagerException {
	}

	@Override
	public Iterator<Triangle3d> getGeometry(PrintJob printJob) throws JobManagerException {
		final STLDataAid data = (STLDataAid)getDataAid(printJob);
		if (data == null) {
			return null;
		}
		
		return new Iterator<Triangle3d>() {
			private Triangle3d nextTriangle;
			{
				nextTriangle = data.slicer.getFirstTriangle();
			}
			
			@Override
			public boolean hasNext() {
				return nextTriangle != null;
			}

			@Override
			public Triangle3d next() {
				Triangle3d t = nextTriangle;
				nextTriangle = nextTriangle.getNextTriangle();
				return t;
			}
		};
	}

	@Override
	public String getFriendlyName() {
		return "STL 3D Model";
	}

	@Override
	public Set<StlError> getErrors(PrintJob printJob) throws JobManagerException {
		STLDataAid data = (STLDataAid)getDataAid(printJob);
		if (data == null) {
			return null;
		}
		
		return new HashSet<>(data.slicer.getStlErrors());
	}

	@Override
	public boolean isThreeDimensionalGeometryAvailable() {
		return true;
	}
}
