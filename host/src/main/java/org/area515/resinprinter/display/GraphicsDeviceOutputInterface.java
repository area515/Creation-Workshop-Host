package org.area515.resinprinter.display;

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GraphicsDevice;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;

import javax.swing.JFrame;

import org.area515.resinprinter.printer.PrinterConfiguration;

public class GraphicsDeviceOutputInterface implements GraphicsOutputInterface {
	private String displayName;
	private GraphicsDevice device;
	
	public GraphicsDeviceOutputInterface(String displayName, GraphicsDevice device) {
		this.displayName = displayName;
		this.device = device;
	}
	
	@Override
	public String getIDstring() {
		return displayName;
	}

	public String toString() {
		return displayName;
	}
	
	public GraphicsDevice getGraphicsDevice() {
		return device;
	}
	
	@Override
	public Rectangle getBoundary() {
		return getGraphicsDevice().getDefaultConfiguration().getBounds();
	}

	@Override
	public boolean isDisplayBusy() {
		return false;
	}

	@Override
	public void resetSliceCount() {
		throw new IllegalStateException("You should never call resetSliceCount from this class");
	}

	@Override
	public void dispose() {
		throw new IllegalStateException("You should never call dispose from this class");
	}

	@Override
	public void showBlankImage() {
		throw new IllegalStateException("You should never call showBlankImage from this class");
	}

	@Override
	public void showCalibrationImage(int xPixels, int yPixels) {
		throw new IllegalStateException("You should never call showCalibrationImage from this class");
	}

	@Override
	public void showGridImage(int pixels) {
		throw new IllegalStateException("You should never call showGridImage from this class");
	}

	@Override
	public void showImage(BufferedImage image, boolean performFullUpdate) {
		throw new IllegalStateException("You should never call showImage from this class");
	}

	@Override
	public String buildIDString() {
		return displayName;
	}

	@Override
	public GraphicsOutputInterface initializeDisplay(String displayId, PrinterConfiguration configuration) {
		GraphicsDevice device;
		try {
			device = ((GraphicsDeviceOutputInterface)DisplayManager.Instance().getDisplayDevice(displayId)).device;
		} catch (InappropriateDeviceException e) {
			throw new IllegalArgumentException("Couldn't find displayId:" + displayId, e);
		}
		PrinterDisplayFrame refreshFrame = new PrinterDisplayFrame(device);
		refreshFrame.setAlwaysOnTop(true);
		refreshFrame.setUndecorated(true);
		refreshFrame.setExtendedState(JFrame.MAXIMIZED_BOTH);
		Dimension dim = device.getDefaultConfiguration().getBounds().getSize();
		refreshFrame.setMinimumSize(dim);
		refreshFrame.setSize(dim);
		refreshFrame.setVisible(true);
		FullScreenMode fullScreenMode = configuration.getMachineConfig().getMonitorDriverConfig().getFullScreenMode();
		if (fullScreenMode == FullScreenMode.AlwaysUseFullScreen || 
			(fullScreenMode == FullScreenMode.UseFullScreenWhenExclusiveIsAvailable && device.isFullScreenSupported())) {
			device.setFullScreenWindow(refreshFrame);
		}
		//This can only be done with a real graphics device since it would reassign the printer Simulation
		//OLD getConfiguration().getMachineConfig().setOSMonitorID(device.getDefaultConfiguration().getDevice().getIDstring());
		//TODO: we shut this off. Is that bad?
		//device.getConfiguration().getMachineConfig().setOSMonitorID(device.getIDstring());
		
		// hide mouse in full screen
		Toolkit toolkit = Toolkit.getDefaultToolkit();
	    Point hotSpot = new Point(0,0);
	    BufferedImage cursorImage = new BufferedImage(1, 1, BufferedImage.TRANSLUCENT); 
	    Cursor invisibleCursor = toolkit.createCustomCursor(cursorImage, hotSpot, "InvisibleCursor");        
	    refreshFrame.setCursor(invisibleCursor);
	    return refreshFrame;
	}

	@Override
	public BufferedImage buildBufferedImage(int x, int y) {
		return new BufferedImage(x, y, BufferedImage.TYPE_4BYTE_ABGR);
	}
}
