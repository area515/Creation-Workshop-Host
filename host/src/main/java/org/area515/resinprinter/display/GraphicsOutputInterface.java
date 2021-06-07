package org.area515.resinprinter.display;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import org.area515.resinprinter.printer.PrinterConfiguration;

public interface GraphicsOutputInterface {
	public boolean isDisplayBusy();	//It's not necessary, but it's helpful to let the user know(via a gui) that the display might be busy
	public void resetSliceCount();
	public void dispose();
	public void showBlankImage();
	public void showCalibrationImage(int xPixels, int yPixels);
	public void showGridImage(int pixels);
	public void showImage(BufferedImage image, boolean incrementSlice);
	public Rectangle getBoundary();
	public String getIDstring();
	public String buildIDString();
	public BufferedImage buildBufferedImage(int x, int y);
	public GraphicsOutputInterface initializeDisplay(String displayId, PrinterConfiguration configuration);
	
	public static void showGrid(Graphics2D g2, Rectangle screenSize, int gridSquareSize) {
		g2.setBackground(Color.black);
		g2.clearRect(0, 0, screenSize.width, screenSize.height);
		g2.setColor(Color.RED);
		for (int x = 0; x < screenSize.width; x += gridSquareSize) {
			g2.drawLine(x, 0, x, screenSize.height);
		}
		
		for (int y = 0; y < screenSize.height; y += gridSquareSize) {
			g2.drawLine(0, y, screenSize.width, y);
		}
	}
	
	public static void showCalibration(Graphics2D g2, Rectangle screenSize, int calibrationX, int calibrationY) {
		g2.setBackground(Color.black);
		g2.clearRect(0, 0, screenSize.width, screenSize.height);
		g2.setColor(Color.RED);
		int startingX = screenSize.width / 2 - calibrationX / 2;
		int startingY = screenSize.height / 2 - calibrationY / 2;
		int halfLengthOfDimLines = 50;
		
		//X Dimension lines
		g2.drawLine(startingX               , screenSize.height / 2 - halfLengthOfDimLines, startingX               , screenSize.height / 2 + halfLengthOfDimLines);
		g2.drawLine(startingX + calibrationX, screenSize.height / 2 - halfLengthOfDimLines, startingX + calibrationX, screenSize.height / 2 + halfLengthOfDimLines);
		
		//Y Dimension lines
		g2.drawLine(screenSize.width / 2 - halfLengthOfDimLines, startingY               , screenSize.width / 2 + halfLengthOfDimLines, startingY);
		g2.drawLine(screenSize.width / 2 - halfLengthOfDimLines, startingY + calibrationY, screenSize.width / 2 + halfLengthOfDimLines, startingY + calibrationY);
							
		//Vertical line of cross
		g2.drawLine(screenSize.width / 2, startingY, screenSize.width / 2, startingY + calibrationY);

		//Horizontal line of cross
		g2.setStroke(new BasicStroke(5, 0, 0, 1.0f, new float[]{10, 10}, 2.0f));
		g2.drawLine(startingX, screenSize.height / 2, startingX + calibrationX, screenSize.height / 2);
	}
}
