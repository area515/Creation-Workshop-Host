package org.area515.resinprinter.image;

import org.area515.resinprinter.display.dispmanx.NativeMemoryBackedBufferedImage;
import org.junit.Test;

public class NativeImageTest {
	@Test
	public void testNativeImage() {
		NativeMemoryBackedBufferedImage image = NativeMemoryBackedBufferedImage.newInstance(100, 100);
		image.getGraphics();
	}
}
