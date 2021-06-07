package org.area515.resinprinter.display.dispmanx;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.area515.resinprinter.display.GraphicsOutputInterface;
import org.area515.resinprinter.display.InappropriateDeviceException;
import org.area515.resinprinter.printer.PrinterConfiguration;
import org.area515.util.Log4jUtil;

import com.sun.jna.Memory;
import com.sun.jna.ptr.IntByReference;

public class DispManXDevice implements GraphicsOutputInterface {
	private static final String IMAGE_REALIZE_TIMER = "Image Realize";
    private static final Logger logger = LogManager.getLogger();
    private static boolean BCM_INIT = false;
    private static ReentrantLock BCM_LOCK = new ReentrantLock(true);

    private ReentrantLock displayLock = new ReentrantLock(true);
    private ReentrantLock activityLock = new ReentrantLock(true);
    private Rectangle bounds = new Rectangle();
    private SCREEN screen;
    private VC_DISPMANX_ALPHA_T.ByReference alpha;
    private int displayHandle;
    private boolean screenInitialized = false;
    private String displayName;
    
    //For dispmanx
    private int imageResourceHandle;
    private int imageElementHandle;
    
    //For Calibration and Grid
    private NativeMemoryBackedBufferedImage calibrationAndGridImage;
    
    //This is a cache for when callers use this class without a NativeMemoryBackedBufferedImage
    private Memory imagePixels;
    private int imageWidth;
    private int imageHeight;
    
    public DispManXDevice(String displayName, SCREEN screen) throws InappropriateDeviceException {
		this.displayName = displayName;
		this.screen = screen;
		
		//Call a harmless method to ensure that Dispmanx lib is initialized
        VC_RECT_T.ByReference sourceRect = new VC_RECT_T.ByReference();
        DispManX.INSTANCE.vc_dispmanx_rect_set(sourceRect, 0, 0, 0, 0);
	}
    
    private static void bcmHostInit() {
    	if (BCM_INIT) {
    		return;
    	}
    	
    	BCM_LOCK.lock();
    	try {
	    	if (BCM_INIT) {
	    		return;
	    	}
	    	
	    	logger.info("initialize bcm host");
	    	int returnCode = DispManX.INSTANCE.bcm_host_init();
	    	if (returnCode != 0) {
	    		throw new IllegalArgumentException("bcm_host_init failed with:" + returnCode);
	    	}
	    	BCM_INIT = true;
    	} finally {
    		BCM_LOCK.unlock();
    	}
    }
    
    private void initializeScreen() {
    	if (screenInitialized) {
    		return;
    	}
    	
    	displayLock.lock();
    	try {
        	if (screenInitialized) {
        		return;
        	}

        	logger.info("initialize screen");
	    	bcmHostInit();
	    	
	        IntByReference width = new IntByReference();
	        IntByReference height = new IntByReference();
	    	int returnCode = DispManX.INSTANCE.graphics_get_display_size(screen.getId(), width, height);
	    	if (returnCode != 0) {
	    		throw new IllegalArgumentException("graphics_get_display_size failed with:" + returnCode);
	    	}
	    	bounds.setBounds(0, 0, width.getValue(), height.getValue());
	    	
	    	displayHandle = DispManX.INSTANCE.vc_dispmanx_display_open(screen.getId());
	    	if (displayHandle == 0) {
	    		throw new IllegalArgumentException("vc_dispmanx_display_open failed with:" + returnCode);
	    	}
	    	
	        VC_DISPMANX_ALPHA_T.ByReference alpha = new VC_DISPMANX_ALPHA_T.ByReference();
	        alpha.flags = ALPHA.DISPMANX_FLAGS_ALPHA_FROM_SOURCE.getFlag() | ALPHA.DISPMANX_FLAGS_ALPHA_FIXED_ALL_PIXELS.getFlag();
	        alpha.opacity = 255;
	        screenInitialized = true;
    	} finally {
    		displayLock.unlock();
    	}
    }
    
	@Override
	public void dispose() {
    	displayLock.lock();
    	try {
			logger.info("dispose screen");
			removeAllElementsFromScreen();
	    	logger.info("vc_dispmanx_display_close result:" + DispManX.INSTANCE.vc_dispmanx_display_close(displayHandle));
	    	imagePixels = null;
	    	calibrationAndGridImage = null;
	    	imageWidth = 0;
	    	imageHeight = 0;
	    	screenInitialized = false;
    	} finally {
    		displayLock.unlock();
    	}
	}
	
    public static int getPitch( int x, int y ) {
        return ((x + (y)-1) & ~((y)-1));
    }
    
	private Memory loadBitmapRGB565(BufferedImage image, Memory destPixels, IntByReference width, IntByReference height, IntByReference pitchByRef) {
		int bytesPerPixel = 2;
		int pitch = getPitch(bytesPerPixel * image.getWidth(), 32);
		pitchByRef.setValue(pitch);
		if (destPixels == null) {
			destPixels = new Memory(pitch * image.getHeight());
		}
		
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
        		int rgb = image.getRGB(x, y);
        		destPixels.setShort((y*(pitch / bytesPerPixel) + x) * bytesPerPixel, (short)(((rgb & 0xf80000) >>> 8) | ((rgb & 0xfc00) >>> 5) | (rgb & 0xf8 >>> 3)));
            }
        }
        width.setValue(image.getWidth());
        height.setValue(image.getHeight());
        return destPixels;
	}

	private Memory loadBitmapARGB8888(BufferedImage image, Memory destPixels, IntByReference width, IntByReference height, IntByReference pitchByRef) {
		int bytesPerPixel = 4;
		int pitch = getPitch(bytesPerPixel * image.getWidth(), 32);
		pitchByRef.setValue(pitch);
		if (destPixels == null) {
			destPixels = new Memory(pitch * image.getHeight());
		}
		
		logger.debug("loadBitmapARGB8888 alg started:{}", () -> Log4jUtil.splitTimer(IMAGE_REALIZE_TIMER));
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
        		destPixels.setInt((y*(pitch / bytesPerPixel) + x) * bytesPerPixel, image.getRGB(x, y));
            }
        }
		logger.debug("loadBitmapARGB8888 alg complete:{}", () -> Log4jUtil.splitTimer(IMAGE_REALIZE_TIMER));

        width.setValue(image.getWidth());
        height.setValue(image.getHeight());
        return destPixels;
	}

	@Override
	public void showBlankImage() {
		initializeScreen();
		removeAllElementsFromScreen();
	}

	private void removeAllElementsFromScreen() {
		logger.info("screen cleanup started");
        int updateHandle = DispManX.INSTANCE.vc_dispmanx_update_start( 0 );
        if (updateHandle == 0) {
        	logger.info("vc_dispmanx_update_start failed");
        } else {
        	logger.debug("image vc_dispmanx_element_remove result:" + DispManX.INSTANCE.vc_dispmanx_element_remove(updateHandle, imageElementHandle));
        	logger.debug("vc_dispmanx_update_submit_sync result:" + DispManX.INSTANCE.vc_dispmanx_update_submit_sync(updateHandle));
        	logger.debug("image vc_dispmanx_resource_delete result:" + DispManX.INSTANCE.vc_dispmanx_resource_delete(imageResourceHandle));
        }
	}
	
	private void initializeCalibrationAndGridImage() {
		if (calibrationAndGridImage != null) {
			return;
		}
		
		calibrationAndGridImage = NativeMemoryBackedBufferedImage.newInstance(bounds.width, bounds.height);
	}
	
	@Override
	public void showCalibrationImage(int xPixels, int yPixels) {
		logger.debug("Calibration assigned:{}", () -> Log4jUtil.startTimer(IMAGE_REALIZE_TIMER));
		showBlankImage();
		initializeCalibrationAndGridImage();
		Graphics2D graphics = (Graphics2D)calibrationAndGridImage.createGraphics();
		GraphicsOutputInterface.showCalibration(graphics, bounds, xPixels, yPixels);
		graphics.dispose();
		showImage(null, calibrationAndGridImage);
		logger.debug("Calibration realized:{}", () -> Log4jUtil.completeTimer(IMAGE_REALIZE_TIMER));
	}
	
	@Override
	public void showGridImage(int pixels) {
		logger.debug("Grid assigned:{}", () -> Log4jUtil.startTimer(IMAGE_REALIZE_TIMER));
		showBlankImage();
		initializeCalibrationAndGridImage();
		Graphics2D graphics = (Graphics2D)calibrationAndGridImage.createGraphics();
		GraphicsOutputInterface.showGrid(graphics, bounds, pixels);
		graphics.dispose();
		
		showImage(null, calibrationAndGridImage);		
		logger.debug("Grid realized:{}", () -> Log4jUtil.completeTimer(IMAGE_REALIZE_TIMER));
	}
	
	private Memory showImage(Memory memory, BufferedImage image) {
		activityLock.lock();
		try {
			showBlankImage();//delete the old resources, this is lightning fast...
			
	        IntByReference imageWidth = new IntByReference();
	        IntByReference imageHeight = new IntByReference();
	        IntByReference imagePitch = new IntByReference();
	        
	        if (!(image instanceof NativeMemoryBackedBufferedImage)) {
	        	memory = loadBitmapARGB8888(image, memory, imageWidth, imageHeight, imagePitch);
	        } else {
	        	memory = ((NativeMemoryBackedBufferedImage)image).getMemory();
	    		int bytesPerPixel = 4;
	    		int pitch = getPitch(bytesPerPixel * image.getWidth(), 32);
	    		imagePitch.setValue(pitch);
	    		imageWidth.setValue(image.getWidth());
	    		imageHeight.setValue(image.getHeight());
	        }
	        
	        VC_RECT_T.ByReference sourceRect = new VC_RECT_T.ByReference();
	        DispManX.INSTANCE.vc_dispmanx_rect_set(sourceRect, 0, 0, imageWidth.getValue()<<16, imageHeight.getValue()<<16);//Shifting by 16 is a zoom factor of zero
	        
	        IntByReference nativeImageReference = new IntByReference();
	        imageResourceHandle = DispManX.INSTANCE.vc_dispmanx_resource_create(
	        		VC_IMAGE_TYPE_T.VC_IMAGE_ARGB8888.getcIndex(), 
	        		imageWidth.getValue(), 
	        		imageHeight.getValue(), 
	        		nativeImageReference);
	        if (imageResourceHandle == 0) {
	        	throw new IllegalArgumentException("Couldn't create resourceHandle for dispmanx");
	        }
	        
	        logger.debug("ScreenWidth:" + bounds.getWidth() + " ScreenHeight:" + bounds.getHeight() + " ImageWidth:"+ imageWidth.getValue() + " ImageHeight:" + imageHeight.getValue());
	        
	        VC_RECT_T.ByReference sizeRect = new VC_RECT_T.ByReference();
	        DispManX.INSTANCE.vc_dispmanx_rect_set(sizeRect, 0, 0, imageWidth.getValue(), imageHeight.getValue());
	        int returnCode = DispManX.INSTANCE.vc_dispmanx_resource_write_data( 
	        		imageResourceHandle, 
	        		VC_IMAGE_TYPE_T.VC_IMAGE_ARGB8888.getcIndex(), 
	        		imagePitch.getValue() , 
	        		memory, 
	        		sizeRect);
	        if (returnCode != 0) {
	        	throw new IllegalArgumentException("Couldn't vc_dispmanx_resource_write_data for dispmanx:" + returnCode);
	        }
	        
	        int updateHandle = DispManX.INSTANCE.vc_dispmanx_update_start(0);  //This method should be called create update
	        if (updateHandle == 0) {
	        	throw new IllegalArgumentException("Couldn't vc_dispmanx_update_start for dispmanx");
	        }
	
	        VC_RECT_T.ByReference destinationRect = new VC_RECT_T.ByReference();
	        DispManX.INSTANCE.vc_dispmanx_rect_set(
	        		destinationRect, 
	        		(bounds.width - imageWidth.getValue()) / 2, 
	        		(bounds.height - imageHeight.getValue()) / 2, 
	        		imageWidth.getValue(), 
	        		imageHeight.getValue());
	        imageElementHandle = DispManX.INSTANCE.vc_dispmanx_element_add(     //Creates and adds the element to the current screen update
	        		updateHandle, 
	        		displayHandle, 
	        		1, 
	        		destinationRect, 
	        		imageResourceHandle, 
	        		sourceRect, 
	        		PROTECTION.DISPMANX_PROTECTION_NONE.getcConst(), 
	        		alpha, 
	        		0, 
	        		VC_IMAGE_TRANSFORM_T.VC_IMAGE_ROT0.getcConst());
	        if (updateHandle == 0) {
	        	throw new IllegalArgumentException("Couldn't vc_dispmanx_element_add for dispmanx");
	        }
	
	        returnCode = DispManX.INSTANCE.vc_dispmanx_update_submit_sync(updateHandle);//Wait for the update to complete
	        if (returnCode != 0) {
	        	throw new IllegalArgumentException("Couldn't vc_dispmanx_update_submit_sync for dispmanx:" + returnCode);
	        }
	        
	        return memory;
		} finally {
			activityLock.unlock();
		}
	}
	
	@Override
	public void showImage(BufferedImage image, boolean performFullUpdate) {
		logger.debug("Image assigned:{}", () -> Log4jUtil.startTimer(IMAGE_REALIZE_TIMER));
		if (image.getWidth() == imageWidth && image.getHeight() == imageHeight) {
			imagePixels = showImage(imagePixels, image);
		} else {
			imagePixels = showImage(null, image);
		}
		imageWidth = image.getWidth();
		imageHeight = image.getHeight();
		logger.debug("Image realized:{}", () -> Log4jUtil.completeTimer(IMAGE_REALIZE_TIMER));
	}
	
	@Override
	public void resetSliceCount() {
		//Since this isn't used for debugging we don't do anything
	}

	@Override
	public Rectangle getBoundary() {
		initializeScreen();
		return bounds;
	}

	@Override
	public boolean isDisplayBusy() {
		return activityLock.isLocked();
	}

	@Override
	public String getIDstring() {
		return displayName;
	}

	@Override
	public String buildIDString() {
		return displayName;
	}

	@Override
	public GraphicsOutputInterface initializeDisplay(String displayId, PrinterConfiguration configuration) {
		return this;
	}

	@Override
	public BufferedImage buildBufferedImage(int x, int y) {
		return NativeMemoryBackedBufferedImage.newInstance(x,  y);
	}
}
