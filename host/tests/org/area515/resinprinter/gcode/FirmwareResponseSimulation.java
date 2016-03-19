package org.area515.resinprinter.gcode;

import java.io.IOException;

import org.area515.resinprinter.job.PrintJob;
import org.area515.resinprinter.printer.Printer;
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
	
	@Test
	public void GRBLTest() throws IOException {
		Printer printer = Mockito.mock(Printer.class);
		PrintJob printJob = Mockito.mock(PrintJob.class);
		GCodeControl control = new eGENERICGCodeControl(printer);
		SerialCommunicationsPort serial = org.mockito.Mockito.mock(SerialCommunicationsPort.class);
		Mockito.when(printer.getPrinterFirmwareSerialPort()).thenReturn(serial);
		Mockito.when(printJob.getPrinter()).thenReturn(printer);
		if (data == null) {
			Mockito.when(serial.read()).thenReturn(null).thenReturn(null);
		} else {
			Mockito.when(serial.read()).thenReturn(data.getBytes()).thenReturn(null);
		}
		Assert.assertEquals(data==null?"":data, control.sendGcodeAndRespectPrinter(printJob, "G21"));
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
				};
	}
}
