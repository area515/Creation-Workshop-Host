package org.area515.resinprinter.usbimport;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.area515.resinprinter.plugin.Feature;
import org.area515.resinprinter.server.HostProperties;
import org.area515.resinprinter.server.Main;
import org.area515.resinprinter.services.PrintableService;
import org.area515.resinprinter.util.cron.CronFeature.CronTask;
import org.area515.util.PrintFileFilter;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class USBUploader implements Feature {
    private static final Logger logger = LogManager.getLogger();
	private HashMap<String, File> masterRoots;
	private ScheduledFuture<?> future;
	
	public static class USBUploaderSettings {
		private String[] configuredRoots;

		public String[] getConfiguredRoots() {
			return configuredRoots;
		}
		public void setConfiguredRoots(String[] configuredRoots) {
			this.configuredRoots = configuredRoots;
		}
	}

	private List<File> listRoots(String[] configRoots) {
		List<File> allRoots = new ArrayList<>();
		for (File currentFile : File.listRoots()) {
			logger.debug("listroot:" + currentFile);
			allRoots.add(currentFile);
		}
		for (String root : configRoots) {
			File nextFile = new File(root);
			logger.debug("Config root:" + nextFile);
			if (nextFile.exists()) {
				logger.debug("Existing Child under config root:" + nextFile);
				for (File currentFile : nextFile.listFiles()) {
					logger.debug("Added root:" + currentFile);
					allRoots.add(currentFile);
				}
			}
		}
		
		logger.debug("allRoots:" + allRoots);
		return allRoots;
	}
	
	private void uploadFromRoot(File root) {
		for (File currentFile : root.listFiles(PrintFileFilter.INSTANCE)) {
			logger.debug("Found potential printable:" + currentFile);
			try (InputStream stream = new BufferedInputStream(new FileInputStream(currentFile))) {
				PrintableService.uploadFile(currentFile.getName(), stream, HostProperties.Instance().getUploadDir());
			} catch (IOException e) {
				logger.error("Couldn't upload file:" + currentFile, e);
			}
		}
	}
	
	@Override
	public void start(URI uri, String settingsString) {
		ObjectMapper mapper = new ObjectMapper(new JsonFactory());
		final USBUploaderSettings[] settings = new USBUploaderSettings[1];
		try {
			settings[0] = mapper.readValue(settingsString, new TypeReference<USBUploaderSettings>(){});
		} catch (IOException e) {
			throw new IllegalArgumentException(settingsString + " didn't parse correctly.", e);
		}

		masterRoots = new HashMap<>();
		for (File root : listRoots(settings[0].getConfiguredRoots())) {
			masterRoots.put(root.getAbsolutePath(), root);
		}
		
		future = Main.GLOBAL_EXECUTOR.scheduleWithFixedDelay(new Runnable(){
			@Override
			public void run() {
				Map<String, File> negativeList = new HashMap<>();
				negativeList.putAll(masterRoots);
				logger.debug("MasterRoots:" + masterRoots);
				for (File root : listRoots(settings[0].getConfiguredRoots())) {
					File foundItem = negativeList.remove(root.getAbsolutePath());
					if (foundItem == null) {
						uploadFromRoot(root);
						
						//Since we've uploaded all of the files from this root, we don't want to do it again.
						logger.debug("NewRoot:" + foundItem);
						masterRoots.put(root.getAbsolutePath(), root);
					}
				}
				
				//These are the USB drives that were unplugged
				logger.debug("Unplugged drives:" + negativeList);
				for (String name : negativeList.keySet()) {
					masterRoots.remove(name);
				}
			}
		}, 0, 3, TimeUnit.SECONDS);
	}

	@Override
	public void stop() {
		future.cancel(true);
		future = null;
	}
}
