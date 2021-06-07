package org.area515.resinprinter.actions.osscript;

import java.net.URI;
import java.util.Arrays;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.area515.util.IOUtilities;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ExecuteNativeOSCommandRunnable implements Runnable {
	private static final Logger logger = LogManager.getLogger();
	private String[] shellCommands;
	private URI uri;
	
	@JsonProperty
	public String[] getShellCommands() {
		return shellCommands;
	}
	public void setShellCommands(String[] shellCommands) {
		this.shellCommands = shellCommands;
	}

	public URI getUri() {
		return uri;
	}
	public void setUri(URI uri) {
		this.uri = uri;
	}
	
	@Override
	public void run() {
		logger.info("Executing: " + Arrays.toString(shellCommands));
		String[] output = IOUtilities.executeNativeCommand(shellCommands, null, uri == null?null:uri.toString());
		for (String line : output) {
			logger.info(line);
		}
	}
	
	public String toString() {
		return Arrays.toString(shellCommands);
	}
}
