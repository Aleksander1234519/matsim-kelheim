package org.matsim.analysis.vtts;

import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.vsp.scenario.SnzActivities;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.scenario.ScenarioUtils;

public class RunVTTSAnalysis {
	public static void main(String[] args) {
		Config kelheimConfig = ConfigUtils.loadConfig("output/vtts/kelheim-v3.0-config.xml");
		SnzActivities.addScoringParams(kelheimConfig);
		Scenario kelheimScenario = ScenarioUtils.loadScenario(kelheimConfig);

		VTTSHandler handler = new VTTSHandler(kelheimScenario, new String[]{}, "interaction");
		EventsManager manager = EventsUtils.createEventsManager();
		manager.addHandler(handler);

		EventsUtils.readEvents(manager, "output/vtts/kelheim-v3.0-1pct.output_events.xml.gz");

		handler.printVTTS("output/vtts/testPrintVTTS");
		handler.computeFinalVTTS();
	}
}
