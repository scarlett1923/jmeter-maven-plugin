package com.lazerycode.jmeter.model;

import org.dom4j.Element;

import java.util.LinkedList;
import java.util.List;

/**
 * Created by shenjiali01 on 2014/7/24.
 */
public class JMeterTestFileGroup {
	private String groupname;
	private boolean isOn;
	private List<JMeterTestFile> testList=new LinkedList<JMeterTestFile>();

	public JMeterTestFileGroup(Element element){
		this.groupname=element.attributeValue("name");
		this.isOn=Boolean.parseBoolean(element.attributeValue("ison"));

		List beforeGroup=element.selectNodes("./test[@level='before-group']");

		for (int i=0;i<beforeGroup.size();i++) {
			Element elm = (Element) beforeGroup.get(i);
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

		List afterGroup=element.selectNodes("./test[@ison='true'and @level='after-group']");

		for (int i=0;i<afterGroup.size();i++) {
			Element elm = (Element) afterGroup.get(i);

			JMeterTestFile jMeterTestFile=new JMeterTestFile(elm);
			testList.add(jMeterTestFile);

		}
	}

	public void setGroupname(String groupname) {
		this.groupname = groupname;
	}

	public void setOn(boolean isOn) {
		this.isOn = isOn;
	}

	public void settestList(List<JMeterTestFile> testList) {
		this.testList = testList;
	}

	public List gettestList(){
		return this.testList;
	}
}
