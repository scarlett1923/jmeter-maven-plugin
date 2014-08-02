package com.lazerycode.jmeter.model;

import org.dom4j.Element;

import java.util.LinkedList;
import java.util.List;

/**
 * Created by shenjiali01 on 2014/7/29.
 */
public class JMeterClass {
	private String filename;
	private boolean isOn;


	private List<JMeterTestFile> testList=new LinkedList<JMeterTestFile>();



	public JMeterClass(Element element){
		this.filename=element.attributeValue("name");
		this.isOn=Boolean.parseBoolean(element.attributeValue("ison"));

		List beforeClass=element.selectNodes("./test[@ison='true'and @level='before-class']");

		for (int i=0;i<beforeClass.size();i++) {
			Element elm = (Element) beforeClass.get(i);
			JMeterTestFile jMeterTestFile=new JMeterTestFile(elm);
			testList.add(jMeterTestFile);


		}

		List beforetests=element.selectNodes("./test[@ison='true'and @level='before-test']");
		List aftertest=element.selectNodes("./test[@ison='true'and @level='after-test']");

		List tests=element.selectNodes("./test[@ison='true'and @level='test']");

		for (int i=0;i<tests.size();i++) {
			Element elm = (Element) tests.get(i);


			for (int tmpi=0;tmpi<beforetests.size();tmpi++) {
				Element b_elm = (Element) beforetests.get(tmpi);
				JMeterTestFile jMeterTestFile = new JMeterTestFile(b_elm);
				testList.add(jMeterTestFile);
			}

			testList.add(new JMeterTestFile(elm));


			for(int tmpj=0;tmpj<aftertest.size();tmpj++) {
				Element a_elm = (Element) aftertest.get(tmpj);
				JMeterTestFile jMeterTestFile=new JMeterTestFile(a_elm);
				testList.add(jMeterTestFile);
			}
		}

		List afterclass=element.selectNodes("./test[@ison='true'and @level='after-class']");

		for (int i=0;i<afterclass.size();i++) {
			Element elm = (Element) afterclass.get(i);

			JMeterTestFile jMeterTestFile=new JMeterTestFile(elm);
			testList.add(jMeterTestFile);

		}
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

	public boolean getIsOn(){
		return this.isOn;
	}


	public List gettestList(){
		return this.testList;
	}
}
