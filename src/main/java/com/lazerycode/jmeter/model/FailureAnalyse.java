package com.lazerycode.jmeter.model;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import java.io.*;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Pattern;

/**
 * Handles checking a JMeter results file in XML format for errors and failures.
 *
 * @author Jon Roberts
 */
public class FailureAnalyse {

	private static final String REQUEST_FAILURE_PATTERN = "s=\"false\"";
	private int failureCount;

	public FailureAnalyse() {

	}

	/**
	 * Check given file for errors
	 *
	 * @param file File to parse for failures
	 * @return true if file doesn't contain failures
	 * @throws java.io.IOException
	 */
	public boolean hasTestFailed(File file) throws IOException {

		failureCount = 0;
		Scanner resultFileScanner;
		Pattern errorPattern = Pattern.compile(REQUEST_FAILURE_PATTERN);
		resultFileScanner = new Scanner(file);
		while (resultFileScanner.findWithinHorizon(errorPattern, 0) != null) {
			failureCount++;
		}
		resultFileScanner.close();

		return this.failureCount > 0;

	}
	public boolean IsTestFailed(File file) throws IOException,DocumentException {

		SAXReader reader = new SAXReader();
		Document document = reader.read(new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8")));
		Element rootElm = document.getRootElement();
		List<Element> result = rootElm.selectNodes("//httpSample[@s='false']");

		if(result.size()>0){
		     return true;
		}else
		{
			return false;
		}

	}

	/**
	 * @return failureCount
	 */
	public int getFailureCount() {
		return this.failureCount;
	}
}
