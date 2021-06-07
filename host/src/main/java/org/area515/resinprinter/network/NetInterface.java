package org.area515.resinprinter.network;

import java.util.ArrayList;
import java.util.List;

public class NetInterface {
	private List<WirelessNetwork> wirelessNetworks = new ArrayList<WirelessNetwork>();
	private String name;

	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	
	public List<WirelessNetwork> getWirelessNetworks() {
		return wirelessNetworks;
	}
	public void setWirelessNetworks(List<WirelessNetwork> wirelessNetworks) {
		this.wirelessNetworks = wirelessNetworks;
	}
	
	public String toString() {
		return name;
	}
}
