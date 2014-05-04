package net.abriraqui.dm4.importexport;

import de.deepamehta.core.osgi.PluginActivator;

import de.deepamehta.core.service.PluginService;
import de.deepamehta.core.service.annotation.ConsumesService;

import de.deepamehta.plugins.topicmaps.service.TopicmapsService;
import de.deepamehta.plugins.topicmaps.model.TopicmapViewmodel;

import java.io.Writer;
import java.io.FileWriter;
import java.util.logging.Logger;

import org.codehaus.jettison.json.JSONObject;

import javax.ws.rs.POST;

import javax.ws.rs.Path;
import javax.ws.rs.CookieParam;

@Path("/import-export")
public class ImportExportPlugin extends PluginActivator {
    

    private TopicmapsService topicmapsService;

    private Logger log = Logger.getLogger(getClass().getName());

    // Service implementation //

    @POST
    @Path("/export")
    public void export(@CookieParam("dm4_topicmap_id") long topicmapId) {
	try {
	    log.info("Exporting topicmap #########" + topicmapId);
	    TopicmapViewmodel topicmap = topicmapsService.getTopicmap(topicmapId);
	    Writer writer = new FileWriter("topicmap-" + topicmapId + ".json");
	    JSONObject json = topicmap.toJSON();
	    json.write(writer);
	    writer.close();
	} catch (Exception e) {
	    throw new RuntimeException("Export failed", e );
	}
    }







    // Hook implementation //
    
    @Override
    @ConsumesService("de.deepamehta.plugins.topicmaps.service.TopicmapsService")
    public void serviceArrived(PluginService service) {
	topicmapsService = (TopicmapsService) service;
    }

    @Override
    public void serviceGone(PluginService service) {
	topicmapsService = null;
    }


}