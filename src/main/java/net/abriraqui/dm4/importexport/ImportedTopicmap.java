package net.abriraqui.dm4.importexport;

import de.deepamehta.core.JSONEnabled; 
import org.codehaus.jettison.json.JSONObject;

class ImportedTopicmap implements JSONEnabled {
    private JSONObject json = new JSONObject();

	ImportedTopicmap(long topicmapId) {
	    try {
		json.put("topicmap_id", topicmapId);
	    } catch (Exception e) {
		throw new RuntimeException("Constructing an ImportedTopicmap failed", e);
	    }
	}

    public JSONObject toJSON() {
	    return json;
	}
}