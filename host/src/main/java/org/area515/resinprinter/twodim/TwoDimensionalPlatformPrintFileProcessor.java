package org.area515.resinprinter.twodim;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.Future;

import javax.imageio.ImageIO;
import javax.xml.bind.annotation.XmlTransient;

import org.area515.resinprinter.display.InappropriateDeviceException;
import org.area515.resinprinter.exception.SliceHandlingException;
import org.area515.resinprinter.job.AbstractPrintFileProcessor;
import org.area515.resinprinter.job.JobManagerException;
import org.area515.resinprinter.job.JobStatus;
import org.area515.resinprinter.job.Previewable;
import org.area515.resinprinter.job.PrintJob;
import org.area515.resinprinter.job.render.CurrentImageRenderer;
import org.area515.resinprinter.job.render.RenderingContext;
import org.area515.resinprinter.job.render.RenderingCache;
import org.area515.resinprinter.printer.SlicingProfile.TwoDimensionalSettings;
import org.area515.resinprinter.services.PrintJobService;

public abstract class TwoDimensionalPlatformPrintFileProcessor<T,E> extends AbstractPrintFileProcessor<T,E> implements Previewable {
    public class TwoDimensionalDataAid extends DataAid {
    	public int totalPlatformSlices;
    	public int totalExtrusionSlices;
    	public int platformSlices;
    	public int extrusionSlices;
    	public TwoDimensionalImageRenderer platformSizeInitializer;
    	
    	public TwoDimensionalDataAid(PrintJob printJob) throws JobManagerException {
    		super(printJob);
    	}
    }
    
	@Override
	public void prepareEnvironment(final File processingFile, final PrintJob printJob) throws JobManagerException {
		DataAid aid;
		try {
			aid = initializeJobCacheWithDataAid(printJob);
			createTwoDimensionalRenderer(aid, Boolean.TRUE);
		} catch (InappropriateDeviceException e) {
			throw new JobManagerException("Couldn't create job", e);
		}
	}
	
	@Override
	public DataAid createDataAid(PrintJob printJob) throws JobManagerException {
		return new TwoDimensionalDataAid(printJob);
	}

	protected CurrentImageRenderer buildPlatformRenderer(DataAid dataAid, Object nextRenderingPointer, int totalPlatformSlices, CurrentImageRenderer platformSizeInitializer) {
		return new PlatformImageRenderer(dataAid, this, nextRenderingPointer, totalPlatformSlices, platformSizeInitializer);
	}
	
	public abstract TwoDimensionalImageRenderer createTwoDimensionalRenderer(DataAid aid, Object imageIndexToBuild);
	
	@Override
	public final CurrentImageRenderer createRenderer(DataAid aid, Object imageIndexToBuild) {
		TwoDimensionalDataAid dataAid = (TwoDimensionalDataAid)aid;
		if (dataAid.platformSlices <= 0 && dataAid.extrusionSlices <= 0) {
			return null;
		}
		
		if (dataAid.platformSizeInitializer == null) {
			dataAid.platformSizeInitializer = createTwoDimensionalRenderer(dataAid, imageIndexToBuild);
		}

		if (dataAid.platformSlices > 0) {
			dataAid.platformSlices--;
			return buildPlatformRenderer(dataAid, imageIndexToBuild, dataAid.totalPlatformSlices, dataAid.platformSizeInitializer);
		}
		
		//Clear cache so that the text will render for the first time and not use the build platform cache
		if (dataAid.extrusionSlices == dataAid.totalExtrusionSlices) {
			dataAid.cache.clearCache(Boolean.TRUE);
			dataAid.cache.clearCache(Boolean.FALSE);
		}
		
		dataAid.extrusionSlices--;
		return createTwoDimensionalRenderer(dataAid, imageIndexToBuild);
	}

	protected void setupSlices(PrintJob printJob, TwoDimensionalDataAid dataAid, int suggestedPlatformSlices, int suggestedExtrusionSlices) {
		int startingSlice = dataAid.customizer.getNextSlice();
		dataAid.platformSlices = suggestedPlatformSlices;
		dataAid.extrusionSlices = suggestedExtrusionSlices;
		dataAid.totalPlatformSlices = dataAid.platformSlices;
		dataAid.totalExtrusionSlices = dataAid.extrusionSlices;
		
		if (startingSlice > dataAid.platformSlices) {
			startingSlice -= dataAid.platformSlices;
			dataAid.platformSlices = 0;
		} else {
			dataAid.platformSlices -= startingSlice;
			startingSlice = 0;
		}
		
		if (startingSlice > dataAid.extrusionSlices) {
			startingSlice -= dataAid.extrusionSlices;
			dataAid.extrusionSlices = 0;
		} else {
			dataAid.extrusionSlices -= startingSlice;
			startingSlice = 0;
		}
		
		//Total slices must be set to the actual total number of slices that are in the model
		printJob.setTotalSlices(dataAid.totalPlatformSlices + dataAid.totalExtrusionSlices);
	}
	
	@Override
	public JobStatus processFile(PrintJob printJob) throws Exception {
		TwoDimensionalDataAid dataAid = (TwoDimensionalDataAid)getDataAid(printJob);
		boolean footerAttempted = false;
		try {
			performHeader(dataAid);
			setupSlices(printJob, dataAid, getSuggestedPlatformLayerCount(dataAid), getSuggested2DExtrusionLayerCount(dataAid));
			RenderingCache printState = dataAid.cache;
			Boolean nextRenderingPointer = (Boolean)printState.getCurrentRenderingPointer();
			Future<RenderingContext> currentImage = startImageRendering(dataAid, nextRenderingPointer);		
			while (currentImage != null) {
				
				//Performs all of the duties that are common to most print files
				JobStatus status = performPreSlice(dataAid, dataAid.currentlyRenderingImage.getScriptEngine(), null);
				if (status != null) {
					return status;
				}
				
				//Wait until the image has been properly rendered. Most likely, it's already done though...
				RenderingContext rendered = currentImage.get();
				
				//Now that the image has been rendered, we can make the switch to use the pointer that we were using while we were rendering
				printState.setCurrentRenderingPointer(nextRenderingPointer);
				
				//Get the next pointer in line to start rendering the next image into
				nextRenderingPointer = !nextRenderingPointer;
				
				//Start to render the next image while we are waiting for the current image to cure
				currentImage = startImageRendering(dataAid, nextRenderingPointer);
				
				//Performs all of the duties that are common to most print files
				status = printImageAndPerformPostProcessing(dataAid, rendered.getScriptEngine(), rendered.getPrintableImage());
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
	
	@XmlTransient
	public int getSuggestedPlatformLayerCount(DataAid aid) {
		int fallbackAmount = aid.inkConfiguration.getNumberOfFirstLayers() * 2;
		TwoDimensionalSettings settings = aid.slicingProfile.getTwoDimensionalSettings();
		if (settings == null) {
			return fallbackAmount;
		}
		Double platformHeight = settings.getPlatformHeightMM();
		if (platformHeight == null) {
			return fallbackAmount;
		}
		
		return (int)Math.round(platformHeight / aid.inkConfiguration.getSliceHeight());
	}
	
	@XmlTransient
	public int getSuggested2DExtrusionLayerCount(DataAid aid) {
		int fallbackAmount = aid.inkConfiguration.getNumberOfFirstLayers() * 3;
		TwoDimensionalSettings settings = aid.slicingProfile.getTwoDimensionalSettings();
		if (settings == null) {
			return fallbackAmount;
		}
		Double extrusionHeight = settings.getExtrusionHeightMM();
		if (extrusionHeight == null) {
			return fallbackAmount;
		}
		
		return (int)Math.round(extrusionHeight / aid.inkConfiguration.getSliceHeight());
	}
	
	@Override
	public BufferedImage renderPreviewImage(final DataAid aid) throws SliceHandlingException {
		try {
			TwoDimensionalDataAid dataAid = (TwoDimensionalDataAid)aid;
			setupSlices(aid.printJob, dataAid, getSuggestedPlatformLayerCount(dataAid), getSuggested2DExtrusionLayerCount(dataAid));
			CurrentImageRenderer renderer = createRenderer(dataAid, Boolean.TRUE);
			if (renderer == null) {
				return ImageIO.read(PrintJobService.class.getResourceAsStream("noimageavailable.png"));
			}
			
			return renderer.call().getPrintableImage();
		} catch (IOException | JobManagerException e) {
			throw new SliceHandlingException(e);
		}
	}
}
