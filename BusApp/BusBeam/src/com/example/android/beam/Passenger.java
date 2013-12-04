package com.example.android.beam;

import java.io.Serializable;

import android.location.Location;

public class Passenger implements Serializable{

	/**
	 * 
	 */
	private static final long serialVersionUID = -5147441884881057546L;
	private String id;
	private String time;
	private String action;
	double longitude;
	private double lattitude;
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public String getTime() {
		return time;
	}
	public void setTime(String time) {
		this.time = time;
	}
	public String getAction() {
		return action;
	}
	public void setAction(String action) {
		this.action = action;
	}
	public double getLongitude() {
		return longitude;
	}
	public void setLongitude(double longitude) {
		this.longitude = longitude;
	}
	public double getLattitude() {
		return lattitude;
	}
	public void setLattitude(double lattitude) {
		this.lattitude = lattitude;
	}
		
}
