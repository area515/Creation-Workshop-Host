package org.area515.resinprinter.job;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.TexturePaint;
import java.awt.image.BufferedImage;

import org.area515.resinprinter.job.AbstractPrintFileProcessor.DataAid;
import org.area515.resinprinter.job.render.CurrentImageRenderer;

public class STLImageRenderer extends CurrentImageRenderer {
	private boolean previewMode;
	
	public class HatchPaint extends TexturePaint {
		public HatchPaint(int hatchSize, int lineWidth, Color col1, Color col2) {
			super(new BufferedImage(hatchSize, hatchSize, BufferedImage.TYPE_4BYTE_ABGR), new Rectangle(hatchSize, hatchSize));
			Graphics2D g = (Graphics2D)getImage().getGraphics();
		
			if (lineWidth > 0) {
				//Under Line
				g.setColor(col2);
				g.fillPolygon(new int[]{hatchSize, hatchSize, lineWidth, 0, 0, hatchSize - lineWidth}, new int[]{0, lineWidth, hatchSize, hatchSize, hatchSize - lineWidth, 0}, 6);
				
				//Over Line
				g.setColor(col1);
				g.fillPolygon(new int[]{0, lineWidth, hatchSize, hatchSize, hatchSize - lineWidth, 0}, new int[]{0, 0, hatchSize - lineWidth, hatchSize, hatchSize, lineWidth}, 6);
		
				//Corners
				g.setColor(col2);
				g.fillPolygon(new int[]{0, lineWidth, 0}, new int[]{0, 0, lineWidth}, 3);
				
				g.setColor(col2);
				g.fillPolygon(new int[]{hatchSize, hatchSize - lineWidth, hatchSize}, new int[]{hatchSize, hatchSize, hatchSize - lineWidth}, 3);
				return;
			}
			
			//Under Line
			g.setColor(col2);
			g.drawLine(0, 0, hatchSize, hatchSize);
				
			//Over Line
			g.setColor(col1);
			g.drawLine(hatchSize, 0, 0, hatchSize);
		
			//Corners
			g.setColor(col2);
			g.drawLine(hatchSize, 0, hatchSize, 0);
			g.drawLine(0, hatchSize, 0, hatchSize);
			return;
		}
	}

	public STLImageRenderer(DataAid aid, AbstractPrintFileProcessor<?,?> processor, Object imageIndexToBuild, boolean previewMode) {
		super(aid, processor, imageIndexToBuild);
		this.previewMode = previewMode;
	}

	@Override
	public BufferedImage renderImage(BufferedImage imageToDisplay) {
		STLDataAid aid = (STLDataAid)this.aid;
		aid.slicer.colorizePolygons(null, null);
		if (imageToDisplay == null) {
			imageToDisplay = aid.printer.createBufferedImageFromGraphicsOutputInterface((int)aid.slicer.getWidthPixels(), (int)aid.slicer.getHeightPixels());
		}
		
		Graphics2D g2 = (Graphics2D)imageToDisplay.getGraphics();
		if (previewMode) {
			g2.setPaint(new HatchPaint(50, 8, Color.RED, Color.RED.darker().darker()));
			g2.fillRect(0, 0, (int)aid.slicer.getWidthPixels() - 1, (int)aid.slicer.getHeightPixels() - 1);
			g2.setColor(Color.RED);
			g2.drawRect(0, 0, (int)aid.slicer.getWidthPixels() - 1, (int)aid.slicer.getHeightPixels() - 1);
		}

		aid.slicer.paintSlice(g2, !previewMode);
		
		//imageToDisplay.setArea((double)aid.slicer.getBuildArea());//TODO: This won't work if affine transforms are applied afterwards!
		return imageToDisplay;
	}
}
