package org.area515.resinprinter.twodim;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import javax.script.ScriptException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.area515.resinprinter.job.AbstractPrintFileProcessor;
import org.area515.resinprinter.job.AbstractPrintFileProcessor.DataAid;
import org.area515.resinprinter.job.JobManagerException;
import org.area515.resinprinter.job.render.CurrentImageRenderer;
import org.area515.resinprinter.job.render.RenderingContext;
import org.area515.resinprinter.printer.SlicingProfile.TwoDimensionalSettings;
import org.area515.util.Log4jUtil;
import org.area515.util.TemplateEngine;

public class PlatformImageRenderer extends CurrentImageRenderer {
	private static final Logger logger = LogManager.getLogger();
	protected static final String EXTRUSION_IMAGE = "lastExtrusionImage";
	
	private int totalPlatformSlices;
	private CurrentImageRenderer extrusionImageRenderer;
	
	public PlatformImageRenderer(DataAid aid, AbstractPrintFileProcessor<?,?> processor, Object imageIndexToBuild, int totalPlatformSlices, CurrentImageRenderer extrusionImageRenderer) {
		super(aid, processor, imageIndexToBuild);
		this.totalPlatformSlices = totalPlatformSlices;
		this.extrusionImageRenderer = extrusionImageRenderer;
	}
	
	protected BufferedImage getStarterImage(BufferedImage platformImage) {
		if (platformImage == null) {
			return aid.printer.createBufferedImageFromGraphicsOutputInterface(aid.xResolution, aid.yResolution);
		}

		Graphics2D g = (Graphics2D)platformImage.getGraphics();
		g.setColor(new Color(0, true));
		g.setBackground(new Color(0, true));
		g.clearRect(0, 0, platformImage.getWidth(), platformImage.getHeight());
		return platformImage;
	}
	
	@Override
	public BufferedImage renderImage(BufferedImage platformImage) throws JobManagerException {
		final BufferedImage finalPlatformImage = getStarterImage(platformImage);
		
		logger.trace("Writing renderImage1finalPlatformImage" + imageIndexToBuild + ":{}", () -> Log4jUtil.logImage(finalPlatformImage, "renderImage1finalPlatformImage" + imageIndexToBuild + ".png"));

		TwoDimensionalSettings settings = aid.slicingProfile.getTwoDimensionalSettings();
		if (settings == null) {
			throw new JobManagerException("This printer doesn't have it's 2D File Settings setup properly");
		}
		
		String platformScript = settings.getPlatformCalculator();
		if (platformScript == null) {
			throw new JobManagerException("This printer doesn't have 2D platform rendering calculator setup");
		}
		
		RenderingContext platformToRender = aid.cache.getOrCreateIfMissing(imageIndexToBuild);
		RenderingContext extrusionImageData = aid.cache.getOrCreateIfMissing(EXTRUSION_IMAGE);
		ReentrantLock lock = platformToRender.getLock();
		lock.lock();
		try {
			if (extrusionImageData.getPreTransformedImage() == null) {
				extrusionImageData.setPreTransformedImage(extrusionImageRenderer.renderImage(null));
			}
			logger.trace("Writing renderImage2finalPlatformImage" + imageIndexToBuild + ":{}", () -> Log4jUtil.logImage(finalPlatformImage, "renderImage2finalPlatformImage" + imageIndexToBuild + ".png"));

			Map<String, Object> overrides = new HashMap<>();
			overrides.put("totalPlatformSlices", totalPlatformSlices);
			try {
				TemplateEngine.runScriptInImagingContext(finalPlatformImage, extrusionImageData.getPreTransformedImage(), aid.printJob, aid.printer, platformToRender.getScriptEngine(), overrides, platformScript, "2D Platform rendering script", false);
			} catch (ScriptException e) {
				throw new JobManagerException("Failed to execute script", e);
			}
			logger.trace("Writing renderImage3finalPlatformImage" + imageIndexToBuild + ":{}", () -> Log4jUtil.logImage(finalPlatformImage, "renderImage3finalPlatformImage" + imageIndexToBuild + ".png"));

			return finalPlatformImage;
		} finally {
			lock.unlock();
		}
	}
}
