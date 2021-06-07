package org.area515.resinprinter.discover;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FilenameFilter;
import java.net.URI;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.area515.resinprinter.client.Main;
import org.area515.resinprinter.plugin.Feature;
import org.area515.resinprinter.server.HostInformation;
import org.area515.resinprinter.server.HostProperties;
import org.fourthline.cling.UpnpServiceImpl;
import org.fourthline.cling.binding.annotations.AnnotationLocalServiceBinder;
import org.fourthline.cling.controlpoint.ControlPoint;
import org.fourthline.cling.model.DefaultServiceManager;
import org.fourthline.cling.model.message.header.STAllHeader;
import org.fourthline.cling.model.meta.DeviceDetails;
import org.fourthline.cling.model.meta.DeviceIdentity;
import org.fourthline.cling.model.meta.Icon;
import org.fourthline.cling.model.meta.LocalDevice;
import org.fourthline.cling.model.meta.LocalService;
import org.fourthline.cling.model.meta.ManufacturerDetails;
import org.fourthline.cling.model.meta.ModelDetails;
import org.fourthline.cling.model.resource.Resource;
import org.fourthline.cling.model.types.DLNADoc;
import org.fourthline.cling.model.types.DeviceType;
import org.fourthline.cling.model.types.UDADeviceType;
import org.fourthline.cling.model.types.UDN;
import org.fourthline.cling.support.connectionmanager.AbstractPeeringConnectionManagerService;

public class UPNPAdvertiser implements Feature {
	private static final Logger logger = LogManager.getLogger();
	public static UPNPSetup INSTANCE;
	
	private UpnpServiceImpl upnpService;
	
	public static class UPNPSetup {
		private String deviceName = null;
		private String manufacturer = null;
		private String deviceReleaseString = HostProperties.Instance().getReleaseTagName();
		private int deviceVersion = HostProperties.Instance().getVersionNumber();
		private String deviceType = Main.PRINTER_TYPE;
		private int upnpStreamPort = 5001;
		
		public UPNPSetup(HostInformation info) {
			this.deviceName = info.getDeviceName();
			this.manufacturer = info.getManufacturer();
		}
		
		public String getDeviceName() {
			return deviceName;
		}
		public String getManufacturer() {
			return manufacturer;
		}
	}
	
	public UPNPSetup getSetup() {
		if (INSTANCE == null) {
			INSTANCE = new UPNPSetup(HostProperties.Instance().loadHostInformation());
		}
		
		return INSTANCE;
	}
	
	@Override
	public void start(URI webPresentationURI, String settings) {
		try {
			UDN udn = UDN.uniqueSystemIdentifier(getSetup().deviceName + getSetup().deviceReleaseString + getSetup().manufacturer);
			DeviceType type = new UDADeviceType(getSetup().deviceType, getSetup().deviceVersion);
	
			DeviceDetails details = new DeviceDetails(getSetup().deviceName,
					new ManufacturerDetails(getSetup().manufacturer), 
						new ModelDetails(
								getSetup().deviceName, 
								getSetup().deviceName, 
								"v" + getSetup().deviceReleaseString), 
							webPresentationURI, 
					new DLNADoc[] {
							new DLNADoc("DMS", DLNADoc.Version.V1_5),
							new DLNADoc("M-DMS", DLNADoc.Version.V1_5) },
					null);
			
			List<Icon> icons = new ArrayList<Icon>();
			File iconFiles[] = new File(HostProperties.Instance().getFirstActiveSkin().getResourceBase(), "favicon").listFiles(new FilenameFilter() {
				@Override
				public boolean accept(File dir, String name) {
					name = name.toLowerCase();
					if (!name.endsWith(".jpg") && !name.endsWith(".gif") && !name.endsWith(".png")) {
						return false;
					}
					
					return name.contains("48") || name.contains("120");
				}
			});
			
			if (iconFiles != null) {
				for (File currentFile : iconFiles) {
					String mimeType = Files.probeContentType(currentFile.toPath());
					BufferedImage image = ImageIO.read(currentFile);
					icons.add(new Icon(mimeType, image.getWidth(), image.getHeight(), image.getColorModel().getPixelSize(), new URI(webPresentationURI.toString() + "/favicon/" + currentFile.getName())));
				}
			}
			
			LocalService<PrinterDirectoryService> contentManagerService = new AnnotationLocalServiceBinder().read(PrinterDirectoryService.class);
			PrinterHostSettingsServiceManager manager = new PrinterHostSettingsServiceManager<PrinterDirectoryService>(getSetup(), webPresentationURI, contentManagerService, PrinterDirectoryService.class);
			contentManagerService.setManager(manager);//new DefaultServiceManager<PrinterDirectoryService>(contentManagerService, PrinterDirectoryService.class));
			
			LocalService<AbstractPeeringConnectionManagerService> connectionManagerService = new AnnotationLocalServiceBinder().read(AbstractPeeringConnectionManagerService.class);
			connectionManagerService.setManager(new DefaultServiceManager<AbstractPeeringConnectionManagerService>(connectionManagerService, AbstractPeeringConnectionManagerService.class));
				
				PhotonicDevice printerServer = new PhotonicDevice(
					new DeviceIdentity(udn), 
					type, 
					details, 
					icons.toArray(new Icon[icons.size()]),
					new LocalService[]{contentManagerService, connectionManagerService});
			
			PhotonicUpnpServiceConfiguration serviceConfiguration = new PhotonicUpnpServiceConfiguration(getSetup().upnpStreamPort);
			upnpService = new UpnpServiceImpl(serviceConfiguration);
			if (!upnpService.getRouter().isEnabled()) {
				throw new IllegalArgumentException("It doesn't seem as though a network is available to publish this server, or an advertiser has already been started on this address.");
			}
			
			// Broadcast a search message for all devices
			ControlPoint controlPoint = upnpService.getControlPoint();
			controlPoint.search(new STAllHeader());
			
			// Add the bound local device to the registry
			upnpService.getRegistry().addDevice(printerServer);
			
			//NetworkAddress hooked = upnpService.getRouter().getActiveStreamServers(upnpService.getRouter().getNetworkAddressFactory().getBindAddresses()[0]).get(0);
			
			//By this point the externallyAccessableIP will be setup with the local IP if it started off null
			for (Resource device : upnpService.getRegistry().getResources()) {
				if (device.getModel() instanceof LocalDevice) {
					logger.debug("===========");
					logger.debug("{} exposed on: {}", device.getModel(), device.getPathQuery());
					logger.info("Relative UPNP root descriptor: {}", device.getPathQuery());
					logger.debug("===========");
				} else {
					logger.debug("{} exposed on: {}", device.getModel(), device.getPathQuery());
				}
			}
		} catch (Exception e) {
			logger.error("Couldn't advertise URI:" + webPresentationURI, e);
			throw new RuntimeException("Couldn't advertise URI:" + webPresentationURI);
		}
	}

	@Override
	public void stop() {
		upnpService.shutdown();
	}
}
