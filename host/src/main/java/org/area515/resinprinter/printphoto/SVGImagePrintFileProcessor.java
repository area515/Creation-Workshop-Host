package org.area515.resinprinter.printphoto;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import org.apache.batik.anim.dom.SVGDOMImplementation;
import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.TranscodingHints;
import org.apache.batik.transcoder.image.ImageTranscoder;
import org.apache.batik.util.SVGConstants;
import org.apache.commons.io.FileUtils;
import org.area515.resinprinter.job.AbstractPrintFileProcessor;
import org.area515.resinprinter.job.AbstractPrintFileProcessor.DataAid;
import org.area515.resinprinter.job.JobManagerException;
import org.area515.resinprinter.job.PrintJob;
import org.area515.resinprinter.twodim.TwoDimensionalImageRenderer;

public class SVGImagePrintFileProcessor extends ImagePrintFileProcessor {
	@Override
	public String[] getFileExtensions() {
		return new String[]{"svg"};
	}

	@Override
	public boolean acceptsFile(File processingFile) {
		String name = processingFile.getName().toLowerCase();
		return name.endsWith("svg");
	}
	
	
	@Override
	public TwoDimensionalImageRenderer createTwoDimensionalRenderer(DataAid aid, Object imageIndexToBuild) {
		return new TwoDimensionalImageRenderer(aid, this, imageIndexToBuild) {
			@Override
			public BufferedImage loadImageFromFile(PrintJob processingFile) throws JobManagerException {
				try {
					final BufferedImage[] imagePointer = new BufferedImage[1];
					
					//Optimized for quality not performance
				    String css = "svg {" +
				            "shape-rendering: geometricPrecision;" +
				            "text-rendering:  geometricPrecision;" +
				            "color-rendering: optimizeQuality;" +
				            "image-rendering: optimizeQuality;" +
				            "}";
				    File cssFile = File.createTempFile("batik-default-override-", ".css");
				    FileUtils.writeStringToFile(cssFile, css);

				    TranscodingHints transcoderHints = new TranscodingHints();
				    transcoderHints.put(ImageTranscoder.KEY_XML_PARSER_VALIDATING, Boolean.FALSE);
				    transcoderHints.put(ImageTranscoder.KEY_DOM_IMPLEMENTATION, SVGDOMImplementation.getDOMImplementation());
				    transcoderHints.put(ImageTranscoder.KEY_DOCUMENT_ELEMENT_NAMESPACE_URI, SVGConstants.SVG_NAMESPACE_URI);
				    transcoderHints.put(ImageTranscoder.KEY_DOCUMENT_ELEMENT, "svg");
				    transcoderHints.put(ImageTranscoder.KEY_USER_STYLESHEET_URI, cssFile.toURI().toString());

				    try {
				        TranscoderInput input = new TranscoderInput(new FileInputStream(processingFile.getJobFile()));
				        ImageTranscoder trans = new ImageTranscoder() {
				            @Override
				            public BufferedImage createImage(int w, int h) {
				            	return aid.printer.createBufferedImageFromGraphicsOutputInterface(w,  h);
				            }

				            @Override
				            public void writeImage(BufferedImage image, TranscoderOutput out) throws TranscoderException {
				                imagePointer[0] = image;
				            }
				        };
				        trans.setTranscodingHints(transcoderHints);
				        trans.transcode(input, null);
				    } catch (TranscoderException ex) {
				        throw new IOException(processingFile + " doesn't seem to be an SVG file.");
				    } finally {
				        cssFile.delete();
				    }

				    return imagePointer[0];
				} catch (IOException e) {
					throw new JobManagerException("Couldn't load image file:" + processingFile, e);
				}
			}
		};
	}

	@Override
	public String getFriendlyName() {
		return "Scalable Vector Graphics";
	}
}
