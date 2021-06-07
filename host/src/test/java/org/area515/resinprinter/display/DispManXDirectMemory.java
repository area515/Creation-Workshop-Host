package org.area515.resinprinter.display;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DirectColorModel;
import java.awt.image.SampleModel;
import java.awt.image.SinglePixelPackedSampleModel;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Hashtable;

import org.area515.resinprinter.display.dispmanx.ALPHA;
import org.area515.resinprinter.display.dispmanx.DispManX;
import org.area515.resinprinter.display.dispmanx.PROTECTION;
import org.area515.resinprinter.display.dispmanx.VC_DISPMANX_ALPHA_T;
import org.area515.resinprinter.display.dispmanx.VC_IMAGE_TRANSFORM_T;
import org.area515.resinprinter.display.dispmanx.VC_IMAGE_TYPE_T;
import org.area515.resinprinter.display.dispmanx.VC_RECT_T;

import com.sun.jna.Memory;
import com.sun.jna.ptr.IntByReference;

public class DispManXDirectMemory {
	public static void res( String str, int val ) {
		if ( val != 0 ) {
			System.out.printf( "%s: %08x\n", str, val );
		} else {
			System.out.println(str + " is 0");
		}
	}
	
	public static class NativeMemoryBackedBufferedImage extends BufferedImage {
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
					return (buffer.get(i * 4)) | (buffer.get(i * 4 + 1) << 8) | (buffer.get(i * 4 + 2) << 16) | (buffer.get(i * 4 + 24));
				}
			  
				@Override
				public void setElem(int bank, int i, int val) {
					buffer.put(i * 4 + 0, (byte)val);				//b
					buffer.put(i * 4 + 1, (byte)(val >> 8));		//g
					buffer.put(i * 4 + 2, (byte)(val >> 16));		//r
					buffer.put(i * 4 + 3, (byte)(val >> 24));		//a
					System.out.println("val:" + Integer.toBinaryString((byte)val)); //1 0 1 0 = g | 1 1 0 0 = r | 1 0 0 1 = b
				}
			};
		
			SampleModel argb = new SinglePixelPackedSampleModel(
			    DataBuffer.TYPE_INT, 
			    width, 
			    height,
			    new int[] { 0xFF0000, 0xFF00, 0xFF, 0xFF000000 });
			
			WritableRaster raster = new WritableRaster(argb, nativeScreenBuffer, new Point()){};
			
			return new NativeMemoryBackedBufferedImage(
					pixelMemory,
					new DirectColorModel(32, 0xFF0000, 0xFF00, 0xFF, 0xFF000000),
					raster, 
					false, 
					null);
		}
	}

	//sudo vi DispManXDirectMemory.java
	//sudo javac -cp lib/*:. DispManXDirectMemory.java
	//sudo sudo java -cp lib/*:. -Djava.awt.headless=true DispManXDirectMemory 2 10
	public static void main(String[] args) throws IOException, InterruptedException {
		DispManX dispMan = DispManX.INSTANCE;
		System.out.println("BCM Initialized:" + dispMan.bcm_host_init());
        
        int screen = Integer.parseInt( args[0] );
        int time = Integer.parseInt( args[1] );
        //IntByReference pitch = new IntByReference();
        IntByReference width = new IntByReference();
        IntByReference height = new IntByReference();
        res( "get display size", dispMan.graphics_get_display_size( screen, width, height ) );
        System.out.printf( "display %d: %d x %d\n", screen, width.getValue(), height.getValue() );
        int display = dispMan.vc_dispmanx_display_open( screen );
        
long timer = System.currentTimeMillis();
	NativeMemoryBackedBufferedImage nativeImage = NativeMemoryBackedBufferedImage.newInstance(width.getValue(), height.getValue());
//	BufferedImage copyFrom = new BufferedImage(width.getValue(), height.getValue(), BufferedImage.TYPE_INT_ARGB); Wow is Java really this slow?
System.out.println("time taken to create NativeMemoryBackedBufferedImage: " +  (System.currentTimeMillis() - timer));
        int pitch = nativeImage.getPitch();
        Graphics2D g = (Graphics2D)nativeImage.createGraphics();
        g.setColor(Color.white);
        g.drawOval(0,  0, 2, 2);
        g.setColor(Color.red);
        //g.setFont(g.getFont().deriveFont((float)30));
        //System.out.println(g.getFont());
        //g.drawString("Hello this is a test", width.getValue() / 2, 50);
        System.out.printf( "bitmap: %d x %d pitch->%d\n", width.getValue(), height.getValue(), pitch);

        VC_RECT_T.ByReference copyRect = new VC_RECT_T.ByReference();
        VC_RECT_T.ByReference sourceRect = new VC_RECT_T.ByReference();
        VC_RECT_T.ByReference destinationRect = new VC_RECT_T.ByReference();
        
        res( "rect set", dispMan.vc_dispmanx_rect_set( copyRect, 0, 0, width.getValue(), height.getValue() ) );
        //This seems to be some form of a zoom factor
        res( "rect set", dispMan.vc_dispmanx_rect_set( sourceRect, 0, 0, width.getValue()<<16, height.getValue()<<16 ) );
        res( "rect set", dispMan.vc_dispmanx_rect_set( destinationRect, 0, 0, width.getValue(), height.getValue() ) );

        timer = System.currentTimeMillis();

        IntByReference ref = new IntByReference();
        int resourceHandle = dispMan.vc_dispmanx_resource_create( 
        		VC_IMAGE_TYPE_T.VC_IMAGE_ARGB8888.getcIndex(), 
        		width.getValue(), 
        		height.getValue(), 
        		ref );
        

        g.drawOval(100, 100, 5, 5);

timer = System.currentTimeMillis();
//copyFrom.copyData(nativeImage.getRaster());
        res( "resource write data", dispMan.vc_dispmanx_resource_write_data( 
        		resourceHandle, 
        		VC_IMAGE_TYPE_T.VC_IMAGE_ARGB8888.getcIndex(), 
        		pitch, 
        		nativeImage.getMemory(), 
        		destinationRect )
        	);

        System.out.println("copyRect:" + copyRect.width + ", " + copyRect.height);
        System.out.println("sourceRect:" + sourceRect.width + ", " + sourceRect.height);
        System.out.println("destinationRect:" + destinationRect.width + ", " + destinationRect.height);
        
        int update = dispMan.vc_dispmanx_update_start( 0 );
        VC_DISPMANX_ALPHA_T.ByReference alpha = new VC_DISPMANX_ALPHA_T.ByReference();
        alpha.flags = ALPHA.DISPMANX_FLAGS_ALPHA_FROM_SOURCE.getFlag() | ALPHA.DISPMANX_FLAGS_ALPHA_FIXED_ALL_PIXELS.getFlag();
        alpha.opacity = 255;
        int element = dispMan.vc_dispmanx_element_add(
        		update, 
        		display, 
        		2010, 
        		destinationRect, 
        		resourceHandle, 
        		sourceRect, 
        		PROTECTION.DISPMANX_PROTECTION_NONE.getcConst(), 
        		alpha, 
        		0, 
        		VC_IMAGE_TRANSFORM_T.VC_IMAGE_ROT0.getcConst() );
        
        res( "submit", dispMan.vc_dispmanx_update_submit_sync( update ) );

System.out.println("Time taken to write to screen!!!:" + (System.currentTimeMillis() - timer));
        
        
        Thread.sleep( time * 1000 );
        update = dispMan.vc_dispmanx_update_start( 0 );

        res( "element remove", dispMan.vc_dispmanx_element_remove( update, element ) );
        res( "submit", dispMan.vc_dispmanx_update_submit_sync( update ) );
        res( "resource delete", dispMan.vc_dispmanx_resource_delete( resourceHandle ) );
        res( "display close", dispMan.vc_dispmanx_display_close( display ) );
	}
}
