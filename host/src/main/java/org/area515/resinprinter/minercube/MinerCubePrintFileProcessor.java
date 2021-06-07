package org.area515.resinprinter.minercube;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.area515.resinprinter.display.InappropriateDeviceException;
import org.area515.resinprinter.job.AbstractPrintFileProcessor;
import org.area515.resinprinter.job.JobManagerException;
import org.area515.resinprinter.job.JobStatus;
import org.area515.resinprinter.job.PrintJob;
import org.area515.resinprinter.job.AbstractPrintFileProcessor.DataAid;
import org.area515.resinprinter.job.render.CurrentImageRenderer;
import org.area515.resinprinter.job.render.RenderingContext;
import org.area515.resinprinter.server.Main;

public class MinerCubePrintFileProcessor extends AbstractPrintFileProcessor<Object,Object> {
    private static final Logger logger = LogManager.getLogger();
	
    private class MinerDataAid extends DataAid {
		public Future<MinerCube> cube;

		public MinerDataAid(PrintJob printJob) throws JobManagerException {
			super(printJob);
		}
    }
    
	@Override
	public String[] getFileExtensions() {
		return new String[]{"cubemaze"};
	}

	@Override
	public boolean acceptsFile(File processingFile) {
		return processingFile.getName().toLowerCase().endsWith("cubemaze");
	}

	@Override
	public Double getBuildAreaMM(PrintJob printJob) {
		//TODO: haven't built any of this
		return null;
	}

	@Override
	public CurrentImageRenderer createRenderer(DataAid aid, Object imageIndexToBuild) {
		//Won't get called because we aren't using renders.
		return null;
	}

	@Override
	public JobStatus processFile(PrintJob printJob) throws Exception {
		boolean footerAttempted = false;
		DataAid dataAid = null;
		try {
			MinerDataAid data = (MinerDataAid)getDataAid(printJob);
			MinerCube cube = data.cube.get();
			
			//Everything needs to be setup in the dataByPrintJob before we start the header
			performHeader(data);
			
			cube.startPrint(data.xPixelsPerMM, data.yPixelsPerMM, data.sliceHeight);
			//TODO: need to set the total slices for a percentage complete: printJob.setTotalSlices();
	
			int centerX = data.xResolution / 2;
			int centerY = data.yResolution / 2;
	
			int firstSlices = data.inkConfiguration.getNumberOfFirstLayers();
			List<Rectangle> rects = cube.buildNextPrintSlice(centerX, centerY);
			RenderingContext renderedData = data.cache.getOrCreateIfMissing(Boolean.TRUE);

			while (cube.hasPrintSlice()) {
				data.startSlice();
				
				//Performs all of the duties that are common to most print files
				JobStatus status = performPreSlice(data, renderedData.getScriptEngine(), null);
				if (status != null) {
					return status;
				}
				
				BufferedImage image = data.printer.createBufferedImageFromGraphicsOutputInterface(data.xResolution, data.yResolution);
				Graphics2D graphics = (Graphics2D)image.getGraphics();
				graphics.setColor(Color.black);
				graphics.fillRect(0, 0, data.xResolution, data.yResolution);
				graphics.setColor(Color.white);
				for (Rectangle currentRect : rects) {
					graphics.fillRect(currentRect.x, currentRect.y, currentRect.width, currentRect.height);
				}

				image = applyImageTransforms(data, renderedData.getScriptEngine(), image);
				
				//Performs all of the duties that are common to most print files]
				renderedData.setPrintableImage(image);
				status = printImageAndPerformPostProcessing(data, renderedData.getScriptEngine(), image);
				if (status != null) {
					return status;
				}
	
				if (firstSlices > 0) {
					firstSlices--;
				} else {
					rects = cube.buildNextPrintSlice(centerX, centerY);
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
	public DataAid createDataAid(PrintJob printJob) throws JobManagerException {
		return new MinerDataAid(printJob);
	}

	@Override
	public void prepareEnvironment(File processingFile, PrintJob printJob) throws JobManagerException {
		JAXBContext jaxbContext;
		try {
			jaxbContext = JAXBContext.newInstance(MinerCube.class);
			Unmarshaller jaxbUnMarshaller = jaxbContext.createUnmarshaller();
			final MinerCube cube = (MinerCube)jaxbUnMarshaller.unmarshal(processingFile);
			Future<MinerCube> future = Main.GLOBAL_EXECUTOR.submit(new Callable<MinerCube>() {
				@Override
				public MinerCube call() throws Exception {
					cube.buildMaze();
					return cube;
				}
			});
			MinerDataAid aid = (MinerDataAid)initializeJobCacheWithDataAid(printJob);
			aid.cube = future;
		} catch (JAXBException e) {
			logger.error("Marshalling error while processing file:" + processingFile, e);
			throw new JobManagerException("I was expecting a MinerCube XML file. I don't understand this file.");
		} catch (InappropriateDeviceException e) {
			String error = "Couldn't initialize data aid for file:" + processingFile;
			logger.error(error, e);
			throw new JobManagerException(error);
		}
	}

	@Override
	public void cleanupEnvironment(File processingFile) throws JobManagerException {
		//Nothing to cleanup everything is done in memory.
	}

	@Override
	public Object getGeometry(PrintJob printJob) throws JobManagerException {
		throw new JobManagerException("You can't get geometry from this type of file");
	}

	@Override
	public Object getErrors(PrintJob printJob) throws JobManagerException {
		throw new JobManagerException("You can't get error geometry from this type of file");
	}

	@Override
	public String getFriendlyName() {
		return "Maze Cube";
	}

	@Override
	public boolean isThreeDimensionalGeometryAvailable() {
		return false;
	}
}
