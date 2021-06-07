package org.area515.resinprinter.services;

import org.area515.resinprinter.display.InappropriateDeviceException;
import org.area515.resinprinter.job.JobManagerException;
import org.area515.resinprinter.printer.Printer;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class TestScriptAndTemplating {
	@Test
	public void testzLiftDistanceCalculator() throws InappropriateDeviceException, JobManagerException {
		Printer printer = PrinterService.INSTANCE.createTemplatePrinter();
		PrinterService mockPrinterService = Mockito.mock(PrinterService.class, Mockito.RETURNS_DEFAULTS);
		Mockito.when(mockPrinterService.getPrinter(Mockito.anyString())).thenReturn(printer);
		Mockito.when(mockPrinterService.testScript(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString())).thenCallRealMethod();
		TestingResult result = mockPrinterService.testScript("Mock Printer", "lift distance", printer.getConfiguration().getSlicingProfile().getzLiftDistanceCalculator(), "java.lang.Number");
		Assert.assertFalse(result.getErrorDescription(), result.isError());
	}
	
	@Test
	public void testzLiftSpeedCalculator() throws InappropriateDeviceException, JobManagerException {
		Printer printer = PrinterService.INSTANCE.createTemplatePrinter();
		PrinterService mockPrinterService = Mockito.mock(PrinterService.class, Mockito.RETURNS_DEFAULTS);
		Mockito.when(mockPrinterService.getPrinter(Mockito.anyString())).thenReturn(printer);
		Mockito.when(mockPrinterService.testScript(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString())).thenCallRealMethod();
		TestingResult result = mockPrinterService.testScript("Mock Printer", "lift speed", printer.getConfiguration().getSlicingProfile().getzLiftSpeedCalculator(), "java.lang.Number");
		Assert.assertFalse(result.getErrorDescription(), result.isError());
	}
	
	@Test
	public void testExposureTimeCalculator() throws InappropriateDeviceException, JobManagerException {
		Printer printer = PrinterService.INSTANCE.createTemplatePrinter();
		PrinterService mockPrinterService = Mockito.mock(PrinterService.class, Mockito.RETURNS_DEFAULTS);
		Mockito.when(mockPrinterService.getPrinter(Mockito.anyString())).thenReturn(printer);
		Mockito.when(mockPrinterService.testScript(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString())).thenCallRealMethod();
		TestingResult result = mockPrinterService.testScript("Mock Printer", "exposure time", printer.getConfiguration().getSlicingProfile().getExposureTimeCalculator(), "java.lang.Number");
		Assert.assertFalse(result.getErrorDescription(), result.isError());
	}
	
	@Test
	public void testGraphCalculator() throws InappropriateDeviceException, JobManagerException {
		Printer printer = PrinterService.INSTANCE.createTemplatePrinter();
		PrinterService mockPrinterService = Mockito.mock(PrinterService.class, Mockito.RETURNS_DEFAULTS);
		Mockito.when(mockPrinterService.getPrinter(Mockito.anyString())).thenReturn(printer);
		Mockito.when(mockPrinterService.testScript(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString())).thenCallRealMethod();
		TestingResult result = mockPrinterService.testScript("Mock Printer", "I'm not sure", "if ($CURSLICE >= $NumFirstLayers) {12 * $buildAreaMM} else {2 * $buildAreaMM}", "java.lang.Double[$CURSLICE(9,11,1)$buildAreaMM(0.0,100.0,20)]");
		Assert.assertFalse(result.getErrorDescription(), result.isError());
		Assert.assertEquals(result.getResult().getClass(), TestingResult.Chart.class);
	}
	
	@Test
	public void testProjectorGradientCalculator() throws InappropriateDeviceException, JobManagerException {
		Printer printer = PrinterService.INSTANCE.createTemplatePrinter();
		PrinterService mockPrinterService = Mockito.mock(PrinterService.class, Mockito.RETURNS_DEFAULTS);
		Mockito.when(mockPrinterService.getPrinter(Mockito.anyString())).thenReturn(printer);
		Mockito.when(mockPrinterService.testScript(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString())).thenCallRealMethod();
		TestingResult result = mockPrinterService.testScript("Mock Printer", "projector gradient", printer.getConfiguration().getSlicingProfile().getProjectorGradientCalculator(), "java.awt.Paint");
		Assert.assertFalse(result.getErrorDescription(), result.isError());
	}
	
	@Test
	public void testSyntaxErrorCalculator() throws InappropriateDeviceException, JobManagerException {
		Printer printer = PrinterService.INSTANCE.createTemplatePrinter();
		PrinterService mockPrinterService = Mockito.mock(PrinterService.class, Mockito.RETURNS_DEFAULTS);
		Mockito.when(mockPrinterService.getPrinter(Mockito.anyString())).thenReturn(printer);
		Mockito.when(mockPrinterService.testScript(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString())).thenCallRealMethod();
		TestingResult result = mockPrinterService.testScript("Mock Printer", "Syntax error", "asdf)dasd;d{", "java.awt.Paint");
		Assert.assertTrue(result.isError());
	}
	
}
