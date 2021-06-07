package org.area515.resinprinter.gcode;

import org.area515.resinprinter.display.InappropriateDeviceException;
import org.area515.resinprinter.printer.Printer;

public class PrinterDriver {
	private String driverClassName;
	private String driverName;
	private String prettyName;
	
	public String getDriverClassName() {
		return driverClassName;
	}
	public void setDriverClassName(String driverClassName) {
		this.driverClassName = driverClassName;
	}
	
	public String getDriverName() {
		return driverName;
	}
	public void setDriverName(String driverName) {
		this.driverName = driverName;
	}
	
	public String getPrettyName() {
		return prettyName;
	}
	public void setPrettyName(String prettyName) {
		this.prettyName = prettyName;
	}
	
	public PrinterController buildNewPrinterController(Printer printer) throws InappropriateDeviceException {
		try {
			@SuppressWarnings("unchecked")
			Class<PrinterController> gCodeClass = (Class<PrinterController>)Class.forName(driverClassName);
			return (PrinterController)gCodeClass.getConstructors()[0].newInstance(printer);
		} catch (ClassNotFoundException e) {
			throw new InappropriateDeviceException("Couldn't find GCode controller for:" + driverClassName, e);
		} catch (SecurityException e) {
			throw new InappropriateDeviceException("No permission to create class for:" + driverClassName, e);
		} catch (Exception e) {
			throw new InappropriateDeviceException("Couldn't create instance for:" + driverClassName, e);
		}
	}
}
