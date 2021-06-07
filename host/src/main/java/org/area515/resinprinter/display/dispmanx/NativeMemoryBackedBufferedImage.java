package org.area515.resinprinter.display.dispmanx;

import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DirectColorModel;
import java.awt.image.SampleModel;
import java.awt.image.SinglePixelPackedSampleModel;
import java.awt.image.WritableRaster;
import java.nio.ByteBuffer;
import java.util.Hashtable;

import com.sun.jna.Memory;

public class NativeMemoryBackedBufferedImage extends BufferedImage {
	private Memory memory;
	
	public NativeMemoryBackedBufferedImage(Memory memory, ColorModel cm, WritableRaster raster, boolean isRasterPremultiplied, Hashtable<?, ?> properties) {
		super(cm, raster, isRasterPremultiplied, properties);
		this.memory = memory;
	}
	
	public Memory getMemory() {
		return memory;
	}

	public int getPitch() {
        return ((4 * getWidth() + (getHeight())-1) & ~((getHeight())-1));
    }	    
    
    private static int getPitch(int x, int y) {
        return ((x + (y)-1) & ~((y)-1));
    }

	public static NativeMemoryBackedBufferedImage newInstance(int width, int height) {
		final int pitch = getPitch(4 * width, 32);
		Memory pixelMemory = new Memory(pitch * height);
		final ByteBuffer buffer = pixelMemory.getByteBuffer(0, pixelMemory.size());
		DataBuffer nativeScreenBuffer = new DataBuffer(DataBuffer.TYPE_INT, width * height) {
			@Override
			public int getElem(int bank, int i) {
				return (buffer.get(i * 4)) | (buffer.get(i * 4 + 1) << 8) | (buffer.get(i * 4 + 2) << 16) | (buffer.get(i * 4 + 3) << 24);
			}
		  
			
			@Override
			public void setElem(int bank, int i, int val) {
				buffer.put(i * 4 + 0, (byte)val);				//b
				buffer.put(i * 4 + 1, (byte)(val >> 8));		//g
				buffer.put(i * 4 + 2, (byte)(val >> 16));		//r
				buffer.put(i * 4 + 3, (byte)(val >> 24));		//a
				//System.out.println("val:" + Integer.toBinaryString((byte)val)); //1 0 1 0 = g | 1 1 0 0 = r | 1 0 0 1 = b
			}
		};
	
		SampleModel argb = new SinglePixelPackedSampleModel(
		    DataBuffer.TYPE_INT, 
		    width, 
		    height,
		    new int[] { 0xFF0000, 0xFF00, 0xFF, 0xFF000000 });
		sun.awt.image.WritableRasterNative raster = new sun.awt.image.WritableRasterNative(argb, nativeScreenBuffer){};
		
		return new NativeMemoryBackedBufferedImage(
				pixelMemory,
				new DirectColorModel(32, 0xFF0000, 0xFF00, 0xFF, 0xFF000000),
				raster, 
				false, 
				null);
	}
}
