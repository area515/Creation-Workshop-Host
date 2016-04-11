package org.area515.resinprinter.test;

import org.area515.resinprinter.gcode.FirmwareResponseSimulation;
import org.area515.resinprinter.image.ConvertCWMaskToTransparencyMask;
import org.area515.resinprinter.inkdetection.visual.CircleTest;
import org.area515.resinprinter.inkdetection.visual.LineTest;
import org.area515.resinprinter.inkdetection.visual.TestVisualPrintMaterialDetector;
import org.area515.resinprinter.job.AbstractPrintFileProcessorTest;
import org.area515.resinprinter.network.LinuxNetworkManagerTest;
import org.area515.resinprinter.printer.DetectFirmwareMock;
import org.area515.resinprinter.projector.HexCodeBasedProjectorTesting;
import org.area515.resinprinter.services.TestScriptAndTemplating;
import org.area515.util.IOUtilitiesTest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
	IOUtilitiesTest.class,
	TestVisualPrintMaterialDetector.class,
	CircleTest.class,
	LineTest.class,
	LinuxNetworkManagerTest.class,
	AbstractPrintFileProcessorTest.class,
	TestScriptAndTemplating.class,
	HexCodeBasedProjectorTesting.class,
	DetectFirmwareMock.class,
	FirmwareResponseSimulation.class,
	ConvertCWMaskToTransparencyMask.class
})

public class FullTestSuite {
}