package org.area515.resinprinter.network;

import java.util.Arrays;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;

public class MatchStringArray extends BaseMatcher<String[]> {
	private String firstString;
	private String[] fullMatch;
	
	public MatchStringArray(String firstString) {
		this.firstString = firstString;
	}
	public MatchStringArray(String[] fullMatch) {
		this.fullMatch = fullMatch;
	}
	
	public boolean tryFirstMatch(String[] found) {
		for (String string : found) {
			if (string == null) {
				continue;
			}
			
			if (string.matches(firstString)) {
				return true;
			}
		}
		return false;
	}
	
	public boolean tryFullMatch(String[] found) {
		return Arrays.equals(fullMatch, found);
	}
	
	@Override
	public boolean matches(Object item) {
		if (item == null || !(item instanceof String[])) {
			return false;
		}
		
		String[] stringItem = (String[])item;
		if (stringItem.length == 0) {
			return false;
		}
		
		if (firstString != null) {
			return tryFirstMatch(stringItem);
		}
		
		return tryFullMatch(stringItem);
	}

	@Override
	public void describeTo(Description description) {
		System.out.println("hello");
	}
}

