package org.area515.resinprinter.test;

import org.area515.resinprinter.gcode.FirmwareResponseSimulation;
import org.area515.resinprinter.gcode.TestGCodeTemplating;
import org.area515.resinprinter.http.TestByteSession;
import org.area515.resinprinter.image.ConvertCWMaskToTransparencyMask;
import org.area515.resinprinter.image.NativeImageTest;
import org.area515.resinprinter.inkdetection.visual.CircleTest;
import org.area515.resinprinter.inkdetection.visual.LineTest;
import org.area515.resinprinter.inkdetection.visual.TestVisualPrintMaterialDetector;
import org.area515.resinprinter.job.AbstractPrintFileProcessorTest;
import org.area515.resinprinter.job.TestCustomizer;
import org.area515.resinprinter.network.LinuxNetworkManagerTest;
import org.area515.resinprinter.printer.DetectFirmwareMock;
import org.area515.resinprinter.projector.HexCodeBasedProjectorTesting;
import org.area515.resinprinter.security.KeystoreSecurityTest;
import org.area515.resinprinter.security.SerializeMessageAsJson;
import org.area515.resinprinter.security.keystore.RendezvousExchange;
import org.area515.resinprinter.services.MachineServiceTest;
import org.area515.resinprinter.services.PrinterServiceTest;
import org.area515.resinprinter.services.TestScriptAndTemplating;
import org.area515.resinprinter.slice.CheckSlicePoints;
import org.area515.resinprinter.stl.ZSlicingGeometry;
import org.area515.resinprinter.util.cron.RunCronPredictor;
import org.area515.util.IOUtilitiesTest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
	NativeImageTest.class,
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
	ConvertCWMaskToTransparencyMask.class,
	CheckSlicePoints.class,
	ZSlicingGeometry.class,
	KeystoreSecurityTest.class,
	SerializeMessageAsJson.class,
	RendezvousExchange.class,
	TestGCodeTemplating.class,
	TestByteSession.class,
	TestCustomizer.class,
	MachineServiceTest.class,
	RunCronPredictor.class,
	PrinterServiceTest.class
})

public class FullTestSuite {
}