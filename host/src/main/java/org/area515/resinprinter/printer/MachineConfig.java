package org.area515.resinprinter.printer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.area515.resinprinter.display.ControlFlow;
import org.area515.resinprinter.display.FullScreenMode;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

@XmlRootElement(name="MachineConfig")
public class MachineConfig implements Named {
	public static final String NOT_CAPABLE = "Your printer configuration isn't capable of this feature";
	
	public static class DisplayedControls {
	}
	
	public static class CorrectionMask {
	}
	
	public static class MotorsDriverConfig {
		@XmlElement(name="DriverType")
		private String driverType = "eGENERIC";
		@XmlElement(name="ComPortSettings")
		private ComPortSettings comPortSettings;
		
		@XmlTransient
		public ComPortSettings getComPortSettings() {
			return comPortSettings;
		}
		public void setComPortSettings(ComPortSettings comPortSettings) {
			this.comPortSettings = comPortSettings;
		}
		
		@XmlTransient
		public String getDriverType() {
			return driverType;
		}
		public void setDriverType(String driverType) {
			this.driverType = driverType;
		}
	}

	public static class MonitorDriverConfig {
		@XmlElement(name="DLP_X_Res")
		private double dLP_X_Res;
		@XmlElement(name="DLP_Y_Res")
		private double dLP_Y_Res;
		@XmlElement(name="MonitorID")
		private String monitorID;
		@XmlElement(name="OSMonitorID")
		private String osMonitorID;
		@XmlElement(name="DisplayCommEnabled")
		private boolean displayCommEnabled;
		@XmlElement(name="ComPortSettings")
		private ComPortSettings comPortSettings;
		@XmlElement(name="MonitorTop")
		private int monitorTop;
		@XmlElement(name="MonitorLeft")
		private int monitorLeft;
		@XmlElement(name="MonitorRight")
		private int monitorRight;
		@XmlElement(name="MonitorBottom")
		private int monitorBottom;
		@XmlElement(name="UseMask")
		private boolean useMask;
		@XmlElement(name="FullScreenMode")
		private FullScreenMode fullScreenMode;
		
		@XmlTransient
		public boolean isUseMask() {
			return useMask;
		}
		public void setUseMask(boolean useMask) {
			this.useMask = useMask;
		}
		
		@XmlTransient
		public double getDLP_X_Res() {
			return dLP_X_Res;
		}
		public void setDLP_X_Res(double dLP_X_Res) {
			this.dLP_X_Res = dLP_X_Res;
		}

		@XmlTransient
		public double getDLP_Y_Res() {
			return dLP_Y_Res;
		}
		public void setDLP_Y_Res(double dLP_Y_Res) {
			this.dLP_Y_Res = dLP_Y_Res;
		}
		
		@XmlTransient
		public ComPortSettings getComPortSettings() {
			return comPortSettings;
		}
		public void setComPortSettings(ComPortSettings comPortSettings) {
			this.comPortSettings = comPortSettings;
		}
		
		@XmlTransient
		public FullScreenMode getFullScreenMode() {
			if (fullScreenMode == null) {
				return FullScreenMode.NeverUseFullScreen;
			}
			
			return fullScreenMode;
		}
		public void setFullScreenMode(FullScreenMode fullScreenMode) {
			this.fullScreenMode = fullScreenMode;
		}
	}
	
	@XmlAttribute(name="FileVersion")
	private int fileVersion;
	@XmlElement(name="PlatformXSize")
	private double platformXSize;
	@XmlElement(name="PlatformYSize")
	private double platformYSize;
	@XmlElement(name="PlatformZSize")
	private double platformZSize;
	@XmlElement(name="MaxXFeedRate")
	private int maxXFeedRate;
	@XmlElement(name="MaxYFeedRate")
	private int maxYFeedRate;
	@XmlElement(name="MaxZFeedRate")
	private int maxZFeedRate;
	@XmlElement(name="XRenderSize")
	private int xRenderSize;
	@XmlElement(name="YRenderSize")
	private int yRenderSize;
	@XmlElement(name="DisplayedControls")
	private DisplayedControls controls;
	@XmlElement(name="MachineType")
	private String machineType;
	@XmlElement(name="MultiMonType")
	private String multiMonType;
	@XmlElement(name="MotorsDriverConfig")
	private MotorsDriverConfig motorsDriverConfig;
	@XmlElement(name="MonitorDriverConfig")
	private MonitorDriverConfig monitorDriverConfig;
	@XmlElement(name="PauseOnPrinterResponseRegEx")
	private String pauseOnPrinterResponseRegEx;
	@XmlElement(name="PrinterResponseTimeoutMillis")
	private Integer printerResponseTimeoutMillis;
	@XmlElement(name="OverrideModelNormalsWithRightHandRule")
	private Boolean overrideModelNormalsWithRightHandRule;
	@XmlElement(name="RestartSerialOnTimeout")
	private Boolean restartSerialOnTimeout;
	private ControlFlow footerExecution;
	
	private String name;

	@XmlTransient
	@JsonProperty
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	
	@XmlElement(name="FooterExecutionHandling")
	@JsonProperty
	public ControlFlow getFooterExecutionHandling() {
		if (footerExecution == null) {
			footerExecution = ControlFlow.OnSuccess;
		}
		return footerExecution;
	}
	public void setFooterExecutionHandling(ControlFlow footerExecution) {
		this.footerExecution = footerExecution;
	}
	
	@XmlTransient
	public String getPauseOnPrinterResponseRegEx() {
		return pauseOnPrinterResponseRegEx;
	}
	public void setPauseOnPrinterResponseRegEx(String pauseOnPrinterResponseRegEx) {
		this.pauseOnPrinterResponseRegEx = pauseOnPrinterResponseRegEx;
	}

	@XmlTransient
	public double getPlatformXSize() {
		return platformXSize;
	}
	public void setPlatformXSize(double platformXSize) {
		this.platformXSize = platformXSize;
	}

	@XmlTransient
	public double getPlatformYSize() {
		return platformYSize;
	}
	public void setPlatformYSize(double platformYSize) {
		this.platformYSize = platformYSize;
	}

	@XmlTransient
	public double getPlatformZSize() {
		return platformZSize;
	}
	public void setPlatformZSize(double platformZSize) {
		this.platformZSize = platformZSize;
	}

	@XmlTransient
	public int getxRenderSize() {
		return xRenderSize;
	}
	public void setxRenderSize(int xRenderSize) {
		this.xRenderSize = xRenderSize;
	}

	@XmlTransient
	public int getyRenderSize() {
		return yRenderSize;
	}
	public void setyRenderSize(int yRenderSize) {
		this.yRenderSize = yRenderSize;
	}
	
	@XmlTransient
	public MotorsDriverConfig getMotorsDriverConfig() {
		return motorsDriverConfig;
	}
	public void setMotorsDriverConfig(MotorsDriverConfig motorsDriverConfig) {
		this.motorsDriverConfig = motorsDriverConfig;
	}
	
	@XmlTransient
	public MonitorDriverConfig getMonitorDriverConfig() {
		return monitorDriverConfig;
	}
	public void setMonitorDriverConfig(MonitorDriverConfig monitorDriverConfig) {
		this.monitorDriverConfig = monitorDriverConfig;
	}
	
	@JsonIgnore
	public Integer getDisplayIndex() {
		if (monitorDriverConfig == null || monitorDriverConfig.monitorID == null) {
			return null;
		}
		Pattern displayPattern = Pattern.compile(".*?([\\d]{1,2})");
		Matcher matcher = displayPattern.matcher(monitorDriverConfig.monitorID);
		if (!matcher.matches()) {
			return 0;
		}
		
		return Integer.parseInt(matcher.group(1)) - 1;		
	}
	
	@XmlTransient
	public Boolean getOverrideModelNormalsWithRightHandRule() {
		return overrideModelNormalsWithRightHandRule;
	}
	public void setOverrideModelNormalsWithRightHandRule(Boolean overrideModelNormalsWithRightHandRule) {
		this.overrideModelNormalsWithRightHandRule = overrideModelNormalsWithRightHandRule;
	}
	
	@XmlTransient
	public Integer getPrinterResponseTimeoutMillis() {
		return printerResponseTimeoutMillis;
	}
	public void setPrinterResponseTimeoutMillis(Integer printerResponseTimeoutMillis) {
		this.printerResponseTimeoutMillis = printerResponseTimeoutMillis;
	}
	
	@XmlTransient
	public Boolean getRestartSerialOnTimeout() {
		return restartSerialOnTimeout;
	}
	public void setRestartSerialOnTimeout(Boolean restartSerialOnTimeout) {
		this.restartSerialOnTimeout = restartSerialOnTimeout;
	}
	
	@XmlTransient
	public String getOSMonitorID() {
		return monitorDriverConfig.osMonitorID;
	}
	public void setOSMonitorID(String name) {
		Pattern displayPattern = Pattern.compile(".*?([\\d]{1,2})");
		Matcher matcher = displayPattern.matcher(name);
		if (matcher.matches()) {
			Integer index = Integer.parseInt(matcher.group(1)) + 1;
			monitorDriverConfig.monitorID = "DISPLAY" + index;
		}
		
		monitorDriverConfig.osMonitorID = name;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
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
		MachineConfig other = (MachineConfig) obj;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		return true;
	}
	
	public String toString() {
		return getName();
	}
}
