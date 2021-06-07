package org.area515.resinprinter.gcode;

import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.area515.resinprinter.display.AlreadyAssignedException;
import org.area515.resinprinter.display.InappropriateDeviceException;
import org.area515.resinprinter.job.JobStatus;
import org.area515.resinprinter.job.PrintJob;
import org.area515.resinprinter.notification.NotificationManager;
import org.area515.resinprinter.printer.MachineConfig;
import org.area515.resinprinter.printer.Printer;
import org.area515.resinprinter.serial.SerialManager;
import org.area515.resinprinter.services.TestingResult;
import org.area515.util.IOUtilities;
import org.area515.util.IOUtilities.ParseState;
import org.area515.util.TemplateEngine;

import freemarker.template.TemplateException;

public abstract class PrinterController {
	public static Logger logger = LogManager.getLogger();
	private int SUGGESTED_TIMEOUT_FOR_ONE_GCODE = 1000 * 60 * 2;//2 minutes
	private Pattern GCODE_RESPONSE_PATTERN = Pattern.compile("(?i)(?:(o?k|e?rror:|a?larm:)(.*)|<?([^>]*)>|\\[?([^]]*)\\])\r?\n");
	
    private Printer printer;
    private ReentrantLock gCodeLock = new ReentrantLock();
    private StringBuilder builder = new StringBuilder();
    private int parseLocation = 0;
    private int gcodeTimeout;
    private boolean restartSerialOnTimeout;
    
    public PrinterController(Printer printer) {
    	this.printer = printer;
    	this.gcodeTimeout = printer.getConfiguration().getMachineConfig().getPrinterResponseTimeoutMillis() != null?printer.getConfiguration().getMachineConfig().getPrinterResponseTimeoutMillis():SUGGESTED_TIMEOUT_FOR_ONE_GCODE;
    	this.restartSerialOnTimeout = printer.getConfiguration().getMachineConfig().getRestartSerialOnTimeout() != null?printer.getConfiguration().getMachineConfig().getRestartSerialOnTimeout():false;
    }
	
    protected Printer getPrinter() {
    	return printer;
    }
    
	private PrinterResponse readUntilOkOrStoppedPrinting(boolean exitIfPrintInactive) throws IOException {
		PrinterResponse line = null;
		StringBuilder responseBuilder = new StringBuilder();
		ParseState state = null;
		Matcher matcher = null;
		do {
			state = IOUtilities.readLine(exitIfPrintInactive?getPrinter():null, getPrinter().getPrinterFirmwareSerialPort(), builder, parseLocation, gcodeTimeout, IOUtilities.CPU_LIMITING_DELAY);
			parseLocation = state.parseLocation;
			if (state.currentLine != null) {
				if (line == null) {
					line = new PrinterResponse();
					line.setFullResponse(responseBuilder);
				}
				responseBuilder.append(state.currentLine);
				matcher = GCODE_RESPONSE_PATTERN.matcher(state.currentLine);
				line.setLastLineMatcher(matcher);
			}
			
			logger.info("lineRead: {}", state.currentLine);
		} while (matcher != null && !matcher.matches());
		if (state.timeout && restartSerialOnTimeout) {
			try {
				getPrinter().getPrinterFirmwareSerialPort().restartCommunications();
			} catch (AlreadyAssignedException | InappropriateDeviceException e) {
				throw new IOException("Problems restarting serial port:" + getPrinter().getPrinterFirmwareSerialPort(), e);
			}
		}
		return line;
	}
	
	private boolean isPausableError(Matcher matcher, PrintJob printJob) {
		if (matcher.group(1) == null || !matcher.group(1).toLowerCase().endsWith("rror:")) {
			return false;
		}
		
		String responseRegEx = printJob.getPrinter().getConfiguration().getMachineConfig().getPauseOnPrinterResponseRegEx();
		return responseRegEx != null && responseRegEx.trim().length() > 0 && matcher.group(2) != null && matcher.group(2).matches(responseRegEx);
	}
	
	String sendCommandToFirmwareSerialPortAndRespectPrinter(PrintJob printJob, String cmd) throws IOException {
		gCodeLock.lock();
        try {
        	if (!cmd.endsWith("\n")) {
        		cmd += "\n";
        	}
        	
        	StringBuilder builder = new StringBuilder();
        	boolean mustAttempt = true;
        	for (int attempt = 0; mustAttempt; attempt++) {
	        	logger.info("Write {}: {}", attempt, cmd);
	        	getPrinter().getPrinterFirmwareSerialPort().write(cmd.getBytes());
	        	PrinterResponse response = readUntilOkOrStoppedPrinting(true);
	        	if (response == null) {
	        		return "";//I think this should be null, but I'm preserving backwards compatibility
	        	}
	        	
	        	if (isPausableError(response.getLastLineMatcher(), printJob)) {
	        		attempt++;
	        		printJob.setErrorDescription(response.getLastLineMatcher().group(2));
	        		logger.info("Received error from printer:" + response.getLastLineMatcher().group(2));
	        		getPrinter().setStatus(JobStatus.PausedWithWarning);
	        		NotificationManager.jobChanged(getPrinter(), printJob);
	        		
	        		//Allow the user to manipulate the printer while paused
	        		gCodeLock.unlock();
	        		try {
	        			mustAttempt = getPrinter().waitForPauseIfRequired(printJob.getPrintFileProcessor(), printJob.getDataAid());
	        		} finally {
	        			gCodeLock.lock();
	        		}
	        	} else {
	        		mustAttempt = false;
	        	}
	        	
	        	builder.append(response.getFullResponse().toString());
        	}
        	
        	return builder.toString();
        } finally {
        	gCodeLock.unlock();
        }
    }
    
    public String executeSingleCommand(String cmd) {
		gCodeLock.lock();
        try {
        	if (!cmd.endsWith("\n")) {
        		cmd += "\n";
        	}
        	
        	logger.info("Write: {}", cmd);
        	getPrinter().getPrinterFirmwareSerialPort().write(cmd.getBytes());
        	PrinterResponse response = readUntilOkOrStoppedPrinting(false);
        	if (response == null) {
        		return "";
        	}
        	
        	return response.getFullResponse().toString();
        } catch (IOException ex) {
        	logger.error("Couldn't send:" + cmd, ex);
        	return "IO Problem!";
        } finally {
        	gCodeLock.unlock();
        }
    }
    
    /**
     * Unfortunately this chitchat isn't like gcode responses. Instead, the reads seem to go on forever without an indication of 
     * when they are going to stop. 
     * 
     * @return
     * @throws IOException
     */
    public String readWelcomeChitChatFromFirmwareSerialPort() throws IOException {
		try {
			StringBuilder builder = new StringBuilder();
			builder.append(IOUtilities.readWithTimeout(getPrinter().getPrinterFirmwareSerialPort(), SerialManager.READ_TIME_OUT, SerialManager.CPU_LIMITING_DELAY));
			builder.append(executeSetAbsolutePositioning());
			return builder.toString();
		} catch (InterruptedException e) {
			return null;
		}
    }

    private void parseCommentCommand(String comment) {
		//If a comment was encountered, parse it to determine if something interesting was in there.
		Pattern delayPattern = Pattern.compile(";\\s*<\\s*Delay\\s*>\\s*(\\d+).*", Pattern.CASE_INSENSITIVE);
		Matcher matcher = delayPattern.matcher(comment);
		if (matcher.matches()) {
			try {
				int sleepTime = Integer.parseInt(matcher.group(1));
				logger.info("Sleep:{}", sleepTime);
				Thread.sleep(sleepTime);
				logger.info("Sleep complete");
			} catch (InterruptedException e) {
				logger.error("Interrupted while waiting for sleep to complete.", e);
			}
		}
    }
    
    public String executeCommands(PrintJob printJob, String commands, boolean stopSendingGCodeWhenPrintInactive) throws InappropriateDeviceException {
		Pattern gCodePattern = Pattern.compile("\\s*([^;]*)\\s*(;.*)?", Pattern.CASE_INSENSITIVE);
		try {
			if (commands == null || commands.trim().isEmpty()) {
				return null;
			}
			
			StringBuilder buffer = new StringBuilder();
			commands = TemplateEngine.buildData(printJob, printJob.getPrinter(), commands);
			if (commands == null) {
				return null;
			}
			
			for (String gcode : commands.split("[\r]?\n")) {
				if (stopSendingGCodeWhenPrintInactive && !printJob.getPrinter().isPrintActive()) {
					break;
				}
				
				if (gcode != null) {
					Matcher matcher = gCodePattern.matcher(gcode);
					if (matcher.matches()) {
						String singleGCode = matcher.group(1);
						String comment = matcher.group(2);
						if (singleGCode != null && singleGCode.trim().length() > 0) {
							buffer.append(sendCommandToFirmwareSerialPortAndRespectPrinter(printJob, singleGCode));
						}
						if (comment != null) {
							parseCommentCommand(comment);
						}
					}
				}
			}
			
			return buffer.toString();
		} catch (IOException | TemplateException e) {
			throw new InappropriateDeviceException(MachineConfig.NOT_CAPABLE, e);
		}
    }
    
    abstract public String executeSetAbsolutePositioning();
    abstract public String executeSetRelativePositioning();
    abstract public String executeMoveX(double dist);
    abstract public String executeMoveY(double dist);
    abstract public String executeMoveZ(double dist);
    abstract public String executeMotorsOn();
    abstract public String executeMotorsOff();
    abstract public String executeXHome();
    abstract public String executeYHome();
    abstract public String executeZHome();
    abstract public String executeHomeAll();
    abstract public TestingResult testTemplate(Printer printer, String scriptName, String commands);
}
