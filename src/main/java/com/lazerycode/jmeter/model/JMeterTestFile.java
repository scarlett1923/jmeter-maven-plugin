package com.lazerycode.jmeter.model;

import org.dom4j.Element;

/**
 * Created by shenjiali01 on 2014/7/24.
 */
public class JMeterTestFile {
	private String filename;
	private boolean isOn;
	private String level;


	public JMeterTestFile(Element elm){
		this.filename=elm.attributeValue("name");
		this.isOn=Boolean.parseBoolean(elm.attributeValue("ison"));
		this.level=elm.attributeValue("level");
	}

	public void setFilename(String name){
		this.filename=name;
	}

	public void setisOn(boolean ison){
		this.isOn=ison;
	}

	public String getFilename(){
		return this.filename;
	}

	public String getLevel() {
		return level;
	}

	public boolean getIsOn(){
		return this.isOn;
	}
}
