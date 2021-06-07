package org.area515.resinprinter.gcode;

import java.io.IOException;

import org.area515.resinprinter.job.PrintJob;
import org.area515.resinprinter.printer.MachineConfig;
import org.area515.resinprinter.printer.Printer;
import org.area515.resinprinter.printer.PrinterConfiguration;
import org.area515.resinprinter.serial.SerialCommunicationsPort;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.Mockito;

@RunWith(Parameterized.class)
public class FirmwareResponseSimulation {
	private String data;
	
	public FirmwareResponseSimulation(String data) {
		this.data = data;
	}
	
	public static PrintJob buildTestPrintJob() {
		Printer printer = Mockito.mock(Printer.class);
		PrintJob printJob = Mockito.mock(PrintJob.class);
		SerialCommunicationsPort serial = org.mockito.Mockito.mock(SerialCommunicationsPort.class);
		MachineConfig machine = org.mockito.Mockito.mock(MachineConfig.class);
		PrinterConfiguration configuration = org.mockito.Mockito.mock(PrinterConfiguration.class);
		
		Mockito.when(printer.getConfiguration()).thenReturn(configuration);
		Mockito.when(printer.getConfiguration().getMachineConfig()).thenReturn(machine);
		Mockito.when(printer.getPrinterFirmwareSerialPort()).thenReturn(serial);
		Mockito.when(printJob.getPrinter()).thenReturn(printer);
		return printJob;
	}
	
	@Test
	public void GRBLTest() throws IOException {
		PrintJob printJob = buildTestPrintJob();
		Printer printer = printJob.getPrinter();
		PrinterController control = new eGENERICGCodeControl(printer);
		if (data == null) {
			Mockito.when(printer.getPrinterFirmwareSerialPort().read()).thenReturn(null).thenReturn(null);
		} else {
			Mockito.when(printer.getPrinterFirmwareSerialPort().read()).thenReturn(data.getBytes()).thenReturn(null);
		}
		Assert.assertEquals(data==null?"":data, control.sendCommandToFirmwareSerialPortAndRespectPrinter(printJob, "G21"));
	}
	
	@Parameters
	public static Object[] data() {
		return new Object[][]{
				{null},
				{"k\n"},
				{"K\r\n"},
				{"Ok\n"},
				{"ok\r\n"},
				{"Rror: This is the error message.\n"},
				{"rRor: This is the error message.\r\n"},
				{"Error: This is the error message.\n"},
				{"Error: This is the error message.\r\n"},
				{"Larm: This is the alarm message.\n"},
				{"lArm: This is the alarm message.\r\n"},
				{"Alarm: This is the alarm message.\n"},
				{"alaRm: This is the alarm message.\r\n"},
				{"feedback message>\n"},
				{"feedback message>\r\n"},
				{"<feedback message>\n"},
				{"<feedback message>\r\n"},
				{"status report>\n"},
				{"status report>\r\n"},
				{"<status report>\n"},
				{"<status report>\r\n"},
				{"X:0.000 Y:3.000 Z:196.000 E:0.000 Count X: 0.000 Y:2.997 Z:196.000\nok\n"},
				};
	}
}
