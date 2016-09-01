package org.area515.resinprinter.job;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;

import javax.script.ScriptEngine;
import javax.script.ScriptException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.area515.resinprinter.display.InappropriateDeviceException;
import org.area515.resinprinter.exception.NoPrinterFoundException;
import org.area515.resinprinter.exception.SliceHandlingException;
import org.area515.resinprinter.notification.NotificationManager;
import org.area515.resinprinter.printer.Printer;
import org.area515.resinprinter.printer.PrinterConfiguration;
import org.area515.resinprinter.printer.SlicingProfile;
import org.area515.resinprinter.printer.SlicingProfile.InkConfig;
import org.area515.resinprinter.server.HostProperties;
import org.area515.resinprinter.services.PrinterService;
import org.area515.resinprinter.slice.StlError;
import org.area515.util.Log4jTimer;
import org.area515.util.TemplateEngine;

public abstract class AbstractPrintFileProcessor<G,E> implements PrintFileProcessor<G,E>{
	private static final Logger logger = LogManager.getLogger();
	public static final String EXPOSURE_TIMER = "exposureTime";
	//TODO: Instead of having each implementation keep it's own state in it's own hashtable, we should be doing all of that work in here...
	
	public static class DataAid {
		public ScriptEngine scriptEngine;
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
		public boolean performTransforms;
		public AffineTransform affineTransform = new AffineTransform();

		//should have affine transform matrix calculated here 
		//store Affine Transform Object here

		public DataAid(PrintJob printJob) throws InappropriateDeviceException {
			this.printJob = printJob;
			this.scriptEngine = HostProperties.Instance().buildScriptEngine();
			printer = printJob.getPrinter();
			printJob.setStartTime(System.currentTimeMillis());
		    configuration = printer.getConfiguration();
			slicingProfile = configuration.getSlicingProfile();
			inkConfiguration = slicingProfile.getSelectedInkConfig();
			xPixelsPerMM = slicingProfile.getDotsPermmX();
			yPixelsPerMM = slicingProfile.getDotsPermmY();
			xResolution = slicingProfile.getxResolution();
			yResolution = slicingProfile.getyResolution();
			this.performTransforms = true;
			
			// Set the affine transform given the customizer from the printJob
			Customizer customizer = printJob.getCustomizer();
			if (customizer != null) {
				this.affineTransform = customizer.createAffineTransform(xResolution, yResolution);
			}
			
			//This file processor requires an ink configuration
			if (inkConfiguration == null) {
				throw new InappropriateDeviceException("Your printer doesn't have a selected ink configuration.");
			}
			
			//TODO: how do I integrate slicingProfile.getLiftDistance()
			sliceHeight = inkConfiguration.getSliceHeight();
		}
	}
	
	public DataAid initializeDataAid(PrintJob printJob) throws InappropriateDeviceException {
		return new DataAid(printJob);
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
		if (aid.slicingProfile.getgCodeHeader() != null && aid.slicingProfile.getgCodeHeader().trim().length() > 0) {
			aid.printer.getGCodeControl().executeGCodeWithTemplating(aid.printJob, aid.slicingProfile.getgCodeHeader());
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
	
	public JobStatus performPreSlice(DataAid aid, List<StlError> errors) throws InappropriateDeviceException {
		if (aid == null) {
			throw new IllegalStateException("initializeDataAid must be called before this method");
		}
		aid.currentSliceTime = System.currentTimeMillis();

		//Show the errors to our users if the stl file is broken, but we'll keep on processing like normal
		if (errors != null && !errors.isEmpty()) {
			NotificationManager.errorEncountered(aid.printJob, errors);
		}
		
		//Perform two actions at once here:
		// 1. Pause if the user asked us to pause
		// 2. Get out if the print is cancelled
		if (!aid.printer.waitForPauseIfRequired()) {
			return aid.printer.getStatus();
		}

		//Execute preslice gcode
		if (aid.slicingProfile.getgCodePreslice() != null && aid.slicingProfile.getgCodePreslice().trim().length() > 0) {
			aid.printer.getGCodeControl().executeGCodeWithTemplating(aid.printJob, aid.slicingProfile.getgCodePreslice());
		}
		
		return null;
	}
	
	public JobStatus printImageAndPerformPostProcessing(DataAid aid, BufferedImage sliceImage) throws ExecutionException, InterruptedException, InappropriateDeviceException, ScriptException {
		if (aid == null) {
			throw new IllegalStateException("initializeDataAid must be called before this method");
		}

		if (sliceImage == null) {
			throw new IllegalStateException("You must specify a sliceImage to display");
		}
		
		//Start but don't wait for a potentially heavy weight operation to determine if we are out of ink.
		if (aid.inkDetector != null) {
			aid.inkDetector.startMeasurement();
		}
		
		//Determine the dynamic amount of time we should expose our resin
		if (!aid.printJob.isExposureTimeOverriden() && aid.slicingProfile.getExposureTimeCalculator() != null && aid.slicingProfile.getExposureTimeCalculator().trim().length() > 0) {
			Number value = calculate(aid, aid.slicingProfile.getExposureTimeCalculator(), "exposure time script");
			if (value != null) {
				aid.printJob.setExposureTime(value.intValue());
			}
		}

		logger.info("ExposureStart:{}", ()->Log4jTimer.startTimer(EXPOSURE_TIMER));
		aid.printer.showImage(sliceImage);
		
		if (aid.slicingProfile.getgCodeShutter() != null && aid.slicingProfile.getgCodeShutter().trim().length() > 0) {
			aid.printer.setShutterOpen(true);
			aid.printer.getGCodeControl().executeGCodeWithTemplating(aid.printJob, aid.slicingProfile.getgCodeShutter());
		}
		
		//Sleep for the amount of time that we are exposing the resin.
		Thread.sleep(aid.printJob.getExposureTime());
		
		if (aid.slicingProfile.getgCodeShutter() != null && aid.slicingProfile.getgCodeShutter().trim().length() > 0) {
			aid.printer.setShutterOpen(false);
			aid.printer.getGCodeControl().executeGCodeWithTemplating(aid.printJob, aid.slicingProfile.getgCodeShutter());
		}

		//Blank the screen
		aid.printer.showBlankImage();
		
		logger.info("ExposureTime:{}", ()->Log4jTimer.completeTimer(EXPOSURE_TIMER));
		
		//Perform two actions at once here:
		// 1. Pause if the user asked us to pause
		// 2. Get out if the print is cancelled
		if (!aid.printer.waitForPauseIfRequired()) {
			return aid.printer.getStatus();
		}
		
		if (!aid.printJob.isZLiftDistanceOverriden() && aid.slicingProfile.getzLiftDistanceCalculator() != null && aid.slicingProfile.getzLiftDistanceCalculator().trim().length() > 0) {
			Number value = calculate(aid, aid.slicingProfile.getzLiftDistanceCalculator(), "lift distance script");
			if (value != null) {
				aid.printJob.setZLiftDistance(value.doubleValue());
			}
		}
		if (!aid.printJob.isZLiftSpeedOverriden() && aid.slicingProfile.getzLiftSpeedCalculator() != null && aid.slicingProfile.getzLiftSpeedCalculator().trim().length() > 0) {
			Number value = calculate(aid, aid.slicingProfile.getzLiftSpeedCalculator(), "lift speed script");
			if (value != null) {
				aid.printJob.setZLiftSpeed(value.doubleValue());
			}
		}
		if (aid.slicingProfile.getZLiftDistanceGCode() != null && aid.slicingProfile.getZLiftDistanceGCode().trim().length() > 0) {
			aid.printer.getGCodeControl().executeGCodeWithTemplating(aid.printJob, aid.slicingProfile.getZLiftDistanceGCode());
		}
		if (aid.slicingProfile.getZLiftSpeedGCode() != null && aid.slicingProfile.getZLiftSpeedGCode().trim().length() > 0) {
			aid.printer.getGCodeControl().executeGCodeWithTemplating(aid.printJob, aid.slicingProfile.getZLiftSpeedGCode());
		}
		
		//Perform the lift gcode manipulation
		aid.printer.getGCodeControl().executeGCodeWithTemplating(aid.printJob, aid.slicingProfile.getgCodeLift());
		
		// Log slice settings (in JSON for extraction and processing)
		logger.info("{ \"layer\": {}, \"exposureTime\": {}, \"liftDistance\": {}, \"liftSpeed\": {} , \"layerAreaMM2\": {} }",
			aid.printJob.getCurrentSlice(), aid.printJob.getExposureTime(), aid.printJob.getZLiftDistance(),
			aid.printJob.getZLiftSpeed(), getBuildAreaMM(aid.printJob));
		
		//Perform area and cost manipulations for current slice
		aid.printJob.addNewSlice(System.currentTimeMillis() - aid.currentSliceTime, getBuildAreaMM(aid.printJob));
		
		//Notify the client that the printJob has increased the currentSlice
		NotificationManager.jobChanged(aid.printer, aid.printJob);
		
		return null;
	}

	public JobStatus performFooter(DataAid aid) throws IOException, InappropriateDeviceException {
		if (aid == null) {
			throw new IllegalStateException("initializeDataAid must be called before this method");
		}

		if (!aid.printer.isPrintActive()) {
			return aid.printer.getStatus();
		}
		
		if (aid.slicingProfile.getgCodeFooter() != null && aid.slicingProfile.getgCodeFooter().trim().length() > 0) {
			aid.printer.getGCodeControl().executeGCodeWithTemplating(aid.printJob, aid.slicingProfile.getgCodeFooter());
		}
		
		if (aid.printer.isProjectorPowerControlSupported()) {
			aid.printer.setProjectorPowerStatus(false);
		}

		return JobStatus.Completed;
	}

	private Number calculate(DataAid aid, String calculator, String calculationName) throws ScriptException {
		try {
			Number num = (Number)TemplateEngine.runScript(aid.printJob, aid.printer, aid.scriptEngine, calculator, calculationName, null);
			if (num == null || Double.isNaN(num.doubleValue())) {
				return null;
			}
			return num;
		} catch (ClassCastException e) {
			throw new IllegalArgumentException("The result of your " + calculationName + " needs to evaluate to an instance of java.lang.Number");
		}
	}

	public void applyBulbMask(DataAid aid, Graphics2D g2, int width, int height) throws ScriptException {
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
				aid.maskPaint = (Paint)TemplateEngine.runScript(aid.printJob, aid.printer, aid.scriptEngine, aid.slicingProfile.getProjectorGradientCalculator(), "projector gradient script", null);
			}
			g2.setPaint(aid.maskPaint);
			g2.fillRect(0, 0, width, height);
		} catch (ClassCastException e) {
			throw new IllegalArgumentException("The result of your bulb mask script needs to evaluate to an instance of java.awt.Paint");
		}
	}

	public BufferedImage applyImageTransforms(DataAid aid, BufferedImage img, int width, int height) throws ScriptException {
		if (aid == null) {
			throw new IllegalStateException("initializeDataAid must be called before this method");
		}
		if (img == null) {
			throw new IllegalStateException("BufferedImage is null");
		}

		if (!aid.performTransforms) {
			return img;
		}
		
		BufferedImage after = new BufferedImage(width, height, img.getType());
			
		((Graphics2D)img.getGraphics()).setBackground(Color.black);
		((Graphics2D)after.getGraphics()).setBackground(Color.black);
		
		AffineTransformOp transOp = new AffineTransformOp(aid.affineTransform, AffineTransformOp.TYPE_BILINEAR);
		after = transOp.filter(img, after);
		
		applyBulbMask(aid, (Graphics2D)after.getGraphics(), width, height);
		return after;
	}
	
	protected BufferedImage convertTo3BGR(BufferedImage input) {
		BufferedImage output = new BufferedImage(input.getWidth(), input.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
		output.getGraphics().drawImage(input, 0, 0, null);
		return output;
	}
	
	public BufferedImage buildPreviewSlice(Customizer customizer, File jobFile, Previewable previewable, boolean projectImage) throws NoPrinterFoundException, SliceHandlingException {
		//find the first activePrinter
		String printerName = customizer.getPrinterName();
		Printer activePrinter = null;
		if (printerName == null || printerName.isEmpty()) {
			//if customizer doesn't have a printer stored, set first active printer as printer
			try {
				activePrinter = PrinterService.INSTANCE.getFirstAvailablePrinter();				
			} catch (NoPrinterFoundException e) {
				throw new NoPrinterFoundException("No printers found for slice preview. You must have a started printer or specify a valid printer in the Customizer.");
			}
			
		} else {
			try {
				activePrinter = PrinterService.INSTANCE.getPrinter(printerName);
			} catch (InappropriateDeviceException e) {
				logger.warn("Could not locate printer {}", printerName, e);
			}
		}

		try {
			//instantiate a new print job based on the jobFile and set its printer to activePrinter
			PrintJob printJob = new PrintJob(jobFile);
			printJob.setPrinter(activePrinter);
			printJob.setCustomizer(customizer);
			printJob.setPrintFileProcessor(this);
			
			//instantiate new dataaid
			DataAid dataAid = initializeDataAid(printJob);
			BufferedImage image;
			
			if (customizer.getOrigSliceCache() == null) {
				dataAid.performTransforms = false;
				image = previewable.renderPreviewImage(dataAid);
				dataAid.performTransforms = true;
				if (customizer.getAffineTransformSettings().isIdentity()) {
					image = convertTo3BGR(image);
					customizer.setOrigSliceCache(image);
				}
			}
			
			image = applyImageTransforms(dataAid, customizer.getOrigSliceCache(), customizer.getOrigSliceCache().getWidth(), customizer.getOrigSliceCache().getHeight());
			if (projectImage) {
				activePrinter.showImage(image);
			} else {
				activePrinter.showBlankImage();
			}

			return image;
		} catch (InappropriateDeviceException | ScriptException e) {
			throw new SliceHandlingException(e);
		}
	}
}
