package org.area515.resinprinter.text;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.area515.resinprinter.job.AbstractPrintFileProcessor;
import org.area515.resinprinter.job.AbstractPrintFileProcessor.DataAid;
import org.area515.resinprinter.job.JobManagerException;
import org.area515.resinprinter.job.PrintJob;
import org.area515.resinprinter.printer.SlicingProfile.TwoDimensionalSettings;
import org.area515.resinprinter.services.PrinterService;
import org.area515.resinprinter.twodim.TwoDimensionalImageRenderer;

public class TextImageRenderer extends TwoDimensionalImageRenderer {
    private static final Logger logger = LogManager.getLogger();

    public TextImageRenderer(DataAid aid, AbstractPrintFileProcessor<?, ?> processor, Object imageIndexToBuild) {
		super(aid, processor, imageIndexToBuild);
	}
   
	@Override
	public BufferedImage scaleImageAndDetectEdges(PrintJob printJob) throws JobManagerException {
		return waitForImage();
	}
	
	private Object[] readTextDataFromFile(File textFile) throws JobManagerException {
		try (BufferedReader reader = new BufferedReader(new FileReader(textFile))) {
			return reader.lines().toArray();
		} catch (IOException e) {
			logger.error("IO error while reading file:" + textFile, e);
			throw new JobManagerException("There was a problem reading this file.");
		}
	}
	
	@Override
	public BufferedImage loadImageFromFile(PrintJob textFile) throws JobManagerException {
		Object[] lines = readTextDataFromFile(textFile.getJobFile());
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_4BYTE_ABGR);
        Graphics2D chickenEggGraphics = img.createGraphics();

        chickenEggGraphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        chickenEggGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		Font font = textFile.buildFont();
		chickenEggGraphics.setFont(font);
		FontMetrics metrics = chickenEggGraphics.getFontMetrics();
		double maxWidth = 0;
		double totalHeight = 0;
        for (int t = 0; t < lines.length; t++) {
        	String currentLine = ((String)lines[t]);
        	Rectangle2D rect = metrics.getStringBounds(currentLine.toCharArray(), 0, currentLine.length(), chickenEggGraphics);
        	if (rect.getWidth() > maxWidth) {
        		maxWidth = rect.getWidth();
        	}
        	totalHeight += rect.getHeight();
        }
        
        if (maxWidth < 1) {
        	maxWidth = 1;
        }
        if (totalHeight < 1) {
        	totalHeight = 1;
        }
		BufferedImage textImage = aid.printer.createBufferedImageFromGraphicsOutputInterface((int)maxWidth, (int)totalHeight);
		Graphics2D textGraphics = (Graphics2D)textImage.getGraphics();
		textGraphics.setFont(font);
		textGraphics.setColor(Color.WHITE);
		textGraphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		textGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (int t = 0; t < lines.length; t++) {
        	String currentLine = ((String)lines[t]);
        	textGraphics.drawString(currentLine, 0, metrics.getAscent() + (t * metrics.getHeight()));
        }
        
        return textImage;
	}
}
