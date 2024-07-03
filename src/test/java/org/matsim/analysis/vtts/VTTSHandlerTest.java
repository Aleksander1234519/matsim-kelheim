package org.matsim.analysis.vtts;

import org.apache.commons.io.FileUtils;
import org.junit.*;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.contrib.vsp.scenario.SnzActivities;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.testcases.MatsimTestUtils;

import java.io.File;
import java.io.IOException;


public class VTTSHandlerTest{

	@Rule
	public MatsimTestUtils utils = new MatsimTestUtils();
	static VTTSHandler static_handler;
	static boolean isInitialized = false;

	VTTSHandler handler;

	@Before
	public void initialize(){
		if(VTTSHandlerTest.isInitialized){
			handler = VTTSHandlerTest.static_handler;
			return;
		}

		Config kelheimConfig = ConfigUtils.loadConfig(utils.getPackageInputDirectory() + "kelheim-v3.0-config.xml");
		SnzActivities.addScoringParams(kelheimConfig);
		Scenario kelheimScenario = ScenarioUtils.loadScenario(kelheimConfig);

		handler = new VTTSHandler(kelheimScenario, new String[]{}, "interaction");
		EventsManager manager = EventsUtils.createEventsManager();
		manager.addHandler(handler);

		EventsUtils.readEvents(manager, utils.getPackageInputDirectory() + "kelheim-v3.0-1pct.output_events.xml.gz");

		VTTSHandlerTest.static_handler = handler;
		VTTSHandlerTest.isInitialized = true;
	}

	@Test
	public void testPrintVTTS() throws IOException {
		handler.printVTTS(utils.getOutputDirectory() + "testPrintVTTS");

		Assert.assertEquals("Test of printVTTS() failed: Output does not match the reference-file!",
			FileUtils.readFileToString(new File(utils.getPackageInputDirectory() + "testPrintVTTS"), "utf-8"),
			FileUtils.readFileToString(new File(utils.getOutputDirectory() + "testPrintVTTS"), "utf-8"));
	}

	@Test
	public void testPrintCarVTTS() throws IOException {
		handler.printCarVTTS(utils.getOutputDirectory() + "testPrintCarVTTS");

		Assert.assertEquals("Test of printCarVTTS() failed: Output does not match the reference-file!",
			FileUtils.readFileToString(new File(utils.getPackageInputDirectory() + "testPrintCarVTTS"), "utf-8"),
			FileUtils.readFileToString(new File(utils.getOutputDirectory() + "testPrintCarVTTS"), "utf-8"));
	}

	@Test
	public void testPrintVTTSMode() throws IOException {
		handler.printVTTS(utils.getOutputDirectory() + "testPrintVTTSMode1", TransportMode.pt);

		Assert.assertEquals("Test of printCarVTTS() failed: Output does not match the reference-file!",
			FileUtils.readFileToString(new File(utils.getPackageInputDirectory() + "testPrintVTTSMode1"), "utf-8"),
			FileUtils.readFileToString(new File(utils.getOutputDirectory() + "testPrintVTTSMode1"), "utf-8"));

		handler.printVTTS(utils.getOutputDirectory() + "testPrintVTTSMode2", TransportMode.bike);

		Assert.assertEquals("Test of printCarVTTS() failed: Output does not match the reference-file!",
			FileUtils.readFileToString(new File(utils.getPackageInputDirectory() + "testPrintVTTSMode2"), "utf-8"),
			FileUtils.readFileToString(new File(utils.getOutputDirectory() + "testPrintVTTSMode2"), "utf-8"));
	}

	@Test
	public void testPrintAvgVTTSperPerson() throws IOException {
		handler.printAvgVTTSperPerson(utils.getOutputDirectory() + "testPrintAvgVTTSperPerson");

		Assert.assertEquals("Test of printCarVTTS() failed: Output does not match the reference-file!",
			FileUtils.readFileToString(new File(utils.getPackageInputDirectory() + "testPrintAvgVTTSperPerson"), "utf-8"),
			FileUtils.readFileToString(new File(utils.getOutputDirectory() + "testPrintAvgVTTSperPerson"), "utf-8"));
	}

	@Test
	public void testPrintVTTSstatistics() throws IOException {
		handler.printVTTSstatistics(utils.getOutputDirectory() + "testPrintVTTSstatistics1", TransportMode.bike, new Tuple<>(50.0, 623.4));

		Assert.assertEquals("Test of printCarVTTS() failed: Output does not match the reference-file!",
			FileUtils.readFileToString(new File(utils.getPackageInputDirectory() + "testPrintVTTSstatistics1"), "utf-8"),
			FileUtils.readFileToString(new File(utils.getOutputDirectory() + "testPrintVTTSstatistics1"), "utf-8"));

		handler.printVTTSstatistics(utils.getOutputDirectory() + "testPrintVTTSstatistics2", TransportMode.bike, new Tuple<>(90.0, 110.0));

		Assert.assertEquals("Test of printCarVTTS() failed: Output does not match the reference-file!",
			FileUtils.readFileToString(new File(utils.getPackageInputDirectory() + "testPrintVTTSstatistics2"), "utf-8"),
			FileUtils.readFileToString(new File(utils.getOutputDirectory() + "testPrintVTTSstatistics2"), "utf-8"));

		handler.printVTTSstatistics(utils.getOutputDirectory() + "testPrintVTTSstatistics3", TransportMode.car, new Tuple<>(50.0, 623.4));

		Assert.assertEquals("Test of printCarVTTS() failed: Output does not match the reference-file!",
			FileUtils.readFileToString(new File(utils.getPackageInputDirectory() + "testPrintVTTSstatistics3"), "utf-8"),
			FileUtils.readFileToString(new File(utils.getOutputDirectory() + "testPrintVTTSstatistics3"), "utf-8"));
	}

	/*
	@Test
	public void testGetAvgVTTSh() {
	}

	@Test
	public void testTestGetAvgVTTSh() {
	}

	@Test
	public void testGetCarVTTS() {
	}
	 */
}
