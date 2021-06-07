package org.area515.resinprinter.network;

import java.util.List;
import java.util.Map;

public interface NetworkManager {
	public List<NetInterface> getNetworkInterfaces();
	public void connectToWirelessNetwork(WirelessNetwork net);
	public String getHostname();
	public Map<String, String> getIPs();
	public Map<String, String> getMACs();
	public void setHostname(String hostname);
}
