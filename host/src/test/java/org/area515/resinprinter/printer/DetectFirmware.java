package org.area515.resinprinter.printer;

import java.util.ArrayList;
import java.util.Collections;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.area515.resinprinter.serial.JSSCCommPort;
import org.area515.resinprinter.serial.SerialCommunicationsPort;
import org.area515.resinprinter.serial.SerialManager;
import org.area515.resinprinter.test.HardwareCompatibilityTestSuite;
import org.junit.Assert;
import org.junit.Test;

public class DetectFirmware {
    private static final Logger logger = LogManager.getLogger();
	
	@Test
	public void noErrorsDetectingFirmware() {
		logger.info("Firmware detection test.");

		ComPortSettings newComPortSettings = new ComPortSettings();
		boolean hasFound = false;
		for (long speed : HardwareCompatibilityTestSuite.COMMON_SPEEDS) {
			newComPortSettings.setSpeed(speed);
			newComPortSettings.setDatabits(8);
			newComPortSettings.setParity("NONE");
			newComPortSettings.setStopbits("1");
			
			String[] identifiers = SerialManager.Instance().getPortNames();
			for (String currentIdentifier : identifiers) {
				newComPortSettings.setPortName(currentIdentifier);
				
				logger.info("Port:{} Baud:{}", currentIdentifier, speed);
				
				SerialCommunicationsPort port = new JSSCCommPort();
				Boolean lastValue = null;
				for (int t = 0; t < 10; t++) {
					boolean found = SerialManager.Instance().is3dFirmware(port, newComPortSettings);
					logger.info("  {}. JSSCCommPort firmware detection:{}", t, found);
					if (lastValue == null) {
						lastValue = found;
					} else {
						Assert.assertEquals((boolean)lastValue, (boolean)found);
					}
					if (found) {
						hasFound = true;
					} else {
						break;
					}
				}
			}
		}
		
		Assert.assertTrue(hasFound);
	}
}
