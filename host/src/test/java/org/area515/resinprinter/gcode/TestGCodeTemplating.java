package org.area515.resinprinter.gcode;

import javax.script.ScriptException;

import org.area515.resinprinter.display.InappropriateDeviceException;
import org.area515.resinprinter.job.AbstractPrintFileProcessor;
import org.area515.resinprinter.job.AbstractPrintFileProcessorTest;
import org.area515.resinprinter.job.PrintJob;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class TestGCodeTemplating {
	@Test
	public void testEmptyGCode() throws Exception {
		AbstractPrintFileProcessor processor = Mockito.mock(AbstractPrintFileProcessor.class, Mockito.CALLS_REAL_METHODS);
		PrintJob printJob = AbstractPrintFileProcessorTest.createTestPrintJob(processor);
		Assert.assertNull(printJob.getPrinter().getPrinterController().executeCommands(printJob, null, true));
		Assert.assertNull(printJob.getPrinter().getPrinterController().executeCommands(printJob, " ", true));
	}
	
	@Test
	public void testBlockOfGCode() throws Exception {
		AbstractPrintFileProcessor processor = Mockito.mock(AbstractPrintFileProcessor.class, Mockito.CALLS_REAL_METHODS);
		PrintJob printJob = AbstractPrintFileProcessorTest.createTestPrintJob(processor);
		String gcodes = "G1 Z${ZLiftDist} F${ZLiftRate}\nG1 Z-${(ZLiftDist - LayerThickness)} F180;\n\nM18\n; <    dElAy >   ${ZLiftDist * ZLiftRate};\n;";
		Mockito.when(printJob.getPrinter().getPrinterController().sendCommandToFirmwareSerialPortAndRespectPrinter(Mockito.any(PrintJob.class), Mockito.any(String.class)))
			.then(new Answer<String>() {
				private int count = 0;

				@Override
				public String answer(InvocationOnMock invocation) throws Throwable {
					switch (count) {
						case 0:
							Assert.assertEquals("G1 Z0 F0", invocation.getArguments()[1]);
							break;
						case 1:
							Assert.assertEquals("G1 Z-0 F180", invocation.getArguments()[1]);
							break;
						case 2:
							Assert.assertEquals("M18", invocation.getArguments()[1]);
							break;
						case 3:
						case 4:
							Assert.fail("Photocentric firmware can't take empty strings");
							break;
					}
					count++;
					return (String)"ok";
				}
			});
		printJob.getPrinter().getPrinterController().executeCommands(printJob, gcodes, true);
	}
}

