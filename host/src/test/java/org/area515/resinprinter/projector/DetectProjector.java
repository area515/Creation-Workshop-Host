package org.area515.resinprinter.projector;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.area515.resinprinter.printer.ComPortSettings;
import org.area515.resinprinter.serial.JSSCCommPort;
import org.area515.resinprinter.serial.SerialCommunicationsPort;
import org.area515.resinprinter.serial.SerialManager;
import org.area515.resinprinter.serial.SerialManager.DetectedResources;
import org.junit.Assert;
import org.junit.Test;

public class DetectProjector {
    private static final Logger logger = LogManager.getLogger();
    
	@Test
	public void noErrorsDetectingProjector() {
		logger.info("Projector detection test.");

		boolean hasFound = false;
		ComPortSettings newComPortSettings = new ComPortSettings();
		String[] identifiers = SerialManager.Instance().getPortNames();
		for (String currentIdentifier : identifiers) {
			newComPortSettings.setPortName(currentIdentifier);
			
			logger.info("Attempting detection on port:{}", currentIdentifier);
			SerialCommunicationsPort port = new JSSCCommPort();
			DetectedResources resources = SerialManager.Instance().getProjectorModel(port, newComPortSettings);
			if (resources != null) {
				hasFound = true;
			}
			logger.info("  JSSCCommPort projector detection:{}", resources);
		}
		Assert.assertTrue(hasFound);
	}
}
