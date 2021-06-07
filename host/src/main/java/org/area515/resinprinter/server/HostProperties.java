package org.area515.resinprinter.server;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipOutputStream;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.util.JAXBSource;
import javax.xml.namespace.QName;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.area515.resinprinter.display.AlreadyAssignedException;
import org.area515.resinprinter.display.GraphicsOutputInterface;
import org.area515.resinprinter.display.InappropriateDeviceException;
import org.area515.resinprinter.gcode.PrinterController;
import org.area515.resinprinter.gcode.PrinterDriver;
import org.area515.resinprinter.job.Customizer;
import org.area515.resinprinter.job.PrintFileProcessor;
import org.area515.resinprinter.network.LinuxNetworkManager;
import org.area515.resinprinter.network.NetworkManager;
import org.area515.resinprinter.notification.NotificationManager;
import org.area515.resinprinter.notification.Notifier;
import org.area515.resinprinter.plugin.Feature;
import org.area515.resinprinter.printer.MachineConfig;
import org.area515.resinprinter.printer.Named;
import org.area515.resinprinter.printer.PrinterConfiguration;
import org.area515.resinprinter.printer.SlicingProfile;
import org.area515.resinprinter.projector.HexCodeBasedProjector;
import org.area515.resinprinter.projector.ProjectorModel;
import org.area515.resinprinter.security.UserManagementException;
import org.area515.resinprinter.security.keystore.KeystoreLoginService;
import org.area515.resinprinter.serial.JSSCCommPort;
import org.area515.resinprinter.serial.SerialCommunicationsPort;
import org.area515.resinprinter.services.UserService;
import org.area515.resinprinter.util.security.PhotonicUser;
import org.area515.util.IOUtilities;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class HostProperties {
    private static final Logger logger = LogManager.getLogger();
    
    public static final String PRINTER_CONFIG_PROPERTIES = "3dPrinterDirconfig.properties";
    public static final String CONFIG_PROPERTIES = "config.properties";
    
	public static String PROFILES_EXTENSION = ".slicing";
	public File PROFILES_DIR = new File(System.getProperty("user.home"), "Profiles");
	public static String MACHINE_EXTENSION = ".machine";
	public File MACHINE_DIR = new File(System.getProperty("user.home"), "Machines");
	public static String PRINTER_EXTENSION = ".printer";
	public File PRINTER_DIR = new File(System.getProperty("user.home"), "3dPrinters");	
	public static String CUSTOMIZER_EXTENSION = ".customizer";
	public File CUSTOMIZER_DIR = new File(System.getProperty("user.home"), "Customizers");
	
	private static HostProperties INSTANCE = null;
	private File uploadDir;
	private File printDir;
	private boolean fakeSerial = false;
	private boolean removeJobOnCompletion = true;
	private boolean forceCalibrationOnFirstUse = false;
	private boolean limitLiveStreamToOneCPU = false;
	private ConcurrentHashMap<String, PrinterConfiguration> configurations;
	private Map<Class<Feature>, String> featureClasses = new HashMap<Class<Feature>, String>();
	private List<Class<Notifier>> notificationClasses = new ArrayList<Class<Notifier>>();
	private List<PrintFileProcessor<?,?>> printFileProcessors = new ArrayList<PrintFileProcessor<?,?>>();
	private List<GraphicsOutputInterface> displayDevices = new ArrayList<GraphicsOutputInterface>();
	private Class<SerialCommunicationsPort> serialPortClass;
	private Class<NetworkManager> networkManagerClass;
	
	private String releaseTagName;
	private List<String> visibleCards;
	private String hexCodeBasedProjectorsJson;
	private String skinsStringJson;
	private String printerDriversStringJson;
	private String forwardHeader;
	private CountDownLatch hostReady = new CountDownLatch(1);
	private String scriptEngineLanguage = null;
	private String printerProfileRepo;
	private boolean useAuthentication;

	//SSL settings:
	private boolean useSSL;
	private int printerHostPort;
	private File sslKeystoreFile;
	private String sslKeypairPassword;
	private String sslKeystorePassword;
	private File userKeystoreFile;
	private String userKeystorePassword;
	private String securityRealmName;
	//Optional SSL settings:
	private String externallyAccessableName;
	
	//These are OS specific commands
	private String[] streamingCommand;
	private String[] imagingCommand;
	private String[] dumpStackTraceCommand;
	private String[] rebootCommand;
	
	private ScriptEngine sharedScriptEngine;
	
	public synchronized static HostProperties Instance() {
		if (INSTANCE == null) {
			INSTANCE = new HostProperties();

			//Put any calls here if they require an initialized HostProperties();
			if (INSTANCE.isUseAuthentication()) {
				//We do this to make sure that people can still login after our keystore migration
				INSTANCE.migratePropertyUserUserManagementFeature(INSTANCE.getMergedProperties(), INSTANCE.securityRealmName);
			}
		}
		
		return INSTANCE;
	}
	
	private Properties getMergedProperties() {
		Properties configurationProperties = getClasspathProperties();
		Properties overridenConfigurationProperties = loadOverriddenConfigurationProperties();
		if (overridenConfigurationProperties != null) {
			configurationProperties.putAll(overridenConfigurationProperties);
		}
		
		return configurationProperties;
	}
	
	/**
	 * ScriptEngines can be used multithreaded, except that they should not share Bindings
	 * 
	 * @return the sharedScriptEngine
	 */
	public ScriptEngine getSharedScriptEngine() {
		return this.sharedScriptEngine;
	}

	private void migratePropertyUserUserManagementFeature(Properties configurationProperties, String securityRealmName) {
		String clientUsername = configurationProperties.getProperty(securityRealmName + ".clientUsername", null);
		String clientPassword = configurationProperties.getProperty(securityRealmName + ".clientPassword", null);
		if (clientUsername != null) {
			if (clientPassword != null) {
				try {
					PhotonicUser newUser = new PhotonicUser(clientUsername, clientPassword, null, null, new String[] {PhotonicUser.FULL_RIGHTS}, false);
					UserService.INSTANCE.createNewUser(newUser);
					removeProperties(securityRealmName + ".clientUsername", securityRealmName + ".clientPassword");
				} catch (UserManagementException e) {
					logger.error("Couldn't migrate user", e);
				}
			} else {
				removeProperties(securityRealmName + ".clientUsername");
			}
		} else if (clientPassword != null) {
			removeProperties(securityRealmName + ".clientPassword");
		}
	}
	
	private HostProperties() {
		String printDirString = null;
		String uploadDirString = null;
		
		if (!PROFILES_DIR.exists() && !PROFILES_DIR.mkdirs()) {
			logger.info("Couldn't make profiles directory. No write access or disk full?" );
			throw new IllegalArgumentException("Couldn't make profiles directory. No write access or disk full?");
		}
		
		if (!MACHINE_DIR.exists() && !MACHINE_DIR.mkdirs()) {
			logger.info("Couldn't make machine directory. No write access or disk full?" );
			throw new IllegalArgumentException("Couldn't make machine directory. No write access or disk full?");
		}
		
		Properties configurationProperties = getMergedProperties();
		printDirString = configurationProperties.getProperty("printdir");
		uploadDirString = configurationProperties.getProperty("uploaddir");
		
		fakeSerial = new Boolean(configurationProperties.getProperty("fakeserial", "false"));
		visibleCards = Arrays.asList(configurationProperties.getProperty("visibleCards", "printers,printJobs,printables,users,settings").split(","));
		skinsStringJson = configurationProperties.getProperty("skins", "[{\"name\":\"Main skin\", \"welcomeFiles\":[\"index.htm\"], \"resourceBase\": \"resourcesnew\", \"active\": true}]");
		printerDriversStringJson = configurationProperties.getProperty("printerDrivers", "[{\"driverClassName\":\"org.area515.resinprinter.gcode.eGENERICGCodeControl\",\"driverName\":\"eGENERIC\", \"prettyName\":\"Generic GCode\"}]");
		
		//This loads features
		for (Entry<Object, Object> currentProperty : configurationProperties.entrySet()) {
			String currentPropertyString = currentProperty.getKey() + "";
			if (currentPropertyString.startsWith("feature.")) {
				currentPropertyString = currentPropertyString.replace("feature.", "");
				if ("true".equalsIgnoreCase(currentProperty.getValue() + "")) {
					try {
						featureClasses.put((Class<Feature>)Class.forName(currentPropertyString), configurationProperties.getProperty("featureSettings." + currentPropertyString));
					} catch (NoClassDefFoundError | UnsatisfiedLinkError | ClassNotFoundException e) {
						logger.error("Failed to load Feature:" + currentPropertyString, e);
					}
				}
			}
		}			
		
		//This loads notifiers
		for (Entry<Object, Object> currentProperty : configurationProperties.entrySet()) {
			String currentPropertyString = currentProperty.getKey() + "";
			if (currentPropertyString.startsWith("notify.")) {
				currentPropertyString = currentPropertyString.replace("notify.", "");
				if ("true".equalsIgnoreCase(currentProperty.getValue() + "")) {
					try {
						notificationClasses.add((Class<Notifier>)Class.forName(currentPropertyString));
					} catch (NoClassDefFoundError | UnsatisfiedLinkError | ClassNotFoundException e) {
						logger.error("Failed to load Notifier:" + currentPropertyString, e);
					}
				}
			}
		}
		
		//This loads print file processors
		for (Entry<Object, Object> currentProperty : configurationProperties.entrySet()) {
			String currentPropertyString = currentProperty.getKey() + "";
			if (currentPropertyString.startsWith("printFileProcessor.")) {
				currentPropertyString = currentPropertyString.replace("printFileProcessor.", "");
				if ("true".equalsIgnoreCase(currentProperty.getValue() + "")) {
					try {
						PrintFileProcessor processor = ((Class<PrintFileProcessor>)Class.forName(currentPropertyString)).newInstance();
						printFileProcessors.add(processor);
					} catch (NoClassDefFoundError | UnsatisfiedLinkError | ClassNotFoundException | InstantiationException | IllegalAccessException e) {
						logger.error("Failed to load PrintFileProcessor:" + currentPropertyString, e);
					}
				}
			}
		}
		
		//This loads displayDevices
		for (Entry<Object, Object> currentProperty : configurationProperties.entrySet()) {
			String currentPropertyString = currentProperty.getKey() + "";
			if (currentPropertyString.startsWith("displayDevice.")) {
				currentPropertyString = currentPropertyString.replace("displayDevice.", "");
				if ("true".equalsIgnoreCase(currentProperty.getValue() + "")) {
					try {
						GraphicsOutputInterface device = ((Class<GraphicsOutputInterface>)Class.forName(currentPropertyString)).newInstance();
						displayDevices.add(device);
					} catch (NoClassDefFoundError | UnsatisfiedLinkError | ClassNotFoundException | InstantiationException | IllegalAccessException e) {
						logger.error("Failed to load DisplayDevice:" + currentPropertyString, e);
					}
				}
			}
		}
		
		String serialCommString = null;
		try {
			serialCommString = configurationProperties.getProperty("SerialCommunicationsImplementation", JSSCCommPort.class.getName());
			serialPortClass = (Class<SerialCommunicationsPort>)Class.forName(serialCommString);
		} catch (ClassNotFoundException e) {
			logger.error("Failed to load SerialCommunicationsImplementation:{}", serialCommString);
		}
		
		String networkManagerName = null;
		try {
			networkManagerName = configurationProperties.getProperty("NetworkManagerImplementation", LinuxNetworkManager.class.getName());
			networkManagerClass = (Class<NetworkManager>)Class.forName(networkManagerName);
		} catch (ClassNotFoundException e) {
			logger.error("Failed to load NetworkManagerImplementation:{}", networkManagerName);
		}

		String userManagementFeature = null;
		try {
			userManagementFeature = configurationProperties.getProperty("UserManagementFeatureImplementation", KeystoreLoginService.class.getName());
			Class<Feature> userManagementFeatureClass = (Class<Feature>)Class.forName(userManagementFeature);
			featureClasses.put(userManagementFeatureClass, configurationProperties.getProperty("featureSettings." + userManagementFeatureClass.getName()));
		} catch (ClassNotFoundException e) {
			logger.error("Failed to load UserManagementFeatureImplementation:{}", userManagementFeature);
		}
		
		//Here are all of the server configuration settings
		String sslKeystoreFilename = configurationProperties.getProperty("keystoreFilename");
		if (sslKeystoreFilename != null) {
			sslKeystoreFile = new File(sslKeystoreFilename);
		}
		String userKeystoreFilename = configurationProperties.getProperty("userKeystoreFilename");
		if (userKeystoreFilename != null) {
			userKeystoreFile = new File(userKeystoreFilename);
		}
		useSSL = new Boolean(configurationProperties.getProperty("useSSL", "false"));
		printerHostPort = new Integer(configurationProperties.getProperty("printerHostPort", useSSL?"443":"9091"));
		if (System.getProperty("overrideHostPort") != null) {
			printerHostPort = Integer.parseInt(System.getProperty("overrideHostPort"));
		}
		useAuthentication = new Boolean(configurationProperties.getProperty("useAuthentication", "false"));
		externallyAccessableName = configurationProperties.getProperty("externallyAccessableName");
		sslKeypairPassword = configurationProperties.getProperty("keypairPassword");
		sslKeystorePassword = configurationProperties.getProperty("keystorePassword");
		userKeystorePassword = configurationProperties.getProperty("userKeystorePassword");
		securityRealmName = configurationProperties.getProperty("securityRealmName", "SecurityRealm");
		
		forwardHeader = configurationProperties.getProperty("forwardHeader", null);
		removeJobOnCompletion = new Boolean(configurationProperties.getProperty("removeJobOnCompletion", "true"));
		forceCalibrationOnFirstUse = new Boolean(configurationProperties.getProperty("forceCalibrationOnFirstUse", "false"));
		limitLiveStreamToOneCPU = new Boolean(configurationProperties.getProperty("limitLiveStreamToOneCPU", "false"));
		scriptEngineLanguage = configurationProperties.getProperty("scriptEngineLanguage", "js");
		printerProfileRepo = configurationProperties.getProperty("printerProfileRepo", "WesGilster/Creation-Workshop-Host");
		
		streamingCommand = getJSonStringArray(configurationProperties, "streamingCommand");
		imagingCommand = getJSonStringArray(configurationProperties, "imagingCommand");
		hexCodeBasedProjectorsJson = configurationProperties.getProperty("hexCodeBasedProjectors");
		dumpStackTraceCommand = getJSonStringArray(configurationProperties, "dumpStackTraceCommand");
		rebootCommand = getJSonStringArray(configurationProperties, "rebootCommand");
		
		if (printDirString == null) {
			printDir = new File(System.getProperty("java.io.tmpdir"), "printdir");
		} else {
			printDir = new File(printDirString);
		}
		
		if (uploadDirString == null) {
			uploadDir = new File(System.getProperty("user.home"), "uploaddir");
		} else {
			uploadDir = new File(uploadDirString);
		}
		
		File versionFile = new File("build.number");
		if (versionFile.exists()) {
			Properties newProperties = new Properties();
			try {
				newProperties.load(new FileInputStream(versionFile));
				//versionNumber = Integer.valueOf((String)newProperties.get("build.number")) - 1;
				releaseTagName = (String)newProperties.get("repo.version");
			} catch (IOException e) {
				logger.error("Version file is missing:{}", versionFile);
			}
		}
		
		if(!printDir.exists()) {
			try {
				FileUtils.forceMkdir(printDir);
			} catch (IOException e) {
				throw new IllegalArgumentException("Couldn't create print directory", e);
			}
		}
		
		if(!uploadDir.exists()) {
			try {
				FileUtils.forceMkdir(uploadDir);
			} catch (IOException e) {
				throw new IllegalArgumentException("Couldn't create upload directory", e);
			}
		}
		
		this.sharedScriptEngine = this.buildScriptEngine();
	}

	public Skin getFirstActiveSkin() {
		for (Skin skin : getSkins()) {
			if (skin.isActive()) {
				return skin;
			}
		}
		
		return null;
	}
	
	private Properties getClasspathProperties() {
		InputStream stream = HostProperties.class.getClassLoader().getResourceAsStream(CONFIG_PROPERTIES);
		if (stream == null) {
			throw new IllegalArgumentException("Server couldn't find your " + CONFIG_PROPERTIES + " file.");
		}
		
		Properties configurationProperties = new Properties();
		try {
			configurationProperties.load(stream);
			return configurationProperties;
		} catch (IOException e) {
			throw new IllegalArgumentException("Server couldn't find your " + CONFIG_PROPERTIES + " file.", e);
		} finally {
			IOUtils.closeQuietly(stream);
		}
	}
	
	private String[] getJSonStringArray(Properties configurationProperties, String property) {
		String json = configurationProperties.getProperty(property);
		if (json == null)
			return null;
		
		ObjectMapper mapper = new ObjectMapper(new JsonFactory());
		try {
			return mapper.readValue(json, new TypeReference<String[]>(){});
		} catch (IOException e) {
			throw new IllegalArgumentException("Property:" + property + " didn't parse correctly.", e);
		}
	}
	
	public void exportDiagnostic(ZipOutputStream zipOutputStream) throws IOException {
		String MASK = "Masked by CWH";

		Properties properties = getClasspathProperties();
		properties.put("CWH3DPrinterRealm.clientPassword", MASK);
		properties.put("keypairPassword", MASK);
		properties.put("keystorePassword", MASK);
		properties.put("password", MASK);
		ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
		properties.store(byteStream, "Stored on " + new Date());
		IOUtilities.zipStream(CONFIG_PROPERTIES, new ByteArrayInputStream(byteStream.toByteArray()), zipOutputStream);
		
		properties = loadOverriddenConfigurationProperties();
		properties.put("CWH3DPrinterRealm.clientPassword", MASK);
		properties.put("keypairPassword", MASK);
		properties.put("keystorePassword", MASK);
		properties.put("password", MASK);
		byteStream = new ByteArrayOutputStream();
		properties.store(byteStream, "Stored on " + new Date());
		IOUtilities.zipStream(PRINTER_CONFIG_PROPERTIES, new ByteArrayInputStream(byteStream.toByteArray()), zipOutputStream);
		
		ObjectMapper mapper = new ObjectMapper(new JsonFactory());
		IOUtilities.zipStream("currentPrinterConfigurations.json", new ByteArrayInputStream(mapper.writeValueAsBytes(getPrinterConfigurations())), zipOutputStream);
		
		IOUtilities.zipFile(new File("build.number"), zipOutputStream);
		
		dumpFiles(PRINTER_EXTENSION, PRINTER_DIR, zipOutputStream);
		dumpFiles(PROFILES_EXTENSION, PROFILES_DIR, zipOutputStream);
		dumpFiles(MACHINE_EXTENSION, MACHINE_DIR, zipOutputStream);
		dumpFiles(CUSTOMIZER_EXTENSION, CUSTOMIZER_DIR, zipOutputStream);
	}

	private void dumpFiles(final String extension, File directory, ZipOutputStream zipOutputStream) {
		File fileList[] = directory.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				if (name.endsWith(extension)) {
					return true;
				}
				
				return false;
			}
		});
		for (File currentFile : fileList) {
			IOUtilities.zipFile(currentFile, zipOutputStream);
		}
	}
	
	public ScriptEngine buildScriptEngine() {
		return new ScriptEngineManager().getEngineByExtension(scriptEngineLanguage);
	}
	
	public String getSecurityRealmName() {
		return securityRealmName;
	}

	public int getVersionNumber() {
		if (releaseTagName == null) {
			return 0;
		}
		
		String release = releaseTagName.replaceAll("[^\\d]", "");
		if (release.length() == 0) {
			return 0;
		}
		
		return Integer.valueOf(release);
	}

	public String getReleaseTagName() {
		return releaseTagName;
	}

	public String getPrinterProfileRepo() {
		return printerProfileRepo;
	}

	public int getPrinterHostPort() {
		return printerHostPort;
	}
	
	public File getUploadDir(){
		return uploadDir;
	}
	
	public File getUpgradeDir(){
		return new File("");
	}
	
	public File getWorkingDir(){
		return printDir;
	}
	
	public boolean getFakeSerial(){
		return fakeSerial;
	}
	
	public String getSSLKeypairPassword() {
		return sslKeypairPassword;
	}

	public String getSSLKeystorePassword() {
		return sslKeystorePassword;
	}

	public File getSSLKeystoreFile() {
		return sslKeystoreFile;
	}

	public String getUserKeystorePassword() {
		return userKeystorePassword;
	}

	public File getUserKeystoreFile() {
		return userKeystoreFile;
	}

	public Class<SerialCommunicationsPort> getSerialCommunicationsClass() {
		return serialPortClass;
	}
	
	public Class<NetworkManager> getNetworkManagerClass() {
		return networkManagerClass;
	}
	
	public Map<Class<Feature>, String> getFeatures() {
		return featureClasses;
	}
	
	public List<Class<Notifier>> getNotifiers() {
		return notificationClasses;
	}
	
	public List<PrintFileProcessor<?,?>> getPrintFileProcessors() {
		return printFileProcessors;
	}
	
	public List<GraphicsOutputInterface> getDisplayDevices() {
		return displayDevices;
	}	
	
	public boolean isUseSSL() {
		return useSSL;
	}
	
	public boolean isUseAuthentication() {
		return useAuthentication;
	}

	public String getForwardHeader() {
		return forwardHeader;
	}

	public String getExternallyAccessableName() {
		return externallyAccessableName;
	}

	public String[] getDumpStackTraceCommand() {
		return dumpStackTraceCommand;
	}

	public String[] getRebootCommand() {
		return rebootCommand;
	}
	
	public String[] getStreamingCommand() {
		return streamingCommand;
	}
	
	public String[] getImagingCommand() {
		return imagingCommand;
	}
	
	public boolean getLimitLiveStreamToOneCPU() {
		return limitLiveStreamToOneCPU;
	}

	public List<String> getVisibleCards() {
		return visibleCards;
	}
	
	public boolean isRemoveJobOnCompletion() {
		return removeJobOnCompletion;
	}
	
	public boolean isHostReady() {
		return hostReady.getCount() == 0;
	}
	
	public boolean isForceCalibrationOnFirstUse() {
		return forceCalibrationOnFirstUse;
	}
	
	public boolean waitForReady(long timeout, TimeUnit unit) {
		try {
			return hostReady.await(timeout, unit);
		} catch (InterruptedException e) {
			logger.error("Interrupted while waiting for startup process to complete");
			return isHostReady();
		}
	}
	
	public HostInformation loadHostInformation() {
		Properties configurationProperties = getMergedProperties();
		HostInformation settings = new HostInformation(
				configurationProperties.getProperty("deviceName", "Photonic 3D Multiprint Host"),
				configurationProperties.getProperty("manufacturer", "Wes Gilster"));
		return settings;
	}
	
	public void saveHostInformation(HostInformation device) {
		Properties hostInfoformationProperties = new Properties();
		hostInfoformationProperties.setProperty("deviceName", device.getDeviceName());
		hostInfoformationProperties.setProperty("manufacturer", device.getManufacturer());
		
		saveOverriddenConfigurationProperties(hostInfoformationProperties);
		
		NotificationManager.hostSettingsChanged();
	}
	
	public CwhEmailSettings loadEmailSettings() {
		Properties properties = getMergedProperties();
		CwhEmailSettings settings = new CwhEmailSettings(
										properties.getProperty("smtpServer"),
										Integer.valueOf(properties.getProperty("smtpPort")),
										properties.getProperty("username"),
										properties.getProperty("password"),
										properties.getProperty("toEmailAddresses"),
										properties.getProperty("serviceEmailAddresses"),
										Boolean.valueOf(properties.getProperty("mail.smtp.starttls.enable")));
		return settings;
	}
	
	public void saveEmailSettings(CwhEmailSettings settings) {
		Properties emailProperties = new Properties();
		StringBuilder toEmails = new StringBuilder();
		List<String> emails = settings.getNotificationEmailAddresses();
		for (int t = 0; t < emails.size(); t++) {
			if (t > 0) {
				toEmails.append(",");
			}
			
			toEmails.append(emails.get(t));
		}
		emailProperties.setProperty("toEmailAddresses", toEmails.toString());
		emails = settings.getServiceEmailAddresses();
		toEmails.setLength(0);
		for (int t = 0; t < emails.size(); t++) {
			if (t > 0) {
				toEmails.append(",");
			}
			
			toEmails.append(emails.get(t));
		}
		emailProperties.setProperty("serviceEmailAddresses", toEmails.toString());
		emailProperties.setProperty("username", settings.getUserName());
		emailProperties.setProperty("password", settings.getPassword());
		emailProperties.setProperty("smtpServer", settings.getSmtpServer());
		emailProperties.setProperty("smtpPort", settings.getSmtpPort() + "");
		emailProperties.setProperty("mail.smtp.starttls.enable", settings.isUseTLS() + "");
		
		saveOverriddenConfigurationProperties(emailProperties);
		
		NotificationManager.hostSettingsChanged();
	}
	
	public List<ProjectorModel> getAutodetectProjectors() {
		ObjectMapper mapper = new ObjectMapper(new JsonFactory());
		List<ProjectorModel> projectors;
		try {
			projectors = mapper.readValue(hexCodeBasedProjectorsJson, new TypeReference<List<HexCodeBasedProjector>>(){});
			return projectors;
		} catch (IOException e) {
			logger.error("Problem loading hexcode projector json.", e);
			return new ArrayList<ProjectorModel>();
		}
	}
	
	public List<PrinterDriver> getPrinterDrivers() {
		ObjectMapper mapper = new ObjectMapper(new JsonFactory());
		try {
			List<PrinterDriver> skins = mapper.readValue(printerDriversStringJson, new TypeReference<List<PrinterDriver>>(){});
			return skins;
		} catch (IOException e) {
			logger.error("Problem loading skins json.", e);
			return new ArrayList<PrinterDriver>();
		}
	}

	public List<Skin> getSkins() {
		ObjectMapper mapper = new ObjectMapper(new JsonFactory());
		try {
			List<Skin> skins = mapper.readValue(skinsStringJson, new TypeReference<List<Skin>>(){});
			return skins;
		} catch (IOException e) {
			logger.error("Problem loading skins json.", e);
			return new ArrayList<Skin>();
		}
	}
	
	public void saveSkins(List<Skin> skins) {
		ObjectMapper mapper = new ObjectMapper(new JsonFactory());
		try {
			skinsStringJson = mapper.writeValueAsString(skins);
			saveProperty("skins", skinsStringJson);
		} catch (IOException e) {
			logger.error("Problem saving skins json.", e);
		}
	}
	
	public <T extends Named> List<T> getConfigurations(File searchDirectory, String extension, Class<T> clazz) {
		List<T> configurations = new ArrayList<T>();
		
		File files[] = searchDirectory.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				if (name.endsWith(extension)) {
					return true;
				}
				
				return false;
			}
		});
		
		for (File currentFile : files) {
			JAXBContext jaxbContext;
			try {
				jaxbContext = JAXBContext.newInstance(clazz);
				Unmarshaller jaxbUnMarshaller = jaxbContext.createUnmarshaller();
				
				T machine = (T)jaxbUnMarshaller.unmarshal(currentFile);
				machine.setName(currentFile.getName().substring(0, currentFile.getName().length() - extension.length()));
				configurations.add(machine);
				logger.info("Created {} for:{}", extension, currentFile);
			} catch (JAXBException e) {
				logger.error("Problem marshalling " + extension + " configuration from:" + currentFile, e);
			}
		}
		
		return configurations;
	}
	
	public Customizer loadCustomizer(String customizerName) throws InappropriateDeviceException {
		if (!CUSTOMIZER_DIR.exists() && !CUSTOMIZER_DIR.mkdirs()) {
			throw new InappropriateDeviceException("Couldn't create customizer directory:" + CUSTOMIZER_DIR);
		}

		File currentFile = new File(CUSTOMIZER_DIR, customizerName + CUSTOMIZER_EXTENSION);
		if (!currentFile.exists()) {
			return null;
		}
		
		JAXBContext jaxbContext;
		try {
			jaxbContext = JAXBContext.newInstance(Customizer.class);
			Unmarshaller jaxbUnMarshaller = jaxbContext.createUnmarshaller();
			
			Customizer customizer = (Customizer)jaxbUnMarshaller.unmarshal(currentFile);
			
			logger.info("Loaded customizer for:{}", customizer);
			return customizer;
		} catch (JAXBException e) {
			throw new InappropriateDeviceException("Problem marshalling customizer from:" + currentFile, e);
		}
	}
	
	public Customizer saveCustomizer(Customizer customizer) throws InappropriateDeviceException {
		if (!CUSTOMIZER_DIR.exists() && !CUSTOMIZER_DIR.mkdirs()) {
			throw new InappropriateDeviceException("Couldn't create customizer directory:" + CUSTOMIZER_DIR);
		}

		File currentFile = new File(CUSTOMIZER_DIR, customizer.getName() + CUSTOMIZER_EXTENSION);
		JAXBContext jaxbContext;
		try {
			jaxbContext = JAXBContext.newInstance(Customizer.class);
			Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
			jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
			jaxbMarshaller.marshal(customizer, currentFile);
			logger.info("Saved customizer for:{}", customizer);
			NotificationManager.hostSettingsChanged();
			return customizer;
		} catch (JAXBException e) {
			throw new InappropriateDeviceException("Problem marshalling customizer from:" + currentFile, e);
		}
	}
	
	public List<PrinterConfiguration> getPrinterConfigurations() {
		if (configurations != null) {
			return new ArrayList<PrinterConfiguration>(configurations.values());
		}
		
		configurations = new ConcurrentHashMap<String, PrinterConfiguration>();

		if (!PRINTER_DIR.exists()) {
			if (!PRINTER_DIR.mkdirs()) {
				throw new IllegalArgumentException("Couldn't create printer directory:" + PRINTER_DIR);
			}
			
			//PrinterService.INSTANCE.createPrinter("Autodetected Printer", DisplayManager.LAST_AVAILABLE_DISPLAY, SerialManager.FIRST_AVAILABLE_PORT);
		}
		
		File printerFiles[] = PRINTER_DIR.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				if (name.endsWith(PRINTER_EXTENSION)) {
					return true;
				}
				
				return false;
			}
		});
		
		for (File currentFile : printerFiles) {
			JAXBContext jaxbContext;
			try {
				jaxbContext = JAXBContext.newInstance(PrinterConfiguration.class, MachineConfig.class, SlicingProfile.class);
				Unmarshaller jaxbUnMarshaller = jaxbContext.createUnmarshaller();
				
				PrinterConfiguration configuration = (PrinterConfiguration)jaxbUnMarshaller.unmarshal(currentFile);
				configuration.setName(currentFile.getName().replace(PRINTER_EXTENSION, ""));
	
				File machineFile = new File(MACHINE_DIR, configuration.getMachineConfigName() + MACHINE_EXTENSION);
				MachineConfig machineConfig = (MachineConfig)jaxbUnMarshaller.unmarshal(machineFile);
				machineConfig.setName(machineFile.getName().substring(0, machineFile.getName().length() - MACHINE_EXTENSION.length()));
				configuration.setMachineConfig(machineConfig);
				
				File profileFile = new File(PROFILES_DIR, configuration.getSlicingProfileName() + PROFILES_EXTENSION);
				SlicingProfile profile = (SlicingProfile)jaxbUnMarshaller.unmarshal(profileFile);
				profile.setName(profileFile.getName().substring(0, profileFile.getName().length() - PROFILES_EXTENSION.length()));
				configuration.setSlicingProfile(profile);
				
				//We do not want to start the printer here
				configurations.put(configuration.getName(), configuration);
				
				logger.info("Loaded printer configuration for:{}", configuration);
			} catch (JAXBException e) {
				logger.error("Problem marshalling printer configurations from:" + currentFile, e);
			}
		}
		
		return new ArrayList<PrinterConfiguration>(configurations.values());
	}	
	
	public PrinterConfiguration getPrinterConfiguration(String name) {
		getPrinterConfigurations();
		
		return configurations.get(name);
	}
	
	public static <T> T deepCopyJAXB(T object, Class<T> clazz) throws JAXBException {
	    JAXBContext jaxbContext = JAXBContext.newInstance(clazz);
	    JAXBElement<T> contentObject = new JAXBElement<T>(new QName(clazz.getSimpleName()), clazz, object);
	    JAXBSource source = new JAXBSource(jaxbContext, contentObject);
	    return jaxbContext.createUnmarshaller().unmarshal(source, clazz).getValue();
	}
	
	private void saveConfigurations(PrinterConfiguration focusedSave) {
		for (PrinterConfiguration currentConfiguration : configurations.values()) {
			JAXBContext jaxbContext;
			try {
				jaxbContext = JAXBContext.newInstance(PrinterConfiguration.class);
				Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
				jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

				MachineConfig machineConfig = currentConfiguration.getMachineConfig();
				if (focusedSave != null && currentConfiguration.getMachineConfigName().equals(focusedSave.getMachineConfigName())) {
					machineConfig = deepCopyJAXB(focusedSave.getMachineConfig(), MachineConfig.class);
					machineConfig.setName(currentConfiguration.getMachineConfigName());
					currentConfiguration.setMachineConfig(machineConfig);
				}
				
				File machineFile = new File(MACHINE_DIR, currentConfiguration.getMachineConfigName() + MACHINE_EXTENSION);
				jaxbMarshaller.marshal(machineConfig, machineFile);

				SlicingProfile slicingProfile = currentConfiguration.getSlicingProfile();
				if (focusedSave != null && currentConfiguration.getSlicingProfileName().equals(focusedSave.getSlicingProfileName())) {
					slicingProfile = deepCopyJAXB(focusedSave.getSlicingProfile(), SlicingProfile.class);
					slicingProfile.setName(focusedSave.getSlicingProfileName());
					currentConfiguration.setSlicingProfile(slicingProfile);
				}

				File slicingFile = new File(PROFILES_DIR, currentConfiguration.getSlicingProfileName() + PROFILES_EXTENSION);
				jaxbMarshaller.marshal(slicingProfile, slicingFile);

				File printerFile = new File(PRINTER_DIR, currentConfiguration.getName() + PRINTER_EXTENSION);
				jaxbMarshaller.marshal(new PrinterConfiguration(
						currentConfiguration.getMachineConfigName(), 
						currentConfiguration.getSlicingProfileName(), 
						currentConfiguration.isAutoStart()), printerFile);
			} catch (JAXBException e) {
				logger.error("Problem saving printer configuration for:" + currentConfiguration, e);
			}
		}
		
		NotificationManager.hostSettingsChanged();
	}
	
	private Properties loadOverriddenConfigurationProperties() {
		Properties overridenConfigurationProperties = new Properties();
		File configPropertiesInPrintersDirectory = new File(PRINTER_DIR, CONFIG_PROPERTIES);
		if (configPropertiesInPrintersDirectory.exists()) {
			FileInputStream stream = null;
			try {
				stream = new FileInputStream(configPropertiesInPrintersDirectory);
				overridenConfigurationProperties.load(stream);
			} catch (IOException e) {
				logger.error("Problem loading configuration properties from:" + configPropertiesInPrintersDirectory, e);
			} finally {
				IOUtils.closeQuietly(stream);
			}
		} else {
			saveOverriddenConfigurationProperties(overridenConfigurationProperties);
		}
		
		return overridenConfigurationProperties;
	}
	
	private void saveOverriddenConfigurationProperties(Properties overridenConfigurationProperties) {
		File configPropertiesInPrintersDirectory = new File(PRINTER_DIR, CONFIG_PROPERTIES);
		PRINTER_DIR.mkdirs();
		FileOutputStream stream = null;
		Properties currentProperties = null;
		try {
			//Do this check, otherwise we'll end up calling loadOverriddenConfigurationProperties/saveOverriddenConfigurationProperties recursively forever.
			if (configPropertiesInPrintersDirectory.exists()) {
				currentProperties = loadOverriddenConfigurationProperties();
			} else {
				currentProperties = new Properties();
			}
			currentProperties.putAll(overridenConfigurationProperties);
			stream = new FileOutputStream(configPropertiesInPrintersDirectory);
			currentProperties.store(stream, "File created by CWH");
		} catch (IOException e) {
			logger.error("Problem saving configuration properties from:" + configPropertiesInPrintersDirectory, e);
			throw new IllegalArgumentException("Couldn't create:" + configPropertiesInPrintersDirectory, e);
		} finally {
			IOUtils.closeQuietly(stream);
		}
	}
	
	private void overwriteOverriddenConfigurationProperties(Properties overridenConfigurationProperties) {
		File configPropertiesInPrintersDirectory = new File(PRINTER_DIR, CONFIG_PROPERTIES);
		PRINTER_DIR.mkdirs();
		FileOutputStream stream = null;
		try {
			stream = new FileOutputStream(configPropertiesInPrintersDirectory);
			overridenConfigurationProperties.store(stream, "File created by CWH");
		} catch (IOException e) {
			logger.error("Problem saving configuration properties from:" + configPropertiesInPrintersDirectory, e);
			throw new IllegalArgumentException("Couldn't create:" + configPropertiesInPrintersDirectory, e);
		} finally {
			IOUtils.closeQuietly(stream);
		}
	}
	
	public void hostStartupComplete() {
		Properties oneTimeInstall = new Properties();
		oneTimeInstall.setProperty("performedOneTimeInstall", "true");
		saveOverriddenConfigurationProperties(oneTimeInstall);
		
		while (hostReady.getCount() > 0) {
			hostReady.countDown();
		}
		
		NotificationManager.hostSettingsChanged();
	}

	public void saveProperty(String name, String value) {
		Properties properties = loadOverriddenConfigurationProperties();
		properties.setProperty(name, value);
		
		overwriteOverriddenConfigurationProperties(properties);
		NotificationManager.hostSettingsChanged();
	}
	
	public String loadProperty(String name) {
		Properties properties = loadOverriddenConfigurationProperties();
		String value = properties.getProperty(name);
		return value;
	}
	
	public void removeProperties(String... propertiesToRemove) {
		Properties properties = loadOverriddenConfigurationProperties();
		for (String property : propertiesToRemove) {
			properties.remove(property);
		}
		
		overwriteOverriddenConfigurationProperties(properties);
		NotificationManager.hostSettingsChanged();
	}
	
	//TODO: Implement versioning so that we don't have any lost update issues.
	public void addOrUpdatePrinterConfiguration(PrinterConfiguration configuration) throws AlreadyAssignedException {
		getPrinterConfigurations();

		configurations.put(configuration.getName(), configuration);
		saveConfigurations(configuration);
	}

	public void removePrinterConfiguration(PrinterConfiguration configuration) throws InappropriateDeviceException {
		getPrinterConfigurations();

		File machine = new File(PRINTER_DIR, configuration.getName() + PRINTER_EXTENSION);
		if (!machine.exists()) {
			throw new InappropriateDeviceException("Printer configuration doesn't exist for:" + configuration.getName());
		}
		
		if (!machine.isFile()) {
			throw new InappropriateDeviceException("Printer configuration wasn't a file:" + configuration.getName());
		}
		
		if (!machine.delete()) {
			throw new InappropriateDeviceException("Couldn't delete configuration for:" + configuration.getName());
		}
		
		configurations.remove(configuration.getName());
	}
}