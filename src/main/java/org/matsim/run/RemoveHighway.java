package org.matsim.run;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.network.NetworkUtils;

public class RemoveHighway {

	public static void main(String[] args) {

		// use a utils class to load the file
		var network = NetworkUtils.readNetwork("/Users/janek/Downloads/kelheim-v3.0-1pct.output_network.xml.gz");

		// then change something
		for (Link link : network.getLinks().values()) {
			if (link.getId().equals(Id.createLinkId("322183374")) || link.getId().equals(Id.createLinkId("322186089"))){
				link.setFreespeed(10.);
			}
		}

		// use a utils class to write the result into a file
		NetworkUtils.writeNetwork(network, "/Users/janek/Downloads/output-for-the-class.xml.gz");
	}
}
