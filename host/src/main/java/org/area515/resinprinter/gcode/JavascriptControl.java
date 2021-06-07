package org.area515.resinprinter.gcode;

import java.io.IOException;

import javax.script.ScriptException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.area515.resinprinter.display.InappropriateDeviceException;
import org.area515.resinprinter.job.JobManagerException;
import org.area515.resinprinter.job.PrintJob;
import org.area515.resinprinter.job.render.RenderingCache;
import org.area515.resinprinter.printer.Printer;
import org.area515.resinprinter.services.TestingResult;
import org.area515.util.TemplateEngine;

public class JavascriptControl extends PrinterController {
	public static Logger logger = LogManager.getLogger();

	public JavascriptControl(Printer printer) {
		super(printer);
	}

    @Override
	public String executeSingleCommand(String commands) {
		try {
			return super.executeCommands(TemplateEngine.buildStubJob(getPrinter()), commands, false);
		} catch (InappropriateDeviceException | JobManagerException e) {
			logger.error(e);
			return e.getMessage();
		}
	}

	@Override
	public String executeCommands(PrintJob printJob, String commands, boolean stopSendingGCodeWhenPrintInactive) throws InappropriateDeviceException {
		if (stopSendingGCodeWhenPrintInactive && !printJob.getPrinter().isPrintActive()) {
			return "Failed to execute due to printer being inactive";
		}
		
		try {
			RenderingCache cache = printJob.getDataAid().cache;
			return TemplateEngine.runScript(
					printJob, 
					printJob.getPrinter(), 
					cache.getOrCreateIfMissing(cache.getCurrentRenderingPointer()).getScriptEngine(), 
					commands, 
					"Printer Commands", 
					null) + "";
		} catch (ScriptException e) {
			throw new InappropriateDeviceException("Error executing script", e);
		}
	}

	public String readWelcomeChitChatFromFirmwareSerialPort() throws IOException {
    	return "No welcome from this printer";
    }
    
	@Override
	public String executeSetAbsolutePositioning() {
		return "Not Supported Yet";
	}
	@Override
	public String executeSetRelativePositioning() {
		return "Not Supported Yet";
	}
	@Override
	public String executeMoveX(double dist) {
		return "Not Supported Yet";
	}
	@Override
	public String executeMoveY(double dist) {
		return "Not Supported Yet";
	}
	@Override
	public String executeMoveZ(double dist) {
		return "Not Supported Yet";
	}
	@Override
	public String executeMotorsOn() {
		return "Not Supported Yet";
	}
	@Override
	public String executeMotorsOff() {
		return "Not Supported Yet";
	}
	@Override
	public String executeXHome() {
		return "Not Supported Yet";
	}
	@Override
	public String executeYHome() {
		return "Not Supported Yet";
	}
	@Override
	public String executeZHome() {
		return "Not Supported Yet";
	}
	@Override
	public String executeHomeAll() {
		return "Not Supported Yet";
	}

	@Override
	public TestingResult testTemplate(Printer printer, String scriptName, String commands) {
		try {
			PrintJob job = TemplateEngine.buildStubJob(printer);
			return new TestingResult(executeCommands(job, commands, false));
		} catch (InappropriateDeviceException | JobManagerException  e) {
			if (e.getCause() instanceof ScriptException) {
				ScriptException se = (ScriptException)e.getCause();
				return new TestingResult(se.getMessage(), se.getLineNumber());
			}
			
			return new TestingResult(e.getMessage(), -1);
		}
	}
}
