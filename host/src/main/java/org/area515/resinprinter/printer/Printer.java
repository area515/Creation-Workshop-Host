package org.area515.resinprinter.printer;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import javax.xml.bind.annotation.XmlTransient;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.area515.resinprinter.display.GraphicsOutputInterface;
import org.area515.resinprinter.display.InappropriateDeviceException;
import org.area515.resinprinter.gcode.PrinterController;
import org.area515.resinprinter.gcode.PrinterDriver;
import org.area515.resinprinter.job.AbstractPrintFileProcessor;
import org.area515.resinprinter.job.AbstractPrintFileProcessor.DataAid;
import org.area515.resinprinter.job.JobStatus;
import org.area515.resinprinter.job.PrintFileProcessor;
import org.area515.resinprinter.projector.ProjectorModel;
import org.area515.resinprinter.serial.SerialCommunicationsPort;
import org.area515.resinprinter.server.HostProperties;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Printer implements Named {
    private static final Logger logger = LogManager.getLogger();
	private PrinterConfiguration configuration;
	
	//For Display
	private GraphicsOutputInterface refreshFrame;
	private boolean started;
	private boolean shutterOpen;
	private Integer bulbHours;
	private long currentSlicePauseTime;
	private String displayDeviceID;
	
	//For Serial Ports
	private SerialCommunicationsPort printerFirmwareSerialPort;
	private SerialCommunicationsPort projectorSerialPort;
	
	//For Job Status
	private volatile JobStatus status = JobStatus.Ready;
	private ReentrantLock statusLock = new ReentrantLock();
	private Condition jobContinued = statusLock.newCondition();
	
	//Controls how templates are interrpretted
	private PrinterController printerControl;

	//Projector model
	private ProjectorModel projectorModel;
	
	public static enum DisplayState {
		Calibration,
		Grid,
		Blank,
		CurrentSlice
	}
	
	//For jaxb/json
	@SuppressWarnings("unused")
	private Printer() {}
	
	public Printer(PrinterConfiguration configuration) throws InappropriateDeviceException {
		this.configuration = configuration;
		
		String driverType = configuration.getMachineConfig().getMotorsDriverConfig().getDriverType();
		for (PrinterDriver currentDriver : HostProperties.Instance().getPrinterDrivers()) {
			if (driverType.equalsIgnoreCase(currentDriver.getDriverName())) {
				printerControl = currentDriver.buildNewPrinterController(this);
			}
		}
	}
	
	@JsonIgnore
	public String getName() {
		return configuration.getName();
	}
	public void setName(String name) {
		configuration.setName(name);
	}
	
	@XmlTransient
	@JsonProperty
	public boolean isPrintInProgress() {
		return status != null && status.isPrintInProgress();
	}
	@JsonIgnore
	public void setPrintInProgress(boolean printInProgress) {
	}
	
	@XmlTransient
	@JsonProperty
	public boolean isPrintPaused() {
		return status != null && getStatus().isPaused();
	}
	@JsonIgnore
	public void setPrintPaused(boolean printInProgress) {
	}
	
	@XmlTransient
	@JsonIgnore
	public boolean isPrintActive() {
		return status != null && status.isPrintActive();
	}

	@XmlTransient
	@JsonProperty
	public boolean isStarted() {
		return started;
	}
	public void setStarted(boolean started) {
		this.started = started;
	}
	
	@XmlTransient
	@JsonProperty
	public JobStatus getStatus() {
		return status;
	}
	
	@JsonIgnore
	public void setStatus(JobStatus status) {
		statusLock.lock();
		try {
			if (this.status != null && this.status.isPaused()) {
				jobContinued.signalAll();
			}
			logger.info("Moving from status:" + this.status + " to status:" + status);
			this.status = status;
			if (!status.isPrintInProgress()) {
				refreshFrame.resetSliceCount();
			}
		} finally {
			statusLock.unlock();
		}
	}
	
	public boolean waitForPauseIfRequired(PrintFileProcessor<?, ?> processor, DataAid aid) {
		statusLock.lock();
		try {
			//Very important that this check is performed
			if (this.status != null && !this.status.isPaused()) {
				return isPrintActive();
			}
			logger.info("Print has been paused.");
			long startPause = System.currentTimeMillis();
			
			try {
				if (processor instanceof AbstractPrintFileProcessor) {
					((AbstractPrintFileProcessor)processor).performPauseGCode(aid);
				}
			} catch (IOException | InappropriateDeviceException e) {
				logger.error("Error while executing pause gCode, but we will recover", e);
			}
			jobContinued.await();
			try {
				if (processor instanceof AbstractPrintFileProcessor) {
					((AbstractPrintFileProcessor)processor).performResumeGCode(aid);
				}
			} catch (IOException | InappropriateDeviceException e) {
				logger.error("Error while executing resume gCode, but we will recover", e);
			}
			
			currentSlicePauseTime += System.currentTimeMillis() - startPause;
			logger.info("Print has resumed.");
			return isPrintActive();
		} catch (InterruptedException e) {
			logger.error("Normal if os is shutting us down", e);
			return isPrintActive();
		} finally {
			statusLock.unlock();
		}
	}
	
	public JobStatus togglePause() {
		statusLock.lock();
		try {
			if (this.status != null && this.status.isPaused()) {
				setStatus(JobStatus.Printing);
				return this.status;
			}
			
			if (this.status == JobStatus.Printing) {
				setStatus(JobStatus.Paused);
			}

			return this.status;
		} finally {
			statusLock.unlock();
		}
	}
	
	public BufferedImage createBufferedImageFromGraphicsOutputInterface(int x, int y) {
		return refreshFrame.buildBufferedImage(x, y);
	}
	
	public void initializeAndAssignGraphicsOutputInterface(final GraphicsOutputInterface device, final String displayDeviceID) {
		this.displayDeviceID = displayDeviceID;
		this.refreshFrame = device.initializeDisplay(displayDeviceID, getConfiguration());
		
		Rectangle screenSize = refreshFrame.getBoundary();
		getConfiguration().getMachineConfig().getMonitorDriverConfig().setDLP_X_Res(screenSize.width);
		getConfiguration().getMachineConfig().getMonitorDriverConfig().setDLP_Y_Res(screenSize.height);
	}
	
	public String getDisplayDeviceID() {
		return displayDeviceID;
	}
	
	public void showBlankImage() {	
		refreshFrame.showBlankImage();
	}
	
	public void showCalibrationImage(int xPixels, int yPixels) {
		refreshFrame.showCalibrationImage(xPixels, yPixels);
	}
	
	public void showGridImage(int pixels) {
		refreshFrame.showGridImage(pixels);
	}
	
	public void showImage(BufferedImage image, boolean performFullUpdate) {
		refreshFrame.showImage(image, performFullUpdate);
	}
	
	@JsonIgnore
	@XmlTransient
	public boolean isDisplayBusy() {
		if (refreshFrame == null) {
			return false;
		}
		
		return refreshFrame.isDisplayBusy();
	}

	@JsonIgnore
	@XmlTransient
	public boolean isProjectorPowerControlSupported() {
		return projectorModel != null;
	}
	
	@JsonIgnore
	@XmlTransient
	public void setProjectorModel(ProjectorModel projectorModel) {
		this.projectorModel = projectorModel;
	}
	@JsonIgnore
	@XmlTransient
	
	public ProjectorModel getProjectorModel() {
		return projectorModel;
	}
	
	public void setProjectorPowerStatus(boolean powerOn) throws IOException {
		if (projectorModel == null) {
			throw new IOException("Projector model couldn't be detected");
		}
		
		if (projectorSerialPort == null) {
			throw new IOException("Serial port not available for projector.");
		}
		
		projectorModel.setPowerState(powerOn, projectorSerialPort);
	}

	public PrinterConfiguration getConfiguration() {
		return configuration;
	}
	public void setConfiguration(PrinterConfiguration configuration) {
		this.configuration = configuration;
	}

	public boolean isShutterOpen() {
		return shutterOpen;
	}
	public void setShutterOpen(boolean shutterOpen) {
		this.shutterOpen = shutterOpen;
	}

	@JsonIgnore
	public Integer getBulbHours() {
		if (bulbHours == null && projectorModel != null) {
			try {
				bulbHours = projectorModel.getBulbHours(projectorSerialPort);
			} catch (IOException e) {
				logger.error("Failed communicating with projector for bulb hours", e);
			}
		}
		
		return bulbHours;
	}
	public void setBulbHours(Integer bulbHours) {
		this.bulbHours = bulbHours;
	}
	
	public Integer getCachedBulbHours() {
		return bulbHours;
	}
	public void setCachedBulbHours(Integer bulbHours) {
		this.bulbHours = bulbHours;
	}
	
	public long getCurrentSlicePauseTime() {
		return currentSlicePauseTime;
	}
	public void setCurrentSlicePauseTime(long currentSlicePauseTime) {
		this.currentSlicePauseTime = currentSlicePauseTime;
	}
	
	@JsonIgnore
	public PrinterController getPrinterController() {
		return printerControl;
	}
	
	public void setPrinterFirmwareSerialPort(SerialCommunicationsPort printerFirmwareSerialPort) {
		this.printerFirmwareSerialPort = printerFirmwareSerialPort;
		logger.info("Firmware serial port set to:" + printerFirmwareSerialPort);
		
		//Read the welcome mat if it's not null
		if (printerFirmwareSerialPort != null) {
			try {
				logger.info("Firmware Welcome chitchat:" + getPrinterController().readWelcomeChitChatFromFirmwareSerialPort());
			} catch (IOException e) {
				logger.error("Error while reading welcome chitchat", e);
			}
		}
	}
	@JsonIgnore
	public SerialCommunicationsPort getPrinterFirmwareSerialPort() {
		return printerFirmwareSerialPort;
	}
	
	public void setProjectorSerialPort(SerialCommunicationsPort projectorSerialPort) {
		this.projectorSerialPort = projectorSerialPort;
	}
	@JsonIgnore
	public SerialCommunicationsPort getProjectorSerialPort() {
		return projectorSerialPort;
	}

	public String toString() {
		return getName() + "(printerFirmwareSerialPort:" + printerFirmwareSerialPort + ", projectorSerialPort:" + projectorSerialPort + " Display:" + displayDeviceID + ")";
	}
	
	public void close() {
		if (printerFirmwareSerialPort != null) {
			printerFirmwareSerialPort.close();
		}
		if (refreshFrame != null) {
			refreshFrame.dispose();
		}
		started = false;
	}

	public void disassociateDisplay() {
		this.bulbHours = null;
		this.refreshFrame = null;
		this.displayDeviceID = null;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((configuration == null) ? 0 : configuration.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Printer other = (Printer) obj;
		if (configuration == null) {
			if (other.configuration != null)
				return false;
		} else if (!configuration.equals(other.configuration))
			return false;
		return true;
	}
}
