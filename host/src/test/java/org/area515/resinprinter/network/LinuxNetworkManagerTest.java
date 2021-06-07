package org.area515.resinprinter.network;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringBufferInputStream;
import java.util.List;

import org.area515.util.IOUtilities;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({"javax.management.*"})
public class LinuxNetworkManagerTest {
	public static String SCAN_WIFI_DATA = "wpa_cli v1.0\nCopyright (c) 2004-2012, Jouni Malinen <j@w1.fi> and contributors\n\nThis program is free software. You can distribute it and/or modify it\nunder the terms of the GNU General Public License version 2.\n\nAlternatively, this software may be distributed under the terms of the\nBSD license. See README and COPYING for more details.\n\n\n\n\nInteractive mode\n\n>OK\n>\r<3>CTRL-EVENT-SCAN-RESULTS \n>bssid / frequency / signal level / flags / ssid\n03:15:2a:0c:93:15       2437    -69      [WEP][ESS]\tSomeNetwork\n11:51:a6:71:51:55       2412    92      [WPA-PSK-TKIP+CCMP][WPA2-PSK-TKIP+CCMP][WPS][ESS]\t\\x00\nac:95:17:92:60:20       2437    26      [WPA2-PSK-CCMP][WPS][ESS]\t&#9786;\\u0044\\\\\\x45\\\\u0044Test\n>";
	
	@Test
	@PrepareForTest(IOUtilities.class)
	public void getNetworks() throws IOException {
		String lanName = "wlan0";
		String pongResponse = "PONG";
		String associatedSSID = "SomeNetwork";
		
		Runtime runtime = Mockito.mock(Runtime.class);
		PowerMockito.mockStatic(Runtime.class);
		PowerMockito.when(Runtime.getRuntime()).thenReturn(runtime);

		final ByteArrayOutputStream output = new ByteArrayOutputStream();
		final Process findNetworkInterfacesProcess = Mockito.mock(Process.class);
		Mockito.when(findNetworkInterfacesProcess.getInputStream()).thenReturn(new StringBufferInputStream(lanName));
		Mockito.when(findNetworkInterfacesProcess.getOutputStream()).thenReturn(output);
		Mockito.when(runtime.exec(Mockito.argThat(new MatchStringArray("ip -o link.*")))).then(new Answer<Process>() {
			@Override
			public Process answer(InvocationOnMock invocation) throws Throwable {
				return findNetworkInterfacesProcess;
			}
		});
		
		final Process performPingPong = Mockito.mock(Process.class);
		Mockito.when(performPingPong.getInputStream()).thenReturn(new StringBufferInputStream(pongResponse));
		Mockito.when(performPingPong.getOutputStream()).thenReturn(output);
		Mockito.when(runtime.exec(Mockito.argThat(new MatchStringArray("ping")))).then(new Answer<Process>() {
			@Override
			public Process answer(InvocationOnMock invocation) throws Throwable {
				return performPingPong;
			}
		});
		
		final Process scanForWifiProcess = Mockito.mock(Process.class);
		Mockito.when(scanForWifiProcess.getInputStream()).thenReturn(new StringBufferInputStream(SCAN_WIFI_DATA));
		Mockito.when(scanForWifiProcess.getOutputStream()).thenReturn(output);
		Mockito.when(runtime.exec(Mockito.argThat(new MatchStringArray(new String[]{"wpa_cli", "-i", lanName})))).then(new Answer<Process>() {
			@Override
			public Process answer(InvocationOnMock invocation) throws Throwable {
				return scanForWifiProcess;
			}
		});
		
		final Process performConnectedSSID = Mockito.mock(Process.class);
		Mockito.when(performConnectedSSID.getInputStream()).thenReturn(new StringBufferInputStream(associatedSSID));
		Mockito.when(performConnectedSSID.getOutputStream()).thenReturn(output);
		Mockito.when(runtime.exec(Mockito.argThat(new MatchStringArray("iwgetid")))).then(new Answer<Process>() {
				@Override
				public Process answer(InvocationOnMock invocation) throws Throwable {
					return performConnectedSSID;
				}
			});
		
		List<NetInterface> interfaces = new LinuxNetworkManager().getNetworkInterfaces();
		Assert.assertEquals(1, interfaces.size());
		Assert.assertEquals(lanName, interfaces.get(0).getName());
		Assert.assertEquals(3, interfaces.get(0).getWirelessNetworks().size());
		Assert.assertEquals("SomeNetwork", interfaces.get(0).getWirelessNetworks().get(0).getSsid());
		Assert.assertEquals(true, interfaces.get(0).getWirelessNetworks().get(0).isAssociated());
		Assert.assertEquals("\u0000", interfaces.get(0).getWirelessNetworks().get(1).getSsid());
		Assert.assertEquals(true, interfaces.get(0).getWirelessNetworks().get(1).isHidden());
		Assert.assertEquals("\u263AD\\E\\u0044Test", interfaces.get(0).getWirelessNetworks().get(2).getSsid());
	}
}
