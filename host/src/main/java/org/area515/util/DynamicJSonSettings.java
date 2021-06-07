package org.area515.util;

import java.util.HashMap;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;

public class DynamicJSonSettings {
	private HashMap<String, Object> settings;
	
    @JsonAnyGetter
    public HashMap<String, Object> getSettings() {
        return settings;
    }
    
    public void setSettings(HashMap<String, Object> settings) {
        this.settings = settings;
    }
    
    @JsonAnySetter
    public void putSettings(String key, Object value) {
        if (settings == null) {
        	settings = new HashMap<>();
        }
        
        settings.put(key, value);
    }
    
    public String toString() {
    	return settings + "";
    }
}
