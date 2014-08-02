package com.lazerycode.jmeter.testrunner;

import com.lazerycode.jmeter.JMeterMojo;
import com.lazerycode.jmeter.UtilityFunctions;
import com.lazerycode.jmeter.configuration.*;
import com.lazerycode.jmeter.model.JMeterClass;
import com.lazerycode.jmeter.model.JMeterTestFile;
import com.lazerycode.jmeter.model.FailureAnalyse;
import com.sun.net.httpserver.Authenticator;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.tools.ant.DirectoryScanner;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;

import java.io.*;
import java.util.*;


/**
 * TestManager encapsulates functions that gather JMeter Test files and execute the tests
 */
public class TestManager extends JMeterMojo {

	private final JMeterArgumentsArray baseTestArgs;
	private final File binDir;
	private final File testFilesDirectory;
	private final File configFileDirectory;
	private final List<String> testFilesIncluded;
	private final List<String> testFilesExcluded;
	private final boolean suppressJMeterOutput;
	private final RemoteConfiguration remoteServerConfiguration;
	private final JMeterProcessJVMSettings jMeterProcessJVMSettings;
	private final int retryTimes;

	public TestManager(JMeterArgumentsArray baseTestArgs, File testFilesDirectory,File configFileDirectory, List<String> testFilesIncluded, List<String> testFilesExcluded, RemoteConfiguration remoteServerConfiguration, boolean suppressJMeterOutput, File binDir, JMeterProcessJVMSettings jMeterProcessJVMSettings,int retryTimes) {
		this.binDir = binDir;
		this.baseTestArgs = baseTestArgs;
		this.testFilesDirectory = testFilesDirectory;
		this.testFilesIncluded = testFilesIncluded;
		this.testFilesExcluded = testFilesExcluded;
		this.remoteServerConfiguration = remoteServerConfiguration;
		this.suppressJMeterOutput = suppressJMeterOutput;
		this.jMeterProcessJVMSettings = jMeterProcessJVMSettings;
		this.retryTimes=retryTimes;
		this.configFileDirectory=configFileDirectory;
	}

	/**
	 * Executes all tests and returns the resultFile names
	 *
	 * @return the list of resultFile names
	 * @throws MojoExecutionException
	 */
	public List<String> executeTests() throws MojoExecutionException {
		JMeterArgumentsArray thisTestArgs = baseTestArgs;
		FailureAnalyse failureScanner=new FailureAnalyse();

		//List<String> tests = generateTestList();
		List<String> tests = generateTests();

		List<String> results = new ArrayList<String>();
		for (String file : tests) {
			int retryNum=0;
			if (remoteServerConfiguration != null) {
				if ((remoteServerConfiguration.isStartServersBeforeTests() && tests.get(0).equals(file)) || remoteServerConfiguration.isStartAndStopServersForEachTest()) {
					thisTestArgs.setRemoteStart();
					thisTestArgs.setRemoteStartServerList(remoteServerConfiguration.getServerList());
				}
				if ((remoteServerConfiguration.isStopServersAfterTests() && tests.get(tests.size() - 1).equals(file)) || remoteServerConfiguration.isStartAndStopServersForEachTest()) {
					thisTestArgs.setRemoteStop();
				}
			}
			String resultFilePath=executeSingleTest(new File(testFilesDirectory, file), thisTestArgs);
			results.add(resultFilePath);
			/*

			analyse the result here and add the retry
			 */
			boolean isError=parseTestResult(resultFilePath);
			if(isError) {
				getLog().info(" ");
				getLog().info("this test fails, you set "+retryTimes+" retry times...");

				for (retryNum = 0; retryNum < retryTimes; retryNum++) {

					if (isError) {


						if (results.size() < 1) {
							getLog().info(" ");
							getLog().error("Executr test error...");
							getLog().info(" ");
						} else {
							results.remove(results.size() - 1);
						}
						resultFilePath = executeSingleTest(new File(testFilesDirectory, file), thisTestArgs);
						results.add(resultFilePath);
						isError=parseTestResult(resultFilePath);
						retryNum++;

						getLog().info("Now retry the " + retryNum+" time...");


					}
					else{
						break;
					}

				}
				getLog().info(" ");
				getLog().info("Had tried "+retryNum+" times...");
			}

		}
		return results;
	}

	//=============================================================================================

	/**
	 * Executes a single JMeter test by building up a list of command line
	 * parameters to pass to JMeter.start().
	 *
	 * @param test JMeter test XML
	 * @return the report file names.
	 * @throws org.apache.maven.plugin.MojoExecutionException
	 *          Exception
	 */
	@SuppressWarnings("ResultOfMethodCallIgnored")
	private String executeSingleTest(File test, JMeterArgumentsArray testArgs) throws MojoExecutionException {
		getLog().info(" ");
		testArgs.setTestFile(test);
		//Delete results file if it already exists
		new File(testArgs.getResultsLogFileName()).delete();
		List<String> argumentsArray = testArgs.buildArgumentsArray();
		argumentsArray.addAll(buildRemoteArgs(remoteServerConfiguration));
		getLog().debug("JMeter is called with the following command line arguments: " + UtilityFunctions.humanReadableCommandLineOutput(argumentsArray));
		getLog().info("Executing test: " + test.getName());
		//Start the test.
		JMeterProcessBuilder JMeterProcessBuilder = new JMeterProcessBuilder(jMeterProcessJVMSettings);
		JMeterProcessBuilder.setWorkingDirectory(binDir);
		JMeterProcessBuilder.addArguments(argumentsArray);
		try {
			final Process process = JMeterProcessBuilder.startProcess();
			//Log process output
			if (!suppressJMeterOutput) {
				BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
				String line;
				while ((line = br.readLine()) != null) {
					getLog().info(line);
				}
			}
			int jMeterExitCode = process.waitFor();
			if (jMeterExitCode != 0) {
				throw new MojoExecutionException("Test failed");
			}
			getLog().info("Completed Test: " + test.getName());

			/*

			analyse the result here and add the retry
			 */


		} catch (InterruptedException ex) {
			getLog().info(" ");
			getLog().info("System Exit Detected!  Stopping Test...");
			getLog().info(" ");
		} catch (IOException e) {
			getLog().error(e.getMessage());
		}
		return testArgs.getResultsLogFileName();
	}

	/**
	 * Scan the result jtl file
	 *
	 * @return TRUE:it is failture   FALSE: it is right
	 */
	boolean parseTestResult(String resultJtl)  {
		FailureAnalyse failureScanner = new FailureAnalyse();
		//int totalFailureCount = 0;
		boolean failed = false;
		getLog().info(" ");
		getLog().info("resultJtl:    "+resultJtl);
		getLog().info(" ");

			try {
				if (failureScanner.IsTestFailed(new File(resultJtl))) {
					//totalFailureCount += failureScanner.getFailureCount();
					failed = true;
				}
			} catch (IOException e) {
				getLog().info(" ");
				getLog().info("Analyse the jtl file IO error !");
				getLog().info(" ");
			}catch (DocumentException e) {
				getLog().info(" ");
				getLog().info("Analyse the jtl file dom4j error !");
				getLog().info(" ");
			}


		if(failed) {

			return true;
			//throw new MojoFailureException("There were " + totalFailureCount + " test failures.  See the JMeter logs at '" + logsDir.getAbsolutePath() + "' for details.");
		}
		else{
			getLog().info(" ");
			getLog().info("this test success !");

			getLog().info(" ");
			return false;


		}

	}
	private List<String> buildRemoteArgs(RemoteConfiguration remoteConfig) {
		if (remoteConfig == null) {
			return Collections.emptyList();
		}
		return new RemoteArgumentsArrayBuilder().buildRemoteArgumentsArray(remoteConfig.getMasterPropertiesMap());
	}

	/**
	 * Scan Project directories for JMeter Test Files according to includes and excludes
	 *
	 * @return found JMeter tests
	 */
	private List<String> generateTestList() {
		List<String> jmeterTestFiles = new ArrayList<String>();
		DirectoryScanner scanner = new DirectoryScanner();
		scanner.setBasedir(this.testFilesDirectory);
		scanner.setIncludes(this.testFilesIncluded == null ? new String[]{"**/*.jmx"} : this.testFilesIncluded.toArray(new String[jmeterTestFiles.size()]));
		if (this.testFilesExcluded != null) {
			scanner.setExcludes(this.testFilesExcluded.toArray(new String[testFilesExcluded.size()]));
		}
		scanner.scan();
		final List<String> includedFiles = Arrays.asList(scanner.getIncludedFiles());
		jmeterTestFiles.addAll(includedFiles);
		return jmeterTestFiles;
	}

	private List<String>  generateTestFileListsByScan() {
		List<String> jmeterTestFiles = new ArrayList<String>();
		DirectoryScanner scanner = new DirectoryScanner();
		scanner.setBasedir(this.testFilesDirectory);
		scanner.setIncludes(new String[]{"**/*.jmx","**/*.sql"} );
		if (this.testFilesExcluded != null) {
			scanner.setExcludes(this.testFilesExcluded.toArray(new String[testFilesExcluded.size()]));
		}

		scanner.scan();

		final List<String> includedFiles = Arrays.asList(scanner.getIncludedFiles());
		for(int i=0;i<scanner.getIncludedFilesCount();i++){
			String filename=includedFiles.get(i);

		//	getLog().info("");
		//	getLog().info(filename);
			int size=filename.length();
			int index=filename.lastIndexOf("\\");
			//getLog().info(""+size);
			//getLog().info(""+index);
			//getLog().info("");


			if(index>0){
				 filename=includedFiles.get(i).substring(index+1,size);
			}
			jmeterTestFiles.add(filename);
		}
		//jmeterTestFiles.addAll(includedFiles);



		return jmeterTestFiles;
	}

	private List<String>  generateTestPathListsByScan() {
		List<String> jmeterTestFiles = new ArrayList<String>();
		DirectoryScanner scanner = new DirectoryScanner();
		scanner.setBasedir(this.testFilesDirectory);
		scanner.setIncludes(new String[]{"**/*.jmx","**/*.sql"} );
		if (this.testFilesExcluded != null) {
			scanner.setExcludes(this.testFilesExcluded.toArray(new String[testFilesExcluded.size()]));
		}

		scanner.scan();

		final List<String> includedFiles = Arrays.asList(scanner.getIncludedFiles());

		jmeterTestFiles.addAll(includedFiles);



		return jmeterTestFiles;
	}

	public boolean generateConfigSample(List<String> testFileList){
		Document newConfigXML = DocumentHelper.createDocument();  //创建文档
		//add the root element
		Element root=newConfigXML.addElement("suite");
		root.addComment("define the runtype attribute by \"suite\" or \"group\"");
		root.addComment("define the before-suite & after-suit level test case here,or it won't be run");
		root.addAttribute("name","suite");
		root.addAttribute("runtype","suite");
		root.addComment("organize the test case in serval classes, each test case must be set a class except the before-suite & after-suite");
		Element classElm=root.addElement("class");
		classElm.addAttribute("name","default");
		classElm.addAttribute("ison","true");
		classElm.addComment("define a test case here");
		classElm.addComment("name should be the testcase filename");
		classElm.addComment("ison control that if the test case should be run");
		classElm.addComment("levle define the case attribute,it can be \"test\",\"before-suite\",\"before-class\",\"before-test\" ");


		for(int i=0;i<testFileList.size();i++){
			Element testElm= classElm.addElement("test");
			testElm.addAttribute("name",testFileList.get(i));
			testElm.addAttribute("ison","true");
			testElm.addAttribute("level","test");
		}




		try {
			OutputFormat format = OutputFormat.createPrettyPrint();
			Writer fileWriter=new FileWriter("config-sample.xml");
			format.setEncoding("UTF-8");//设置编码
			XMLWriter xmlWriter=new XMLWriter(fileWriter,format);
			xmlWriter.write(newConfigXML);   //写入文件中
			xmlWriter.close();
			return true;
		} catch (IOException e) {
			System.out.println(e.getMessage());
			return false;
		}
	}

	private String TestFileByScan(String testfile) {
		//getLog().info(" generateTestList called____________________________"+testfile);
		String jmeterTestFile=null ;
		DirectoryScanner scanner = new DirectoryScanner();
		scanner.setBasedir(this.testFilesDirectory);
		scanner.setIncludes(new String[]{"**/"+testfile});

		if (this.testFilesExcluded != null) {
			scanner.setExcludes(this.testFilesExcluded.toArray(new String[testFilesExcluded.size()]));
		}
		scanner.scan();
		final List<String> includedFiles = Arrays.asList(scanner.getIncludedFiles());
		if(includedFiles.size()>0) {
			jmeterTestFile = includedFiles.get(0);
		}
		getLog().info("jmeterTestFile:     "+jmeterTestFile);
		return jmeterTestFile;
	}

	private List<String> generateTests() {
		getLog().info(" ");
		getLog().info("-------------------------------------------------------");
		getLog().info("Start to read the config.xml");
		getLog().info("-------------------------------------------------------");
		getLog().info(" ");
		List<String> jmeterTestFiles = new ArrayList<String>();
		DirectoryScanner scannerConfig = new DirectoryScanner();
		String configFilePath = null;

		scannerConfig.setBasedir(this.configFileDirectory);
		scannerConfig.setIncludes(new String[]{"config.xml"});
		scannerConfig.scan();
		if (scannerConfig.getIncludedFilesCount() == 0) {
			getLog().info(" ");
			getLog().info("-------------------------------------------------------");
			getLog().info("Can't find the config.xml file , start to generate the config-sample.xml");
			getLog().info("you can modify the config-sample.cml and rename to config.xml");
			getLog().info("-------------------------------------------------------");
			getLog().info(" ");
			List<String> jmeterTestPaths ;
			jmeterTestPaths=generateTestFileListsByScan();

			jmeterTestFiles=generateTestPathListsByScan();
			if(generateConfigSample(jmeterTestPaths)){
				getLog().info(" ");

				getLog().info("generate the config-sample.xml successfully");

				getLog().info(" ");


			}else
			{
				getLog().info(" ");

				getLog().error("can't generate the config-sample.xml !");

				getLog().info(" ");
			}

		} else if (scannerConfig.getIncludedFilesCount() > 0) {

			List<String> configFiles = Arrays.asList(scannerConfig.getIncludedFiles());
			configFilePath = configFiles.get(0);
			getLog().info(" ");
			getLog().info("-------------------------------------------------------");
			getLog().info("analyse the config.xml");

			getLog().info("configFilespath:"+configFilePath);

			getLog().info("-------------------------------------------------------");
			getLog().info(" ");
			File configFile = new File(this.configFileDirectory, configFilePath);
			jmeterTestFiles=generateTestFileListByConfig(configFile);

		}
		//getLog().info("scannerConfig.getIncludedFilesCount():"+scannerConfig.getIncludedFilesCount());
		//getLog().info("scannerConfig:"+jmeterTestFiles.get(0));
		return jmeterTestFiles;

	}


	public List generateTestFileListByConfig(File config){
		List<JMeterTestFile> sortfiles=new LinkedList<JMeterTestFile>();
		List<String> jmeterFileList=new LinkedList<String>();
		//getLog().info(" generateTestFileListByConfig called____________________________");
		SAXReader reader = new SAXReader();
		try {
			Document document = reader.read(new BufferedReader(new InputStreamReader(new FileInputStream(config), "gb2312")));
			Element rootElm = document.getRootElement();
			String runtype = rootElm.attributeValue("runtype");

			if (runtype.equals("suite")) {

				List<Element> beforeSuite = rootElm.selectNodes("./test[@ison='true' and @level='before-suite']");

				for (int i = 0; i < beforeSuite.size(); i++) {
					Element elm = (Element) beforeSuite.get(i);
					sortfiles.add(new JMeterTestFile(elm));

				}
				List classes = rootElm.selectNodes("./class[@ison='true']");

				for (int i = 0; i < classes.size(); i++) {
					Element elm = (Element) classes.get(i);
					JMeterClass classElm = new JMeterClass(elm);
					List<JMeterTestFile> testnames = classElm.gettestList();
					if (testnames != null) {
						for (int j = 0; j < testnames.size(); j++) {
							sortfiles.add(testnames.get(j));
						}
					}

				}


				List<Element> afterSuite = rootElm.selectNodes("./test[@ison='true' and @level='after-suite']");

				for (int i = 0; i < afterSuite.size(); i++) {
					Element elm = (Element) afterSuite.get(i);
					sortfiles.add(new JMeterTestFile(elm));

				}
				for (int i=0;i<sortfiles.size();i++) {

					String testFileByScan=TestFileByScan(sortfiles.get(i).getFilename());
					if(testFileByScan==null) {
					//	getLog().debug("null");
						getLog().error("The case " + sortfiles.get(i).getFilename() + " can't be find in the specified path,please check the config.xml");
					}else {

						jmeterFileList.add(testFileByScan);
					}

				}
			//	getLog().debug(" ");
			//	getLog().debug("jmeterFileList.size():"+jmeterFileList.size());
			//	getLog().debug(" ");


			}
		}catch(Exception e){
			getLog().info("read config.xml fail error! " );
		}
		return jmeterFileList;
	}
}
