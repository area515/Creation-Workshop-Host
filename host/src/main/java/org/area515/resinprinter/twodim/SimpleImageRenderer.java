package org.area515.resinprinter.twodim;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.area515.resinprinter.job.AbstractPrintFileProcessor;
import org.area515.resinprinter.job.JobManagerException;
import org.area515.resinprinter.job.AbstractPrintFileProcessor.DataAid;
import org.area515.resinprinter.job.render.CurrentImageRenderer;

public class SimpleImageRenderer extends CurrentImageRenderer {
	private static final Logger logger = LogManager.getLogger();

	public SimpleImageRenderer(DataAid aid, AbstractPrintFileProcessor<?, ?> processor, Object imageIndexToBuild) {
		super(aid, processor, imageIndexToBuild);
	}

	@Override
	public BufferedImage renderImage(BufferedImage image) throws JobManagerException {
		try {
			BufferedImage newImage = ImageIO.read((File)imageIndexToBuild);
			if (newImage == null) {
				logger.error("Invalid file:" + imageIndexToBuild);
				throw new JobManagerException("BufferedImage returned null on:" + imageIndexToBuild);
			}
			return newImage;
		} catch (IOException e) {
			throw new JobManagerException("Unable to read image:" + imageIndexToBuild, e);
		}
	}
}
