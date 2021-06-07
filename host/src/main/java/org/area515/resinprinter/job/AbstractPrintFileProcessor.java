package org.area515.resinprinter.job;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import javax.script.Bindings;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import javax.xml.bind.JAXBException;

import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.area515.resinprinter.display.ControlFlow;
import org.area515.resinprinter.display.InappropriateDeviceException;
import org.area515.resinprinter.exception.NoPrinterFoundException;
import org.area515.resinprinter.exception.SliceHandlingException;
import org.area515.resinprinter.job.Customizer.PrinterStep;
import org.area515.resinprinter.job.render.CurrentImageRenderer;
import org.area515.resinprinter.job.render.RenderingCache;
import org.area515.resinprinter.job.render.RenderingContext;
import org.area515.resinprinter.notification.NotificationManager;
import org.area515.resinprinter.printer.Printer;
import org.area515.resinprinter.printer.PrinterConfiguration;
import org.area515.resinprinter.printer.SlicingProfile;
import org.area515.resinprinter.printer.SlicingProfile.InkConfig;
import org.area515.resinprinter.server.HostProperties;
import org.area515.resinprinter.server.Main;
import org.area515.resinprinter.services.CustomizerService;
import org.area515.resinprinter.slice.StlError;
import org.area515.util.Log4jUtil;
import org.area515.util.TemplateEngine;

public abstract class AbstractPrintFileProcessor<G,E> implements PrintFileProcessor<G,E>{
	private static final Logger logger = LogManager.getLogger();
	public static final String EXPOSURE_TIMER = "exposureTime";
	
	public static class DataAid {
		private Integer renderingSlice;
		public Printer printer;
		public PrintJob printJob;
		public PrinterConfiguration configuration;
		public SlicingProfile slicingProfile;
		public InkConfig inkConfiguration;
		public double xPixelsPerMM;
		public double yPixelsPerMM;
		public int xResolution;
		public int yResolution;
		public double sliceHeight;
		public InkDetector inkDetector;
		public long currentSliceTime;
		public Paint maskPaint;
		public boolean optimizeWithPreviewMode;
		private AffineTransform affineTransform;
		public RenderingCache cache = new RenderingCache();
		public Customizer customizer;
		public Customizer originalCustomizer;
		public CurrentImageRenderer currentlyRenderingImage;
		
		public DataAid(PrintJob printJob) throws JobManagerException {
			this.printJob = printJob;
			printer = printJob.getPrinter();
			printJob.setStartTime(System.currentTimeMillis());
		    configuration = printer.getConfiguration();
			slicingProfile = configuration.getSlicingProfile();
			inkConfiguration = slicingProfile.getSelectedInkConfig();
			xPixelsPerMM = slicingProfile.getDotsPermmX();
			yPixelsPerMM = slicingProfile.getDotsPermmY();
			xResolution = slicingProfile.getxResolution();
			yResolution = slicingProfile.getyResolution();
			optimizeWithPreviewMode = false;
			customizer = printJob.getCustomizer();
			
			if (customizer == null) {
				customizer = new Customizer();
				customizer.setNextStep(PrinterStep.PerformHeader);
				customizer.setNextSlice(0);
				customizer.setPrintableName(FilenameUtils.getBaseName(printJob.getJobFile().getName()));
				customizer.setPrintableExtension(FilenameUtils.getExtension(printJob.getJobFile().getName()));
				customizer.setPrinterName(printer.getName());
				customizer.setName(printJob.getJobFile().getName() + "." + printer.getName());
				Customizer otherCustomizer = CustomizerService.INSTANCE.getCustomizers().get(customizer.getName());
				if (otherCustomizer != null) {
					customizer.setName(customizer.getName() + "." + System.currentTimeMillis());
				}
				CustomizerService.INSTANCE.addOrUpdateCustomizer(customizer);
			}
			if (customizer.getNextStep() == null) {
				customizer.setNextStep(PrinterStep.PerformHeader);
			}
			if (customizer.getZScale() == null) {
				customizer.setZScale(1.0);
			}
			
			//We must make sure our customizer is perfectly setup at this point, everyone should be able to depend on our customizer after this setup process
			try {
				originalCustomizer = HostProperties.deepCopyJAXB(customizer, Customizer.class);
			} catch (JAXBException e) {
				throw new JobManagerException("Couldn't copy customizer", e);
			}
			
			//This file processor requires an ink configuration
			if (inkConfiguration == null) {
				throw new JobManagerException("Your printer doesn't have a selected ink configuration.");
			}
			
			//TODO: how do I integrate slicingProfile.getLiftDistance()
			sliceHeight = inkConfiguration.getSliceHeight();
		}
		
		//This puts the customizer back into the original state and saves it so that completed prints will start from the original location that the user requested.
		public void saveOriginalCustomizer() {
			CustomizerService.INSTANCE.addOrUpdateCustomizer(originalCustomizer);
		}
		
		public void clearAffineTransformCache() {
			affineTransform = null;
		}
		
		public AffineTransform getAffineTransform(ScriptEngine engine, BufferedImage buildPlatformImage, BufferedImage printImage) throws ScriptException {			
			if (customizer != null && customizer.getAffineTransformSettings() != null) {
				if (this.affineTransform == null || customizer.getAffineTransformSettings().getAffineTransformScriptCalculator() != null) {
					this.affineTransform = customizer.createAffineTransform(this, engine, buildPlatformImage, printImage);
				}
			} else {
				this.affineTransform = new AffineTransform();
				affineTransform.translate(xResolution/2, yResolution/2);
				affineTransform.translate(-printImage.getWidth()/2 , -printImage.getHeight()/2);
			}
			
			return this.affineTransform;
		}
		
		public int getRenderingSlice() {
			if (customizer == null) {
				return -1;
			}
			
			if (renderingSlice == null) {
				startSlice();
			}
			
			return renderingSlice;
		}
		
		public int startSlice(){
			if (renderingSlice == null) {
				if (customizer == null) {
					return -1;
				}
				
				renderingSlice = customizer.getNextSlice();
				return renderingSlice;
			}
			
			return renderingSlice++;
		}
		
		public int completeRenderingSlice() {
			int sliceJustRendered = customizer.getNextSlice();
			customizer.setNextSlice(sliceJustRendered + 1);
			customizer.setNextStep(PrinterStep.PerformPreSlice);
			CustomizerService.INSTANCE.addOrUpdateCustomizer(customizer);
			return sliceJustRendered;
		}
	}
	
	public Future<RenderingContext> startImageRendering(DataAid aid, Object imageIndexToBuild) {
		aid.currentlyRenderingImage = createRenderer(aid, imageIndexToBuild);
		aid.startSlice();
		if (aid.currentlyRenderingImage == null) {
			return null;
		}
		
		return Main.GLOBAL_EXECUTOR.submit(aid.currentlyRenderingImage);
	}
	
	
	public abstract CurrentImageRenderer createRenderer(DataAid aid, Object imageIndexToBuild);

	@Override
	public Double getBuildAreaMM(PrintJob printJob) {
		DataAid aid = getDataAid(printJob);
		if (aid == null) {
			return null;
		}
		
		Double area = aid.cache.getCurrentArea();
		if (area == null) {
			return null;
		}
		
		return aid.cache.getCurrentArea() / (aid.xPixelsPerMM * aid.yPixelsPerMM);
	}
	
	@Override
	public BufferedImage getCurrentImage(PrintJob printJob) {
		return getCurrentImageFromCache(printJob);
	}
	
	protected BufferedImage getCurrentImageFromCache(PrintJob printJob) {
		DataAid data = getDataAid(printJob);
		if (data == null) {
			return null;
		}
		
		ReentrantLock lock = data.cache.getCurrentLock();
		lock.lock();
		try {
			BufferedImage currentImage = data.cache.getCurrentImage();
			if (currentImage == null)
				return null;
			
			return currentImage.getSubimage(0, 0, currentImage.getWidth(), currentImage.getHeight());
		} finally {
			lock.unlock();
		}
	}
	
	public final DataAid initializeJobCacheWithDataAid(PrintJob printJob) throws InappropriateDeviceException, JobManagerException {
		DataAid aid = createDataAid(printJob);
		printJob.setDataAid(aid);
		
		//Notify the client that the printJob has changed the current slice from -1 to 1 and totalSlices are properly set now as well.
		NotificationManager.jobChanged(aid.printer, aid.printJob);
		return aid;
	}
	
	private void moveToNextPrinterStep(Customizer customizer, PrinterStep newState) {
		customizer.setNextStep(newState);
		CustomizerService.INSTANCE.addOrUpdateCustomizer(customizer);
	}
	
	public void performPauseGCode(DataAid aid) throws InappropriateDeviceException, IOException {
		if (aid == null) {
			throw new IllegalStateException("initializeDataAid must be called before this method");
		}
		
		//Perform the gcode associated with the printer pause function
		if (aid.slicingProfile.getgCodePause() != null && aid.slicingProfile.getgCodePause().trim().length() > 0) {
			aid.printer.getPrinterController().executeCommands(aid.printJob, aid.slicingProfile.getgCodePause(), true);
		}
	}
	
	public void performResumeGCode(DataAid aid) throws InappropriateDeviceException, IOException {
		if (aid == null) {
			throw new IllegalStateException("initializeDataAid must be called before this method");
		}
		
		//Perform the gcode associated with the printer resume function
		if (aid.slicingProfile.getgCodeResume() != null && aid.slicingProfile.getgCodeResume().trim().length() > 0) {
			aid.printer.getPrinterController().executeCommands(aid.printJob, aid.slicingProfile.getgCodeResume(), true);
		}
	}
	
	public void performHeader(DataAid aid) throws InappropriateDeviceException, IOException {
		if (aid == null) {
			throw new IllegalStateException("initializeDataAid must be called before this method");
		}
		
		if (aid.printer.isProjectorPowerControlSupported()) {
			aid.printer.setProjectorPowerStatus(true);
		}
		
		//Set the default exposure time(this is only used if there isn't an exposure time calculator)
		aid.printJob.setExposureTime(aid.inkConfiguration.getExposureTime());
		
		//Perform the gcode associated with the printer start function
		if (aid.slicingProfile.getgCodeHeader() != null && 
			aid.slicingProfile.getgCodeHeader().trim().length() > 0 &&
			aid.customizer.getNextStep() == PrinterStep.PerformHeader) {
			aid.printer.getPrinterController().executeCommands(aid.printJob, aid.slicingProfile.getgCodeHeader(), true);
			moveToNextPrinterStep(aid.customizer, PrinterStep.PerformPreSlice);
		}
		
		if (aid.inkConfiguration != null) {
			aid.inkDetector = aid.inkConfiguration.getInkDetector(aid.printJob);
		}
		
		//Set the initial values for all variables.
		aid.printJob.setExposureTime(aid.inkConfiguration.getExposureTime());
		aid.printJob.setZLiftDistance(aid.slicingProfile.getLiftFeedRate());
		aid.printJob.setZLiftSpeed(aid.slicingProfile.getLiftDistance());
		
		//Initialize bulb hours only once per print
		aid.printer.getBulbHours();
	}
	
	public JobStatus performPreSlice(DataAid aid, ScriptEngine engine, List<StlError> errors) throws InappropriateDeviceException {
		if (aid == null) {
			throw new IllegalStateException("initializeDataAid must be called before this method");
		}
		
		//Create exposure timers for new slice if they don't already exist
		Bindings binding = engine.getBindings(ScriptContext.ENGINE_SCOPE);
		if (!binding.containsKey("exposureTimers")) {
			ArrayList<?> timers = new ArrayList<>();
			binding.put("exposureTimers", timers);
		}

		//Start timer
		aid.currentSliceTime = System.currentTimeMillis();

		//Show the errors to our users if the stl file is broken, but we'll keep on processing like normal
		if (errors != null && !errors.isEmpty() && aid.customizer.getNextStep() == PrinterStep.PerformPreSlice) {
			NotificationManager.errorEncountered(aid.printJob, errors);
		}
		
		//Perform two actions at once here:
		// 1. Pause if the user asked us to pause
		// 2. Get out if the print is cancelled
		if (!aid.printer.waitForPauseIfRequired(this, aid)) {
			return aid.printer.getStatus();
		}

		//Execute preslice gcode
		if (aid.slicingProfile.getgCodePreslice() != null && 
			aid.slicingProfile.getgCodePreslice().trim().length() > 0 && 
			aid.customizer.getNextStep() == PrinterStep.PerformPreSlice) {
			aid.printer.getPrinterController().executeCommands(aid.printJob, aid.slicingProfile.getgCodePreslice(), true);
		}
		
		moveToNextPrinterStep(aid.customizer, PrinterStep.PerformExposure);
		return null;
	}
	
	private Future<?>[] startAllExposureTimers(final DataAid aid, ScriptEngine engine, final BufferedImage sliceImage) throws ScriptException {
		if (!(engine instanceof Invocable)) {
			throw new ScriptException("Script engine:" + engine + " not invocable, can't use exposureTimers.");
		}
		Invocable invocable = (Invocable)engine;
		Bindings bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);
		if (!bindings.containsKey("exposureTimers")) {
			return new Future[0];
		}
		
		Number timerValue = (Number)engine.eval("exposureTimers.length");
		logger.info("TimerCountLength:" + timerValue);
		if (timerValue == null) {
			return new Future[0];
		}
		
		int timerCount = timerValue.intValue();
		if (timerCount == 0) {
			return new Future[0];
		}
		
		int delay[] = new int[timerCount];
		final Object function[] = new Object[timerCount];
		final Object parameter[] = new Object[timerCount];
		for (int t = 0; t < timerCount; t++) {
			parameter[t] = engine.eval("exposureTimers[" + t + "].parameter");
			delay[t] = ((Number)engine.eval("exposureTimers[" + t + "].delayMillis")).intValue();
			function[t] = engine.eval("exposureTimers[" + t + "]");
		}
		
		bindings.put("buildPlatformImage", sliceImage);
		bindings.put("buildPlatformGraphics", sliceImage.getGraphics());
		bindings.put("buildPlatformRaster", sliceImage.getRaster());

		Future<?>[] futures = new Future[timerCount];
		for (int t = 0; t < timerCount; t++) {
			final int i = t;
			logger.info("Exposure timer[{}] will start in: {}ms", i, delay[t]);
			futures[i] = Main.GLOBAL_EXECUTOR.schedule(new Runnable() {
				@Override
				public void run() {
					try {
						logger.info("Exposure timer[{}] started:{}", i, invocable.invokeMethod(function[i], "function", parameter[i]));
						aid.printer.showImage(sliceImage, false);
						logger.info("Exposure timer[{}] complete", i);
					} catch (NoSuchMethodException e) {
						logger.error("Exposure timer function[" + i + "] not found", e);
					} catch (ScriptException e) {
						logger.error("Exposure timer[" + i + "] threw exception", e);
					}
				}}, delay[t], TimeUnit.MILLISECONDS);
		}
		return futures;
	}
	
	public JobStatus printImageAndPerformPostProcessing(DataAid aid, ScriptEngine engine, BufferedImage sliceImage) throws ExecutionException, InterruptedException, InappropriateDeviceException, ScriptException {
		if (aid == null) {
			throw new IllegalStateException("initializeDataAid must be called before this method");
		}

		if (engine == null) {
			throw new IllegalStateException("You must specify a script engine");
		}
		
		if (sliceImage == null) {
			throw new IllegalStateException("You must specify a sliceImage to display");
		}
		
		if (aid.customizer.getNextStep() != PrinterStep.PerformExposure) {
			return null;
		}
		
		//Start but don't wait for a potentially heavy weight operation to determine if we are out of ink.
		if (aid.inkDetector != null) {
			aid.inkDetector.startMeasurement();
		}
		
		//Determine the dynamic amount of time we should expose our resin
		if (!aid.printJob.isExposureTimeOverriden() && aid.slicingProfile.getExposureTimeCalculator() != null && aid.slicingProfile.getExposureTimeCalculator().trim().length() > 0) {
			Number value = calculate(aid, engine, aid.slicingProfile.getExposureTimeCalculator(), "exposure time script");
			if (value != null) {
				aid.printJob.setExposureTime(value.intValue());
			}
		}
		
		Future<?>[] timerFutures = startAllExposureTimers(aid, engine, sliceImage);
		
		aid.printer.showImage(sliceImage, true);
		logger.info("ExposureStart:{}", ()->Log4jUtil.startTimer(EXPOSURE_TIMER));
		 	
		if (aid.slicingProfile.getgCodeShutter() != null && aid.slicingProfile.getgCodeShutter().trim().length() > 0) {
			aid.printer.setShutterOpen(true);
			aid.printer.getPrinterController().executeCommands(aid.printJob, aid.slicingProfile.getgCodeShutter(), true);
		}
		
		//Sleep for the amount of time that we are exposing the resin.
		Thread.sleep(aid.printJob.getExposureTime());
		
		if (aid.slicingProfile.getgCodeShutter() != null && aid.slicingProfile.getgCodeShutter().trim().length() > 0) {
			aid.printer.setShutterOpen(false);
			aid.printer.getPrinterController().executeCommands(aid.printJob, aid.slicingProfile.getgCodeShutter(), false);
		}

		//Blank the screen
		aid.printer.showBlankImage();
		
		logger.info("ExposureTime:{}", ()->Log4jUtil.completeTimer(EXPOSURE_TIMER));
		
		//End all timers
		for (int t = 0; t < timerFutures.length; t++) {
			logger.info("Exposure timer[{}] cancel:{}", t, timerFutures[t].cancel(true));
		}
		
		//Reset exposureTimers to blank object
		Bindings binding = engine.getBindings(ScriptContext.ENGINE_SCOPE);
		ArrayList<?> timers = new ArrayList<>();
		binding.put("exposureTimers", timers);

		//Perform two actions at once here:
		// 1. Pause if the user asked us to pause
		// 2. Get out if the print is cancelled
		if (!aid.printer.waitForPauseIfRequired(this, aid)) {
			return aid.printer.getStatus();
		}
		
		if (!aid.printJob.isZLiftDistanceOverriden() && aid.slicingProfile.getzLiftDistanceCalculator() != null && aid.slicingProfile.getzLiftDistanceCalculator().trim().length() > 0) {
			Number value = calculate(aid, engine, aid.slicingProfile.getzLiftDistanceCalculator(), "lift distance script");
			if (value != null) {
				aid.printJob.setZLiftDistance(value.doubleValue());
			}
		}
		if (!aid.printJob.isZLiftSpeedOverriden() && aid.slicingProfile.getzLiftSpeedCalculator() != null && aid.slicingProfile.getzLiftSpeedCalculator().trim().length() > 0) {
			Number value = calculate(aid, engine, aid.slicingProfile.getzLiftSpeedCalculator(), "lift speed script");
			if (value != null) {
				aid.printJob.setZLiftSpeed(value.doubleValue());
			}
		}
		if (aid.slicingProfile.getZLiftDistanceGCode() != null && aid.slicingProfile.getZLiftDistanceGCode().trim().length() > 0) {
			aid.printer.getPrinterController().executeCommands(aid.printJob, aid.slicingProfile.getZLiftDistanceGCode(), true);
		}
		if (aid.slicingProfile.getZLiftSpeedGCode() != null && aid.slicingProfile.getZLiftSpeedGCode().trim().length() > 0) {
			aid.printer.getPrinterController().executeCommands(aid.printJob, aid.slicingProfile.getZLiftSpeedGCode(), true);
		}
		
		//Perform the lift gcode manipulation
		aid.printer.getPrinterController().executeCommands(aid.printJob, aid.slicingProfile.getgCodeLift(), true);
		
		Double buildArea = getBuildAreaMM(aid.printJob);
		// Log slice settings (in JSON for extraction and processing)
		logger.info("{ \"layer\": {}, \"exposureTime\": {}, \"liftDistance\": {}, \"liftSpeed\": {} , \"layerAreaMM2\": {} }",
			aid.printJob.getCurrentSlice(), aid.printJob.getExposureTime(), aid.printJob.getZLiftDistance(),
			aid.printJob.getZLiftSpeed(), buildArea);
		
		//Perform area and cost manipulations for current slice
		aid.printJob.completeRenderingSlice(System.currentTimeMillis() - aid.currentSliceTime, buildArea);
		
		//Notify the client that the printJob has increased the currentSlice
		NotificationManager.jobChanged(aid.printer, aid.printJob);
		
		moveToNextPrinterStep(aid.customizer, PrinterStep.PerformPreSlice);
		
		return null;
	}

	public JobStatus performFooter(DataAid aid) throws IOException, InappropriateDeviceException {
		logger.info("gCode footer started");
		if (aid == null) {
			throw new IllegalStateException("initializeDataAid must be called before this method");
		}
		logger.info("Data aid initialized in footer");

		if (!(aid.configuration.getMachineConfig().getFooterExecutionHandling() == ControlFlow.Always ||
			(aid.printer.isPrintActive() && aid.configuration.getMachineConfig().getFooterExecutionHandling() == ControlFlow.OnSuccess) ||
			(aid.printer.isPrintInProgress() && aid.configuration.getMachineConfig().getFooterExecutionHandling() == ControlFlow.OnSuccessAndCancellation))) {
			logger.info("Didn't perform footer because handling was:" + aid.configuration.getMachineConfig().getFooterExecutionHandling()  + " and status is:" + aid.printer.getStatus());
			return aid.printer.getStatus();
		}
		
		if (aid.slicingProfile.getgCodeFooter() != null && aid.slicingProfile.getgCodeFooter().trim().length() > 0) {
			aid.printer.getPrinterController().executeCommands(aid.printJob, aid.slicingProfile.getgCodeFooter(), false);
		} else {
			logger.info("gCodeFooter was: '" + aid.slicingProfile.getgCodeFooter() + "'");
		}
		
		if (aid.printer.isProjectorPowerControlSupported()) {
			aid.printer.setProjectorPowerStatus(false);
		}

		aid.saveOriginalCustomizer();
		
		return JobStatus.Completed;
	}

	private Number calculate(DataAid aid, ScriptEngine engine, String calculator, String calculationName) throws ScriptException {
		try {
			Number num = (Number)TemplateEngine.runScript(aid.printJob, aid.printer, engine, calculator, calculationName, null);
			if (num == null || Double.isNaN(num.doubleValue())) {
				return null;
			}
			return num;
		} catch (ClassCastException e) {
			throw new IllegalArgumentException("The result of your " + calculationName + " needs to evaluate to an instance of java.lang.Number");
		}
	}

	public void applyBulbMask(DataAid aid, ScriptEngine engine, Graphics2D g2, int width, int height) throws ScriptException {
		if (aid == null) {
			throw new IllegalStateException("initializeDataAid must be called before this method");
		}
		
		if (aid.slicingProfile.getProjectorGradientCalculator() == null || aid.slicingProfile.getProjectorGradientCalculator().trim().length() == 0) {
			return;
		}
		
		if (!aid.configuration.getMachineConfig().getMonitorDriverConfig().isUseMask()) {
			return;
		}
		
		try {
			if (aid.maskPaint == null) {
				aid.maskPaint = (Paint)TemplateEngine.runScript(aid.printJob, aid.printer, engine, aid.slicingProfile.getProjectorGradientCalculator(), "projector gradient script", null);
			}
			g2.setPaint(aid.maskPaint);
			g2.fillRect(0, 0, width, height);
		} catch (ClassCastException e) {
			throw new IllegalArgumentException("The result of your bulb mask script needs to evaluate to an instance of java.awt.Paint");
		}
	}

	public BufferedImage applyImageTransforms(DataAid aid, ScriptEngine engineForManipulation, BufferedImage imageToRender) throws ScriptException, JobManagerException {
		if (aid == null) {
			throw new IllegalStateException("initializeDataAid must be called before this method");
		}
		if (imageToRender == null) {
			throw new IllegalStateException("BufferedImage is null");
		}

		if (aid.optimizeWithPreviewMode) {
			return imageToRender;
		}
		
		logger.trace("Writing applyImageTransforms1Begin:{}", () -> Log4jUtil.logImage(imageToRender, "applyImageTransforms1Begin.png"));

		BufferedImage imageToRenderAfterTransformations = aid.printer.createBufferedImageFromGraphicsOutputInterface(aid.xResolution, aid.yResolution);
		Graphics2D graphicsAfterTransformations = (Graphics2D)imageToRenderAfterTransformations.getGraphics();
		graphicsAfterTransformations.setColor(Color.BLACK);
		graphicsAfterTransformations.fillRect(0, 0, aid.xResolution, aid.yResolution);
		
		logger.trace("Writing applyImageTransforms2AfterFill:{}", () -> Log4jUtil.logImage(imageToRenderAfterTransformations, "applyImageTransforms2AfterFill.png"));
		
		AffineTransform transform = aid.getAffineTransform(engineForManipulation, imageToRenderAfterTransformations, imageToRender);
		graphicsAfterTransformations.drawImage(imageToRender, transform, null);
	
		logger.trace("Writing applyImageTransforms3AfterDraw:{}", () -> Log4jUtil.logImage(imageToRenderAfterTransformations, "applyImageTransforms3AfterDraw.png"));

		if (aid.customizer.getImageManipulationCalculator() != null && aid.customizer.getImageManipulationCalculator().trim().length() > 0) {
			Map<String, Object> overrides = new HashMap<>();
			overrides.put("affineTransform", transform);
			TemplateEngine.runScriptInImagingContext(imageToRenderAfterTransformations, imageToRender, aid.printJob, aid.printer, engineForManipulation, overrides, aid.customizer.getImageManipulationCalculator(), "Image manipulation script", false);
		}

		logger.trace("Writing applyImageTransforms4AfterImageManipulation:{}", () -> Log4jUtil.logImage(imageToRenderAfterTransformations, "applyImageTransforms4AfterImageManipulation.png"));

		//TODO: I was using imageToRenderAfterTransformations.getGraphics() but recently changed to graphicsAfterTransformations
		applyBulbMask(aid, engineForManipulation, graphicsAfterTransformations, aid.xResolution, aid.yResolution);

		logger.trace("Writing applyImageTransforms5AfterBulbMask:{}", () -> Log4jUtil.logImage(imageToRenderAfterTransformations, "applyImageTransforms5AfterBulbMask.png"));
		return imageToRenderAfterTransformations;
	}
	
	public BufferedImage buildPreviewSlice(Customizer customizer, DataAid dataAid) throws NoPrinterFoundException, SliceHandlingException {
		try {
			RenderingContext data = dataAid.cache.getOrCreateIfMissing(customizer);
			BufferedImage preImage = data.getPreTransformedImage();
			if (preImage == null) {
				dataAid.optimizeWithPreviewMode = true;
				preImage = ((Previewable)this).renderPreviewImage(dataAid);
				dataAid.optimizeWithPreviewMode = false;
				data.setPreTransformedImage(preImage);
			}
			
			preImage = applyImageTransforms(dataAid, data.getScriptEngine(), preImage);
			return preImage;
		} catch (ScriptException | JobManagerException e) {
			throw new SliceHandlingException(e);
		}
	}
	
	public DataAid createDataAid(PrintJob printJob) throws JobManagerException {
		return new DataAid(printJob);
	}

	public DataAid getDataAid(PrintJob job) {
		if (job == null) {
			return null;
		}
		
		return job.getDataAid();
	}
	
	//TODO: Not sure this is necessary. What if we just killed the rendering cache
	public void clearDataAid(PrintJob job) {
		if (job == null) {
			return;
		}
	
		job.setDataAid(null);
	}
}
