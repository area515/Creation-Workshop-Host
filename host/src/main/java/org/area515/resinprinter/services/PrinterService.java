
package org.area515.resinprinter.services;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.security.RolesAllowed;
import javax.imageio.ImageIO;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.area515.resinprinter.display.AlreadyAssignedException;
import org.area515.resinprinter.display.DisplayManager;
import org.area515.resinprinter.display.GraphicsOutputInterface;
import org.area515.resinprinter.display.InappropriateDeviceException;
import org.area515.resinprinter.display.LastAvailableDisplay;
import org.area515.resinprinter.display.SimulatedDisplay;
import org.area515.resinprinter.exception.NoPrinterFoundException;
import org.area515.resinprinter.job.Customizer;
import org.area515.resinprinter.job.InkDetector;
import org.area515.resinprinter.job.JobManagerException;
import org.area515.resinprinter.job.PrintJob;
import org.area515.resinprinter.job.PrintJobManager;
import org.area515.resinprinter.printer.BuildDirection;
import org.area515.resinprinter.printer.ComPortSettings;
import org.area515.resinprinter.printer.MachineConfig;
import org.area515.resinprinter.printer.MachineConfig.MonitorDriverConfig;
import org.area515.resinprinter.printer.MachineConfig.MotorsDriverConfig;
import org.area515.resinprinter.printer.Printer;
import org.area515.resinprinter.printer.PrinterConfiguration;
import org.area515.resinprinter.printer.PrinterManager;
import org.area515.resinprinter.printer.SlicingProfile;
import org.area515.resinprinter.printer.SlicingProfile.Font;
import org.area515.resinprinter.printer.SlicingProfile.InkConfig;
import org.area515.resinprinter.printer.SlicingProfile.TwoDimensionalSettings;
import org.area515.resinprinter.serial.ConsoleCommPort;
import org.area515.resinprinter.serial.SerialManager;
import org.area515.resinprinter.server.HostProperties;
import org.area515.resinprinter.services.TestingResult.ChartData;
import org.area515.resinprinter.util.security.PhotonicUser;
import org.area515.util.TemplateEngine;

@Api(value="printers")
@RolesAllowed(PhotonicUser.FULL_RIGHTS)
@Path("printers")
public class PrinterService {
    private static final Logger logger = LogManager.getLogger();
	public static PrinterService INSTANCE = new PrinterService();
	public static Font DEFAULT_FONT = new Font("Dialog", 200);
	private PrinterService(){}
	
	private MachineResponse openShutter(String printerName, boolean shutter) throws InappropriateDeviceException, JobManagerException {
		String name = shutter?"OpenShutter":"CloseShutter";
		Printer printer = PrinterManager.Instance().getPrinter(printerName);
		PrintJob job = TemplateEngine.buildStubJob(printer);
		if (printer == null) {
			return new MachineResponse(name, false, "Printer:" + printerName + " not started");
		}
		
		String shutterGCode = printer.getConfiguration().getSlicingProfile().getgCodeShutter();
		if (shutterGCode != null && shutterGCode.trim().length() > 0) {
			printer.setShutterOpen(shutter);
			return new MachineResponse(name, true, printer.getPrinterController().executeCommands(job, printer.getConfiguration().getSlicingProfile().getgCodeShutter(), false));
		}
		
		return new MachineResponse(name, false, "This printer doesn't support a shutter.");
	}
	
    @ApiOperation(value="Opens the shutter(when supported) of the Printer specified by the printername.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, response=MachineResponse.class, message = SwaggerMetadata.MACHINE_RESPONSE),
            @ApiResponse(code = 500, message = SwaggerMetadata.UNEXPECTED_ERROR)})
	@GET
	@Path("openshutter/{printername}")
	@Produces(MediaType.APPLICATION_JSON)
	public MachineResponse openShutter(@PathParam("printername") String printerName) throws InappropriateDeviceException, JobManagerException {
		return openShutter(printerName, true);
	}
	
    @ApiOperation(value="Closes the shutter(when supported) of the Printer specified by the <b class=\"code\">printername</b>.")
    @ApiResponses(
    		value = {
            @ApiResponse(code = 200, response=MachineResponse.class, message = SwaggerMetadata.MACHINE_RESPONSE),
            @ApiResponse(code = 500, message=SwaggerMetadata.UNEXPECTED_ERROR)})
	@GET
	@Path("closeshutter/{printername}")
	@Produces(MediaType.APPLICATION_JSON)
	public MachineResponse closeShutter(@PathParam("printername") String printerName) throws InappropriateDeviceException, JobManagerException {
		return openShutter(printerName, false);
	}
	
    @ApiOperation(value="Lists all of the printers that are managed by Photonic 3D.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = SwaggerMetadata.TODO),
            @ApiResponse(code = 500, message = SwaggerMetadata.UNEXPECTED_ERROR)})
	@GET
	@Path("list")
	@Produces(MediaType.APPLICATION_JSON)
	public List<Printer> getPrinters() {
		List<PrinterConfiguration> identifiers = HostProperties.Instance().getPrinterConfigurations();
		List<Printer> printers = new ArrayList<Printer>();
		for (PrinterConfiguration current : identifiers) {
			try {
				Printer printer = PrinterManager.Instance().getPrinter(current.getName());
				if (printer == null) {
					printer = new Printer(current);
				}
				printers.add(printer);
			} catch (InappropriateDeviceException e) {
			    logger.error("Error getting printer list", e);
			}
		}
		
		return printers;
	}

    @ApiOperation(value="Lists the first available printer.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = SwaggerMetadata.TODO),
            @ApiResponse(code = 500, message = SwaggerMetadata.UNEXPECTED_ERROR)})
	@GET
	@Path("getFirstAvailablePrinter")
	@Produces(MediaType.APPLICATION_JSON)
	public Printer getFirstAvailablePrinter() throws NoPrinterFoundException {
		List<Printer> printers = getPrinters();
		if (printers.isEmpty()) {
			throw new NoPrinterFoundException("No printers found.");
		}
		Printer activePrinter = null;

		for (Printer printer : printers) {
			if (printer.isStarted()) {
				activePrinter = printer;
				break;
			}
		}
		if (activePrinter == null) {
			throw new NoPrinterFoundException("No active printers.");
		}
		
		return activePrinter;
	}	
 
    @ApiOperation(value="Returns the Printer specified by the printername.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = SwaggerMetadata.TODO),
            @ApiResponse(code = 500, message = SwaggerMetadata.UNEXPECTED_ERROR)})
	@GET
	@Path("get/{printername}")
	@Produces(MediaType.APPLICATION_JSON)
	public Printer getPrinter(@PathParam("printername") String printerName) throws InappropriateDeviceException {
		Printer printer = PrinterManager.Instance().getPrinter(printerName);
		if (printer == null) {
			PrinterConfiguration currentConfiguration = HostProperties.Instance().getPrinterConfiguration(printerName);
			if (currentConfiguration == null) {
				throw new InappropriateDeviceException("No printer with that name:" + printerName);
			}
				
				printer = new Printer(currentConfiguration);
		}
		 
		return printer;
	}
	
    @ApiOperation(value="Deletes the Printer specified by the printername.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, response=MachineResponse.class, message = SwaggerMetadata.MACHINE_RESPONSE),
            @ApiResponse(code = 500, message = SwaggerMetadata.UNEXPECTED_ERROR)})
	@GET
	@POST
	@DELETE
	@Path("delete/{printername}")
	@Produces(MediaType.APPLICATION_JSON)
	public MachineResponse deletePrinter(@PathParam("printername") String printerName) {
		try {
			Printer currentPrinter = PrinterManager.Instance().getPrinter(printerName);
			if (currentPrinter != null) {
				throw new InappropriateDeviceException("Can't delete printer when it's started:" + printerName);
			}
			
			PrinterConfiguration currentConfiguration = HostProperties.Instance().getPrinterConfiguration(printerName);
			if (currentConfiguration == null) {
				throw new InappropriateDeviceException("No printer with that name:" + printerName);
			}				
			
			HostProperties.Instance().removePrinterConfiguration(currentConfiguration);
			return new MachineResponse("deletePrinter", true, "Deleted:" + printerName);
		} catch (InappropriateDeviceException e) {
		    logger.error("Error deleting printer:" + printerName, e);
			return new MachineResponse("deletePrinter", false, e.getMessage());
		}
	}

	//TODO: We need to synchronize on this printer to make sure it isn't in use.
    @ApiOperation(value="Saves the Printer.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, response=MachineResponse.class, message = SwaggerMetadata.MACHINE_RESPONSE),
            @ApiResponse(code = 500, message = SwaggerMetadata.UNEXPECTED_ERROR)})
	@POST
	@Path("save")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public MachineResponse savePrinter(Printer printerToSave) {
		try {
			String printerName = printerToSave.getConfiguration().getName();
			Printer currentPrinter = PrinterManager.Instance().getPrinter(printerName);
			if (currentPrinter != null) {
				throw new InappropriateDeviceException("Can't save printer after it's been started:" + printerName);
			}
			
			try {
				HostProperties.Instance().addOrUpdatePrinterConfiguration(printerToSave.getConfiguration());
				return new MachineResponse("savePrinter", true, "Created:" + printerToSave.getName() + "");
			} catch (AlreadyAssignedException e) {
			    logger.error("Error saving printer:" + printerToSave, e);
				return new MachineResponse("savePrinter", false, e.getMessage());
			}
		} catch (InappropriateDeviceException e) {
		    logger.error("Error saving printer:" + printerToSave, e);
			return new MachineResponse("savePrinter", false, e.getMessage());
		}
	}
	
    @ApiOperation(value="Starts the Printer specified by the printername.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, response=MachineResponse.class, message = SwaggerMetadata.MACHINE_RESPONSE),
            @ApiResponse(code = 500, message = SwaggerMetadata.UNEXPECTED_ERROR)})
	@GET
	@POST
	@Path("start/{printername}")
	@Produces(MediaType.APPLICATION_JSON)
	public MachineResponse startPrinter(@PathParam("printername") String printerName) {
		Printer printer = null;
		try {
			PrinterConfiguration currentConfiguration = HostProperties.Instance().getPrinterConfiguration(printerName);
			if (currentConfiguration == null) {
				throw new InappropriateDeviceException("No printer with that name:" + printerName);
			}
			
			printer = PrinterManager.Instance().startPrinter(currentConfiguration);
			return new MachineResponse("startPrinter", true, "Started:" + printer.getName() + "");
		} catch (JobManagerException | AlreadyAssignedException | InappropriateDeviceException e) {
		    logger.error("Error starting printer:" + printerName, e);
			return new MachineResponse("startPrinter", false, e.getMessage());
		}
	}	 
	 
    @ApiOperation(value="Stops the Printer specified by the printername.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, response=MachineResponse.class, message = SwaggerMetadata.MACHINE_RESPONSE),
            @ApiResponse(code = 500, message = SwaggerMetadata.UNEXPECTED_ERROR)})
	@GET
	@POST
	@Path("stop/{printername}")
	@Produces(MediaType.APPLICATION_JSON)
	public MachineResponse stopPrinter(@PathParam("printername") String printerName) {
		try {
			Printer printer = PrinterManager.Instance().getPrinter(printerName);
			if (printer == null) {
				throw new InappropriateDeviceException("This printer isn't started:" + printerName);
			}
			if (printer.isPrintInProgress()) {
				throw new InappropriateDeviceException("Can't stop printer while a job is in progress. Please stop the active printjob first.");
			}
			//You must stop the printer before removing assignments otherwise the serial ports won't get closed.
			PrinterManager.Instance().stopPrinter(printer);
			DisplayManager.Instance().removeAssignment(printer);
			SerialManager.Instance().removeAssignments(printer);
			return new MachineResponse("stopPrinter", true, "Stopped:" + printerName);
		} catch (InappropriateDeviceException e) {
		    logger.error("Error stop printer:" + printerName, e);
			return new MachineResponse("stopPrinter", false, e.getMessage());
		}
	}
 
    @ApiOperation(value="Creates and returns a Printer based off of a predefined template.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = SwaggerMetadata.TODO),
            @ApiResponse(code = 500, message = SwaggerMetadata.UNEXPECTED_ERROR)})
	@POST
	@Path("createTemplatePrinter")
	@Produces(MediaType.APPLICATION_JSON)
	public Printer createTemplatePrinter() throws InappropriateDeviceException {
		//TODO: Return a nice unused name for this printer instead of the hardcoded value below
		PrinterConfiguration configuration = createTemplatePrinter(
			 "CWH Template Printer", //"mUVe 1 DLP (Testing)", 
			 SimulatedDisplay.NAME, 
			 ConsoleCommPort.GCODE_RESPONSE_SIMULATION, 
			 134, 75, 185);
		configuration.getSlicingProfile().getSelectedInkConfig().setNumberOfFirstLayers(10);
		configuration.getSlicingProfile().getSelectedInkConfig().setFirstLayerExposureTime(20000);
		configuration.getSlicingProfile().getSelectedInkConfig().setExposureTime(8000);
		configuration.getSlicingProfile().getSelectedInkConfig().setPercentageOfInkConsideredEmpty(10);
		configuration.getSlicingProfile().setgCodeHeader(
			 "G21 ;Set units to be mm\n" +
			 "G91 ;Relative Positioning\n" +
			 "G28 ; Home Printer\n" +
			 "M650 D$ZLiftDist S$ZLiftRate P0; CWH Template Preferences\n" + //mUVe 1 Prefs\n" +
			 "M17 ;Enable motors");
		configuration.getSlicingProfile().setgCodeFooter(
			 "M18 ;Disable Motors");
		configuration.getSlicingProfile().setgCodeLift(
			 "M651; Do CWH Template Peel Move\n" + //Do mUVe 1 Peel Move\n" + 
			 "G1 Z${((LayerThickness) * ZDir)}");
		configuration.getSlicingProfile().setZLiftDistanceGCode("M650 D${ZLiftDist} S${ZLiftRate}");
		configuration.getSlicingProfile().setZLiftSpeedGCode("M650 D${ZLiftDist} S${ZLiftRate}");
		configuration.getSlicingProfile().setzLiftSpeedCalculator("var value = 0.25;\n"
				+ "if ($CURSLICE > $NumFirstLayers) {\n"
				+ " value = 4.6666666666666705e+000 * Math.pow($buildAreaMM,0) + -7.0000000000000184e-003 * Math.pow($buildAreaMM,1) + 3.3333333333333490e-006 * Math.pow($buildAreaMM,2);\n"
				+ "}\n"
				+ "value");
		configuration.getSlicingProfile().setzLiftDistanceCalculator("var value = 9.0;\n"
				+ "if ($CURSLICE > $NumFirstLayers) {\n"
		 	+ " value = 3.5555555555555420e+000 * Math.pow($buildAreaMM,0) + 4.3333333333334060e-003 * Math.pow($buildAreaMM,1) + 1.1111111111110492e-006 * Math.pow($buildAreaMM,2);\n"
		 	+ "}\n"
		 	+ "value");
		configuration.getSlicingProfile().setExposureTimeCalculator("var value = $FirstLayerTime;\n"
				+ "if ($CURSLICE > $NumFirstLayers) {\n"
				+ "	value = $LayerTime\n"
				+ "}\n"
		 	+ "value");
		configuration.getSlicingProfile().setProjectorGradientCalculator(
				/*"function getFractions(count, start, end) {\n" + 
			"	var incrementAmount = (end - start) / count;\n" +
			"	var fractions = [];\n" + 
			"	for (t = 0; t < count; t++) {\n" +
			"		fractions[t] = start + incrementAmount * t;\n" +
			"	}\n" +
			"	//return new float[]{0, 1};\n" +
			"	return fractions;\n" + 
				"}\n" +
				"function getColors(fractions, start, stop) {\n" + 
				"	var colors = [];\n" +
				"	var colorRange = stop - start;\n" + 
				"	var atanDivergencePoint = Math.PI / 2;\n" + 
				"	for (t = 0; t < fractions.length; t++) {\n" +
				"		colors[t] = new Packages.java.awt.Color(0, 0, 0, (java.lang.Float)(Math.atan(fractions[t] * atanDivergencePoint) * colorRange + start));\n" + 
			"	}\n" + 
			"	//return new Packages.java.awt.Color[]{new Packages.java.awt.Color(0, 0, 0, (float)(opacityLevelModel.getValue()/(float)opacityLevelModel.getMaximum())), new Packages.java.awt.Color(0, 0, 0, 0)};\n" +
			"	return colors;\n" + 
				"}\n" +*/
				"var bulbCenter = new Packages.java.awt.geom.Point2D.Double($buildPlatformXPixels / 2, $buildPlatformYPixels / 2);\n" +
				"var bulbFocus = new Packages.java.awt.geom.Point2D.Double($buildPlatformXPixels / 2, $buildPlatformYPixels / 2);\n" +
				"var totalSizeOfGradient = $buildPlatformXPixels > $buildPlatformYPixels?$buildPlatformXPixels:$buildPlatformYPixels;\n" +
				"var fractions = [0.0, 1.0];\n" +
				"//Let's start with 20% opaque in the center of the projector bulb\n" + 
				"var colors = [new Packages.java.awt.Color(0.0, 0.0, 0.0, 0.2), new Packages.java.awt.Color(0.0, 0.0, 0.0, 0.0)];\n" +
				"new Packages.java.awt.RadialGradientPaint(\n" +
				"	bulbCenter,\n" + 
				"	totalSizeOfGradient,\n" +
				"	bulbFocus,\n" +
				"	fractions,\n" +
				"	colors,\n" +
				"	java.awt.MultipleGradientPaint.CycleMethod.NO_CYCLE)");

		return new Printer(configuration);
	}
	
	PrinterConfiguration createTemplatePrinter(String printername, String displayId, String comport, double physicalProjectionMMX, double physicalProjectionMMY, double buildHeightMMZ) {
		PrinterConfiguration currentConfiguration = new PrinterConfiguration(printername, printername, false);
		ComPortSettings firmwareComSettings = new ComPortSettings();
		firmwareComSettings.setPortName(comport);
		firmwareComSettings.setDatabits(8);
		firmwareComSettings.setHandshake("None");
		firmwareComSettings.setStopbits("One");
		firmwareComSettings.setParity("None");
		firmwareComSettings.setSpeed(115200L);
		
		MotorsDriverConfig motors = new MotorsDriverConfig();
		motors.setComPortSettings(firmwareComSettings);
		MonitorDriverConfig monitor = new MonitorDriverConfig();
		ComPortSettings projectorComSettings = new ComPortSettings();
		projectorComSettings.setPortName(null);//We aren't going to be using a projector on our template
		projectorComSettings.setDatabits(8);
		projectorComSettings.setHandshake("None");
		projectorComSettings.setStopbits("One");
		projectorComSettings.setParity("None");
		projectorComSettings.setSpeed(9600L);
		monitor.setComPortSettings(projectorComSettings);
		
		MachineConfig machineConfig = new MachineConfig();
		machineConfig.setMotorsDriverConfig(motors);
		machineConfig.setMonitorDriverConfig(monitor);
		machineConfig.setOSMonitorID(displayId);
		machineConfig.setName(printername);
		machineConfig.setPlatformXSize(physicalProjectionMMX);
		machineConfig.setPlatformYSize(physicalProjectionMMY);
		machineConfig.setPlatformZSize(buildHeightMMZ);
		
		SlicingProfile slicingProfile = new SlicingProfile();
		slicingProfile.setLiftDistance(5.0);
		slicingProfile.setLiftFeedRate(50);
		slicingProfile.setDirection(BuildDirection.Bottom_Up);
		try {
			if (SimulatedDisplay.NAME.equals(displayId)) {
				monitor.setDLP_X_Res(1920);
				monitor.setDLP_Y_Res(1080);
			} else {
				GraphicsOutputInterface device = DisplayManager.Instance().getDisplayDevice(LastAvailableDisplay.NAME);
				monitor.setDLP_X_Res(device.getBoundary().getWidth());
				monitor.setDLP_Y_Res(device.getBoundary().getHeight());
			}
			machineConfig.setxRenderSize((int)monitor.getDLP_X_Res());
			machineConfig.setyRenderSize((int)monitor.getDLP_Y_Res());
			slicingProfile.setxResolution((int)monitor.getDLP_X_Res());
			slicingProfile.setyResolution((int)monitor.getDLP_Y_Res());
		} catch (InappropriateDeviceException e) {
		    logger.error("Error creating graphics device for printer:" + printername, e);
			throw new IllegalArgumentException("Couldn't get screen device");
		}
		
		InkConfig ink = new InkConfig();
		ink.setName("Default");
		ink.setNumberOfFirstLayers(3);
		ink.setResinPriceL(65.0);
		ink.setSliceHeight(0.1);
		ink.setFirstLayerExposureTime(5000);
		ink.setExposureTime(1000);
		
		List<InkConfig> configs = new ArrayList<InkConfig>();
		configs.add(ink);
		
		TwoDimensionalSettings settings = new TwoDimensionalSettings();
		settings.setFont(DEFAULT_FONT);
		settings.setPlatformCalculator("var extrusionX = printImage.getWidth();\nvar extrusionY = printImage.getHeight();\nbuildPlatformGraphics.fillRoundRect(centerX - (extrusionX / 2), centerY - (extrusionY / 2), extrusionX, extrusionY, 50, 50);");
		settings.setExtrusionHeightMM(1.5);
		settings.setPlatformHeightMM(1.5);
		settings.setEdgeDetectionDisabled(false);
		settings.setScaleImageToFitPrintArea(true);
		
		slicingProfile.setTwoDimensionalSettings(settings);
		slicingProfile.setInkConfigs(configs);
		slicingProfile.setSelectedInkConfigName("Default");
		slicingProfile.setDotsPermmX(monitor.getDLP_X_Res() / physicalProjectionMMX);
		slicingProfile.setDotsPermmY(monitor.getDLP_Y_Res() / physicalProjectionMMY);
		slicingProfile.setFlipX(false);
		slicingProfile.setFlipY(true);
		
		currentConfiguration.setSlicingProfile(slicingProfile);
		currentConfiguration.setMachineConfig(machineConfig);
			
		currentConfiguration.setName(printername);
		return currentConfiguration;
	}
	
    @ApiOperation(value="Shows the grid screen on the Printer specified by the printername.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, response=MachineResponse.class, message = SwaggerMetadata.MACHINE_RESPONSE),
            @ApiResponse(code = 500, message = SwaggerMetadata.UNEXPECTED_ERROR)})
	@GET
	@Path("showGridScreen/{printername}/{pixels}")
	@Produces(MediaType.APPLICATION_JSON)
	public MachineResponse showGridScreen(@PathParam("printername") String printerName, @PathParam("pixels") int pixels) {
		try {
			Printer currentPrinter = PrinterManager.Instance().getPrinter(printerName);
			if (currentPrinter == null) {
				throw new InappropriateDeviceException("Printer:" + printerName + " not started");
			}
			
			if (currentPrinter.isDisplayBusy()) {
				throw new InappropriateDeviceException("Printer:" + printerName + " display is busy, try again later.");
			}
			
			currentPrinter.showGridImage(pixels);
			return new MachineResponse("gridscreenshown", true, "Showed calibration screen on:" + printerName);
		} catch (InappropriateDeviceException e) {
		    logger.error("Error showing grid screen for printer:" + printerName, e);
			return new MachineResponse("gridscreenshown", false, e.getMessage());
		}
	}
    
    @ApiOperation(value="Display pre-configured image on the printing screen, for example the manufacturer logo for LCD printers, just to test the screen is connected")
    @ApiResponses(value = {
            @ApiResponse(code = 200, response=MachineResponse.class, message = SwaggerMetadata.MACHINE_RESPONSE),
            @ApiResponse(code = 500, message = SwaggerMetadata.UNEXPECTED_ERROR)})
	@GET
	@Path("showLogo/{printername}")
	@Produces(MediaType.APPLICATION_JSON)
	public MachineResponse showLogo(@PathParam("printername") String printerName) {
		try {
			Printer currentPrinter = PrinterManager.Instance().getPrinter(printerName);
			if (currentPrinter == null) {
				throw new InappropriateDeviceException("Printer:" + printerName + " not started");
			}
			
			if (currentPrinter.isDisplayBusy()) {
				throw new InappropriateDeviceException("Printer:" + printerName + " display is busy, try again later.");
			}
			BufferedImage img = null;
			try {
				img = ImageIO.read(this.getClass().getClassLoader().getResourceAsStream("PhotonicSplash.png"));
			} catch (IOException e) {
			    logger.error("Error showing logo for printer:" + printerName + " Location:" + getClass().getProtectionDomain().getCodeSource().getLocation(), e);
				return new MachineResponse("showLogo" + this.getClass().getProtectionDomain().getCodeSource().getLocation(), false, e.getMessage());
			}
			currentPrinter.setStatus(currentPrinter.getStatus());//This is to make sure the slicenumber is reset.
			currentPrinter.showImage(img, true);
			return new MachineResponse("logo shown", true, "Showed logo screen on:" + printerName);
		} catch (InappropriateDeviceException e) {
		    logger.error("Error showing logo for printer:" + printerName, e);
			return new MachineResponse("gridscreenshown", false, e.getMessage());
		}
	}
	
    @ApiOperation(value="Shows the calibration screen on the Printer specified by the printername. "
    		+ "The calibration screen will show a crosshairs icon with xpixels and ypixels across.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, response=MachineResponse.class, message = SwaggerMetadata.MACHINE_RESPONSE),
            @ApiResponse(code = 500, message = SwaggerMetadata.UNEXPECTED_ERROR)})
	@GET
	@Path("showCalibrationScreen/{printername}/{xpixels}/{ypixels}")
	@Produces(MediaType.APPLICATION_JSON)
	public MachineResponse showCalibrationScreen(@PathParam("printername") String printerName, @PathParam("xpixels") int xPixels,  @PathParam("ypixels") int yPixels) {
		try {
			Printer currentPrinter = PrinterManager.Instance().getPrinter(printerName);
			if (currentPrinter == null) {
				throw new InappropriateDeviceException("Printer:" + printerName + " not started");
			}
			
			if (currentPrinter.isDisplayBusy()) {
				throw new InappropriateDeviceException("Printer:" + printerName + " display is busy, try again later.");
			}
			
			logger.info("Showing calibration screen for xPixels:{} yPixels:{}", xPixels, yPixels);
			currentPrinter.showCalibrationImage(xPixels, yPixels);
			return new MachineResponse("calibrationscreenshown", true, "Showed calibration screen on:" + printerName);
		} catch (InappropriateDeviceException e) {
		    logger.error("Error showing calibration screen for printer:" + printerName, e);
			return new MachineResponse("calibrationscreenshown", false, e.getMessage());
		}
	}
	
    @ApiOperation(value="This method calibrates the Printer with the specified printername using <b class=\"code\">xpixelspermm</b> and <b class=\"code\">ypixelspermm</b>. "
    		+ "This is the only service method that allows that saving of a Printer while it has already been started.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, response=MachineResponse.class, message = SwaggerMetadata.MACHINE_RESPONSE),
            @ApiResponse(code = 500, message = SwaggerMetadata.UNEXPECTED_ERROR)})
	//This is the only method that breaks the rules that says that you can't save a printer while it is started...
	@GET
	@Path("calibrate/{printername}/{xpixelspermm}/{ypixelspermm}")
	@Produces(MediaType.APPLICATION_JSON)
	public MachineResponse calibrate(@PathParam("printername") String printerName, @PathParam("xpixelspermm") double xPixelsPerMM,  @PathParam("ypixelspermm") double yPixelsPerMM) {
		try {
			PrinterConfiguration currentConfiguration = HostProperties.Instance().getPrinterConfiguration(printerName);
			if (currentConfiguration == null) {
				throw new InappropriateDeviceException("No printer with that name:" + printerName);
			}
			
			currentConfiguration.getSlicingProfile().setDotsPermmX(xPixelsPerMM);
			currentConfiguration.getSlicingProfile().setDotsPermmY(yPixelsPerMM);
			Printer printer = PrinterService.INSTANCE.getPrinter(printerName);
			GraphicsOutputInterface device = null;
			if (printer.isStarted()) {
				device = DisplayManager.Instance().getDisplayDevice(printer.getDisplayDeviceID());
			}
			if (!printer.isStarted() || device == null) {
				device = DisplayManager.Instance().getDisplayDevice(currentConfiguration.getMachineConfig().getOSMonitorID());
			}
			currentConfiguration.getSlicingProfile().setxResolution(device.getBoundary().width);
			currentConfiguration.getSlicingProfile().setyResolution(device.getBoundary().height);
			currentConfiguration.setCalibrated(true);
			logger.info("Calibrated printer to xPixelsPerMM:{} yPixelsPerMM:{}", xPixelsPerMM, yPixelsPerMM);
			
			HostProperties.Instance().addOrUpdatePrinterConfiguration(currentConfiguration);
			return new MachineResponse("calibratePrinter", true, "Calibrated printer:" + printerName + "");
		} catch (InappropriateDeviceException e) {
		    logger.error("Error calibrating printer:" + printerName, e);
			return new MachineResponse("calibratePrinter", false, e.getMessage());
		} catch (AlreadyAssignedException e) {
		    logger.error("Error saving printer:" + printerName, e);
			return new MachineResponse("calibratePrinter", false, e.getMessage());
		}
	}
	
    @ApiOperation(value="Shows a blank screen on the Printer with the specified printername using xpixelspermm and ypixelspermm.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, response=MachineResponse.class, message = SwaggerMetadata.MACHINE_RESPONSE),
            @ApiResponse(code = 500, message = SwaggerMetadata.UNEXPECTED_ERROR)})
	@GET
	@Path("showBlankScreen/{printername}")
	@Produces(MediaType.APPLICATION_JSON)
	public MachineResponse showBlankScreen(@PathParam("printername") String printerName) {
		try {
			Printer currentPrinter = PrinterManager.Instance().getPrinter(printerName);
			if (currentPrinter == null) {
				throw new InappropriateDeviceException("Printer:" + printerName + " not started");
			}
			
			if (currentPrinter.isDisplayBusy()) {
				throw new InappropriateDeviceException("Printer:" + printerName + " display is busy, try again later.");
			}
			
			currentPrinter.showBlankImage();
			return new MachineResponse("blankscreenshown", true, "Showed blank screen on:" + printerName);
		} catch (InappropriateDeviceException e) {
		    logger.error("Error showing blank screen for printer:" + printerName, e);
			return new MachineResponse("blankscreenshown", false, e.getMessage());
		}
	}
	
    @ApiOperation(value="Executes the specified device dependent code(generally gcode) on the Printer given by the printername."
    		+ "This service call is identical in every way to the executeCode service method.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, response=MachineResponse.class, message = SwaggerMetadata.MACHINE_RESPONSE),
            @ApiResponse(code = 500, message = SwaggerMetadata.UNEXPECTED_ERROR)})
	@GET
	@Path("executeGCode/{printername}/{gcode}")
	@Produces(MediaType.APPLICATION_JSON)
	public MachineResponse executeGCode(@PathParam("printername") String printerName, @PathParam("gcode") String gcode) {
    	return executeCode(printerName, gcode);
    }

    @ApiOperation(value="Executes the specified device dependent code on the Printer given by the printername.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, response=MachineResponse.class, message = SwaggerMetadata.MACHINE_RESPONSE),
            @ApiResponse(code = 500, message = SwaggerMetadata.UNEXPECTED_ERROR)})
	@GET
	@Path("executeCode/{printername}/{code}")
	@Produces(MediaType.APPLICATION_JSON)
	public MachineResponse executeCode(@PathParam("printername") String printerName, @PathParam("gcode") String code) {
		Printer printer = PrinterManager.Instance().getPrinter(printerName);
		if (printer == null) {
			return new MachineResponse("gcode", false, "Printer:" + printerName + " not started");
		}
		
		return new MachineResponse("gcode", true, printer.getPrinterController().executeSingleCommand(code));
	}
    
	//X Axis Move (sedgwick open aperature)
	//MachineControl.cmdMoveX()
    @ApiOperation(value="Executes an X based movement for the Printer specified by the printername.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, response=MachineResponse.class, message = SwaggerMetadata.MACHINE_RESPONSE),
            @ApiResponse(code = 500, message = SwaggerMetadata.UNEXPECTED_ERROR)})
	@GET
	@Path("moveX/{printername}/{distance}")
	@Produces(MediaType.APPLICATION_JSON)
	public MachineResponse moveX(@PathParam("distance") String dist, @PathParam("printername") String printerName) {
		Printer printer = PrinterManager.Instance().getPrinter(printerName);
		if (printer == null) {
			return new MachineResponse("movex", false, "Printer:" + printerName + " not started");
		}
		
		printer.getPrinterController().executeSetRelativePositioning();
		return new MachineResponse("movex", true, printer.getPrinterController().executeMoveX(Double.parseDouble(dist)));
	}
	
	//Y Axis Move (sedgwick close aperature)
	//MachineControl.cmdMoveY()
    @ApiOperation(value="Executes a Y based movement for the Printer specified by the printername.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, response=MachineResponse.class, message = SwaggerMetadata.MACHINE_RESPONSE),
            @ApiResponse(code = 500, message = SwaggerMetadata.UNEXPECTED_ERROR)})
	@GET
	@Path("moveY/{printername}/{distance}")
	@Produces(MediaType.APPLICATION_JSON)
	public MachineResponse moveY(@PathParam("distance") String dist, @PathParam("printername") String printerName) {
		Printer printer = PrinterManager.Instance().getPrinter(printerName);
		if (printer == null) {
			return new MachineResponse("movey", false, "Printer:" + printerName + " not started");
		}
		
		printer.getPrinterController().executeSetRelativePositioning();
		return new MachineResponse("movey", true, printer.getPrinterController().executeMoveY(Double.parseDouble(dist)));
	}
	
	//Z Axis Move(double dist)
	//MachineControl.cmdMoveZ(double dist)
	// (.025 small reverse)
	// (1.0 medium reverse)
	// (10.0 large reverse)
	// (-.025 small reverse)
	// (-1.0 medium reverse)
	// (-10.0 large reverse)
    @ApiOperation(value="Executes a Z based movement for the Printer specified by the printername.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, response=MachineResponse.class, message = SwaggerMetadata.MACHINE_RESPONSE),
            @ApiResponse(code = 500, message = SwaggerMetadata.UNEXPECTED_ERROR)})
	@GET
	@Path("moveZ/{printername}/{distance}")
	@Produces(MediaType.APPLICATION_JSON)
	public MachineResponse moveZ(@PathParam("distance") String dist, @PathParam("printername") String printerName) {
		Printer printer = PrinterManager.Instance().getPrinter(printerName);
		if (printer == null) {
			return new MachineResponse("movez", false, "Printer:" + printerName + " not started");
		}
	
		printer.getPrinterController().executeSetRelativePositioning();
		String response = printer.getPrinterController().executeMoveZ(Double.parseDouble(dist));
		return new MachineResponse("movez", true, response);
	}
	 
    @ApiOperation(value="Homes the Z axis for the Printer specified by the printername.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, response=MachineResponse.class, message = SwaggerMetadata.MACHINE_RESPONSE),
            @ApiResponse(code = 500, message = SwaggerMetadata.UNEXPECTED_ERROR)})
	@GET
	@Path("homeZ/{printername}")
	@Produces(MediaType.APPLICATION_JSON)
	public MachineResponse homeZ(@PathParam("printername") String printerName) {
		Printer printer = PrinterManager.Instance().getPrinter(printerName);
		if (printer == null) {
			return new MachineResponse("homez", false, "Printer:" + printerName + " not started");
		}
		printer.getPrinterController().executeSetRelativePositioning();
		return new MachineResponse("homez", true, printer.getPrinterController().executeZHome());
	 }
	 
    @ApiOperation(value="Homes the X axis for the Printer specified by the printername.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, response=MachineResponse.class, message = SwaggerMetadata.MACHINE_RESPONSE),
            @ApiResponse(code = 500, message = SwaggerMetadata.UNEXPECTED_ERROR)})
	@GET
	@Path("homeX/{printername}")
	@Produces(MediaType.APPLICATION_JSON)
	public MachineResponse homeX(@PathParam("printername") String printerName) {
		Printer printer = PrinterManager.Instance().getPrinter(printerName);
		if (printer == null) {
			return new MachineResponse("homex", false, "Printer:" + printerName + " not started");
		}
			
		printer.getPrinterController().executeSetRelativePositioning();
		return new MachineResponse("homex", true, printer.getPrinterController().executeXHome());
	}	 
	
    @ApiOperation(value="Homes the Y axis for the Printer specified by the printername.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, response=MachineResponse.class, message = SwaggerMetadata.MACHINE_RESPONSE),
            @ApiResponse(code = 500, message = SwaggerMetadata.UNEXPECTED_ERROR)})
	@GET
	@Path("homeY/{printername}")
	@Produces(MediaType.APPLICATION_JSON)
	public MachineResponse homeY(@PathParam("printername") String printerName) {
		Printer printer = PrinterManager.Instance().getPrinter(printerName);
		if (printer == null) {
			return new MachineResponse("homey", false, "Printer:" + printerName + " not started");
		}
		
		printer.getPrinterController().executeSetRelativePositioning();
		return new MachineResponse("homey", true, printer.getPrinterController().executeYHome());
	}

    @ApiOperation(value="Turns off the motors so that they can be manually turned by hand.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, response=MachineResponse.class, message = SwaggerMetadata.MACHINE_RESPONSE),
            @ApiResponse(code = 500, message = SwaggerMetadata.UNEXPECTED_ERROR)})
	@GET
	@Path("motorsOff/{printername}")
	@Produces(MediaType.APPLICATION_JSON)
	public MachineResponse motorsOff(@PathParam("printername") String printerName) {
		Printer printer = PrinterManager.Instance().getPrinter(printerName);
		if (printer == null) {
			return new MachineResponse("motorsoff", false, "Printer:" + printerName + " not started");
		}
			
		return new MachineResponse("motorsoff", true, printer.getPrinterController().executeMotorsOff());
	}
	 
    @ApiOperation(value="Turns on the motors so that they can't be manually turned by hand.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, response=MachineResponse.class, message = SwaggerMetadata.MACHINE_RESPONSE),
            @ApiResponse(code = 500, message = SwaggerMetadata.UNEXPECTED_ERROR)})
	@GET
	@Path("motorsOn/{printername}")
	@Produces(MediaType.APPLICATION_JSON)
	public MachineResponse motorsOn(@PathParam("printername") String printerName) {
		Printer printer = PrinterManager.Instance().getPrinter(printerName);
		if (printer == null) {
			return new MachineResponse("motorson", false, "Printer:" + printerName + " not started");
		}
		
		return new MachineResponse("motorson", true, printer.getPrinterController().executeMotorsOn());
	}

    @ApiOperation(value="Starts the projector(if it's supported) for the Printer specified by the printername.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, response=MachineResponse.class, message = SwaggerMetadata.MACHINE_RESPONSE),
            @ApiResponse(code = 500, message = SwaggerMetadata.UNEXPECTED_ERROR)})
	@GET
	@Path("startProjector/{printername}")
	@Produces(MediaType.APPLICATION_JSON)
	public MachineResponse startProjector(@PathParam("printername") String printerName) {
		Printer printer = PrinterManager.Instance().getPrinter(printerName);
		if (printer == null) {
			return new MachineResponse("startProjector", false, "Printer:" + printerName + " not started");
		}
		
		try {
			printer.setProjectorPowerStatus(true);
			return new MachineResponse("startProjector", true, "Projector started.");
		} catch (IOException e) {
		    logger.error("Error starting projector for printer:" + printerName, e);
			return new MachineResponse("startProjector", false, e.getMessage());
		}
	}
	 
    @ApiOperation(value="Stops the projector(if it's supported) for the Printer specified by the printername.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, response=MachineResponse.class, message = SwaggerMetadata.MACHINE_RESPONSE),
            @ApiResponse(code = 500, message = SwaggerMetadata.UNEXPECTED_ERROR)})
	@GET
	@Path("stopProjector/{printername}")
	@Produces(MediaType.APPLICATION_JSON)
	public MachineResponse stopProjector(@PathParam("printername") String printerName) {
		Printer printer = PrinterManager.Instance().getPrinter(printerName);
		if (printer == null) {
			return new MachineResponse("stopProjector", false, "Printer:" + printerName + " not started");
		}
		
		try {
			printer.setProjectorPowerStatus(false);
			return new MachineResponse("stopProjector", true, "Projector stopped.");
		} catch (IOException e) {
		    logger.error("Error stopping projector for printer:" + printerName, e);
			return new MachineResponse("stopProjector", false, e.getMessage());
		}
	}
	
    @ApiOperation(value="Starts a print with the specified Printable fileName and Printer name. "
    		+ "This method is only necessary if there is more than one printer running since you need to designate which Printer you would like to print to. ")
    @ApiResponses(value = {
            @ApiResponse(code = 200, response=MachineResponse.class, message = SwaggerMetadata.MACHINE_RESPONSE),
            @ApiResponse(code = 500, message = SwaggerMetadata.UNEXPECTED_ERROR)})
	@GET
	@Path("startJob/{fileName}/{printername}")
	@Produces(MediaType.APPLICATION_JSON)
	public MachineResponse print(@PathParam("fileName") String fileName, @PathParam("printername") String printername) {
    	return startPrintJob(printername, fileName, null);
    }
    
    @ApiOperation(value="Attempt to start a print by specifying the name of the Customizer. ")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = SwaggerMetadata.SUCCESS),
            @ApiResponse(code = 500, message = SwaggerMetadata.UNEXPECTED_ERROR)})
	@POST
	@Path("startJob/{customizerName}")
    @Produces(MediaType.APPLICATION_JSON)
	public MachineResponse printWithCustomizer(@PathParam("customizerName") String customizerName) {
    	Customizer customizer = CustomizerService.INSTANCE.getCustomizer(customizerName, null);
    	if (customizer == null) {
    		return new MachineResponse("startJob", false, "Customizer:" + customizerName + " not found");
    	}
    	return startPrintJob(customizer.getPrinterName(), customizer.getPrintableName() + "." + customizer.getPrintableExtension(), customizer);
	}

    public MachineResponse startPrintJob(String printerName, String printableName, Customizer customizer) {
		Printer printer = PrinterManager.Instance().getPrinter(printerName);
		if (printer == null) {
			return new MachineResponse("start", false, "Printer not started:" + printerName);
		}
		
		//Printer must have been calibrated before it can print
		if (HostProperties.Instance().isForceCalibrationOnFirstUse() && !printer.getConfiguration().isCalibrated()) {
		    logger.error("Printer:{} can't print because it wasn't calibrated", printerName);
			return new MachineResponse("startPrinter", false, "Printer:" + printerName + " must be calibrated before it's first use.");
		}
		
		// Create job
		File selectedFile = new File(HostProperties.Instance().getUploadDir(), printableName); //should already be done by marshalling: java.net.URLDecoder.decode(name, "UTF-8"));//name);
		
		// Delete and Create handled in jobManager
		PrintJob printJob = null;
		try {
			printJob = PrintJobManager.Instance().createJob(selectedFile, printer, customizer);
			return new MachineResponse("start", true, printJob.getId() + "");
		} catch (JobManagerException | AlreadyAssignedException e) {
		    logger.error("Error starting job:" + printableName + " printer:" + printerName, e);
			return new MachineResponse("start", false, e.getMessage());
		}		
	}

	private List<ChartData> getChartData(String seriesString) {
		List<ChartData> seriesData = new ArrayList<ChartData>();
		Matcher matcher = Pattern.compile("([^\\(]+)\\(([^\\)]+)\\)").matcher(seriesString);
		while (matcher.find()) {
			Double start = null;
			Double stop = null;
			Double increment = null;
			StringTokenizer numbers = new StringTokenizer(matcher.group(2), ",");
			while (start == null || stop == null || increment == null) {
				if (start == null) {
					start = new Double(numbers.nextToken());
				} else if (stop == null) {
					stop = new Double(numbers.nextToken());
				} else if (increment == null) {
					increment = new Double(numbers.nextToken());
					seriesData.add(new ChartData(start, stop, increment, matcher.group(1)));
					break;
				}
			}
		}
		
		return seriesData;
	}

	/*Fix the two places where we assign icons to all of the image types in javascript
	Fix all of the test buttons in photonic javascript
	/*private Map<String, Object> buildPrintInProgressSimulation() {properly embed this...
		BufferedImage image = new BufferedImage(200, 200, BufferedImage.TYPE_4BYTE_ABGR_PRE);
		BufferedImage printImage = new BufferedImage(10, 10, BufferedImage.TYPE_4BYTE_ABGR_PRE);
		Map<String, Object> overrides = new HashMap<>();
		overrides.put("platformGraphics", image.getGraphics());
		overrides.put("platformRaster", image.getRaster());
		overrides.put("buildPlatformImage", image);
		overrides.put("buildPlatformGraphics", image.getGraphics());
		overrides.put("buildPlatformRaster", image.getRaster());
		overrides.put("printImage", printImage);
		overrides.put("printGraphics", printImage.getGraphics());
		overrides.put("printRaster", printImage.getRaster());
		overrides.put("centerX", 100);
		overrides.put("centerY", 100);
		return overrides;
	}*/
	
    @ApiOperation(value="Tests out a script using the scripting language(likely javascript) specified in the config.properties via scriptEngineLanguage=[script language]"
    		+ "The returnType parameter passed to this method must match a known Java type or the following format: "
    		+ "requestedJavaReturnTypeOfScript[printerVariableToShowInSeries(start,stop,increment)printerVariableToShowInRange(start,stop,increment)]"
    		+ "Here are some examples: "
    		+ "If your script expects an double return type you would use: java.lang.Double "
    		+ "If your script does not expect a return value you would use: java.lang.Void "
    		+ "If your script wants to return chart data in a json array that describes how 'bulbHours' and 'CURSLICE' affect exposureTime you could use: "
    		+ "java.lang.Double[CURSLICE(1,5,1)buildAreaMM(10000,20000,1000)]")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = SwaggerMetadata.TODO),
            @ApiResponse(code = 500, message = SwaggerMetadata.UNEXPECTED_ERROR)})
	@POST
	@Path("testScript/{printername}/{scriptname}/{returnType}")
	@Produces(MediaType.APPLICATION_JSON)
	public TestingResult testScript(@PathParam("printername")String printerName, @PathParam("scriptname")String scriptName, String javascript, @PathParam("returnType")String expectedReturnTypeString) throws InappropriateDeviceException, JobManagerException {
		Printer printer = getPrinter(printerName);
		PrintJob job = TemplateEngine.buildStubJob(printer);

		try {
			Class expectedReturnType = null;
			List<ChartData> chartData = null;
			Matcher matcher = Pattern.compile("([^\\[]+)\\[([^\\]]+)\\]").matcher(expectedReturnTypeString);
			if (matcher.matches()) {
				expectedReturnTypeString = matcher.group(1);
				chartData = getChartData(matcher.group(2));
				expectedReturnType = Class.forName(expectedReturnTypeString);
			} else {
				expectedReturnType = Class.forName(expectedReturnTypeString);
			}

			ScriptEngine engine = HostProperties.Instance().buildScriptEngine();
			
			if (chartData == null) {
				BufferedImage imageToDisplay = new BufferedImage(200, 200, BufferedImage.TYPE_4BYTE_ABGR_PRE);
				BufferedImage targetImage = new BufferedImage(10, 10, BufferedImage.TYPE_4BYTE_ABGR_PRE);

				Map<String, Object> overrides = new HashMap<>();
				overrides.put("affineTransform", new AffineTransform());
				Object returnObject = TemplateEngine.runScriptInImagingContext(imageToDisplay, targetImage, job, printer, engine, overrides, javascript, scriptName, false);
				//Object returnObject = TemplateEngine.runScript(job, printer, engine, javascript, scriptName, buildPrintInProgressSimulation());
				
				if (expectedReturnType.equals(Void.class)) {
					TestingResult result = new TestingResult(null);
					return result;
				}
				
				if (returnObject == null || !expectedReturnType.isAssignableFrom(returnObject.getClass())) {
					TestingResult result = new TestingResult("This method expects a return type of:" + expectedReturnTypeString + " you provided:" + (returnObject == null?null:returnObject.getClass()), -1);
					return result;
				}
				
				return new TestingResult(returnObject);
			}
			
			Map<String, Object> bindings = new HashMap<>();
			ChartData currentSeries = chartData.get(0);
			ChartData currentData = chartData.get(1);
			double currentSeriesValue = currentSeries.getStart();
			TestingResult.Chart chart = new TestingResult.Chart(new double[currentSeries.getSize()][], new String[currentData.getSize()], new String[currentSeries.getSize()], scriptName);
			for (int currentSeriesIndex = 0; currentSeriesIndex < currentSeries.getSize(); currentSeriesIndex++) {
				double[] dataArray = new double[currentData.getSize()];
				bindings.put(currentSeries.getName(), currentSeriesValue);
				
				double currentDataValue = currentData.getStart();
				for (int valueIndex = 0; valueIndex < currentData.getSize(); valueIndex++) {
					bindings.put(currentData.getName(), currentDataValue);
					chart.getLabels()[valueIndex] = currentData.getName() + ":" + currentDataValue;
					
					Object returnObject = TemplateEngine.runScript(job, printer, engine, javascript, scriptName, bindings);
					if (returnObject == null) {
						TestingResult result = new TestingResult("You returned a null from your script.", -1);
						return result;
					}
					if (!expectedReturnType.isAssignableFrom(returnObject.getClass())) {
						TestingResult result = new TestingResult("This script expects a return type of:" + expectedReturnTypeString + " you provided:" + returnObject.getClass(), -1);
						return result;
					}
					
					dataArray[valueIndex] = ((Number)returnObject).doubleValue();
					currentDataValue += currentData.getIncrement();
				}
				chart.getSeries()[currentSeriesIndex] = currentSeries.getName() + ":" + currentSeriesValue;
				chart.getData()[currentSeriesIndex] = dataArray;
				currentSeriesValue += currentSeries.getIncrement();
			}

			return new TestingResult(chart);
		} catch (ScriptException e) {
			TestingResult result = new TestingResult(e.getMessage(), e.getLineNumber());
			return result;
		} catch (ClassNotFoundException e) {
			TestingResult result = new TestingResult("Couldn't find type:" + expectedReturnTypeString, -1);
			return result;
		}
	}
	
    @ApiOperation(value="Computes the return value, but does not execute the template specified by the post parameters. "
    		+ "Generally the returned template is gcode, but could be any protocol that the user requires.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = SwaggerMetadata.TODO),
            @ApiResponse(code = 500, message = SwaggerMetadata.UNEXPECTED_ERROR)})
	@POST
	@Path("testTemplate/{printername}/{templatename}")
	@Produces(MediaType.APPLICATION_JSON)
	public TestingResult testTemplate(@PathParam("printername")String printerName, @PathParam("templatename")String templateName, String template) throws InappropriateDeviceException {
		Printer printer = getPrinter(printerName);
		return printer.getPrinterController().testTemplate(printer, templateName, template);
	}
	
    @ApiOperation(value="Returns the remaining print material left in the printer using the org.area515.resinprinter.inkdetection.PrintMaterialDetector that is setup in the Printer specified by the printerName.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, response=MachineResponse.class, message = SwaggerMetadata.MACHINE_RESPONSE),
            @ApiResponse(code = 500, message = SwaggerMetadata.UNEXPECTED_ERROR)})
	@GET
	@Path("remainingPrintMaterial/{printername}")
	@Produces(MediaType.APPLICATION_JSON)
	public MachineResponse getRemainingResin(@PathParam("printername") String printerName) throws InappropriateDeviceException, JobManagerException {
		Printer printer = getPrinter(printerName);
		if (printer == null) {
			return new MachineResponse("remainingPrintMaterial", false, "Printer not started:" + printerName);
		}
		
		InkDetector detector = printer.getConfiguration().getSlicingProfile().getSelectedInkConfig().getInkDetector(TemplateEngine.buildStubJob(printer));
		if (detector == null) {
			return new MachineResponse("remainingPrintMaterial", false, "This printer doesn't have a PrintMaterialDetector configured. Save and restart the printer.");
		}
		float materialLeft;
		try {
			materialLeft = detector.performMeasurement();
			return new MachineResponse("remainingPrintMaterial", true, materialLeft + "");
		} catch (IOException e) {
		    logger.error("Error remaining print material for printer:" + printerName, e);
			return new MachineResponse("remainingPrintMaterial", false, e.getMessage());
		}
	}
}