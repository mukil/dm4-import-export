package net.abriraqui.dm4.importexport;

import de.deepamehta.core.osgi.PluginActivator;
import de.deepamehta.core.util.JavaUtils;
import de.deepamehta.core.util.DeepaMehtaUtils;
import de.deepamehta.core.service.PluginService;
import de.deepamehta.core.service.annotation.ConsumesService;
import de.deepamehta.core.Topic;
import de.deepamehta.core.Association;
import de.deepamehta.core.model.TopicModel;
import de.deepamehta.core.model.CompositeValueModel;
import de.deepamehta.core.model.AssociationModel;
import de.deepamehta.core.model.TopicRoleModel;

import de.deepamehta.plugins.topicmaps.service.TopicmapsService;
import de.deepamehta.plugins.topicmaps.model.TopicmapViewmodel;

import java.io.Writer;
import java.io.FileWriter;
import java.io.File;
import java.io.FileInputStream;

import java.util.logging.Logger;
import java.util.Map;
import java.util.HashMap;


import org.codehaus.jettison.json.JSONObject;
import org.codehaus.jettison.json.JSONArray;

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
    public void exportTopicmap(@CookieParam("dm4_topicmap_id") long topicmapId) {
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

    @POST
    @Path("/import")
    public void importTopicmap() {
	try {
	    File file = new File("topicmap-2751.json");
	    String json = JavaUtils.readTextFile(file);
	    log.info("JSON file to be imported #########" + json);

	    JSONObject topicmap = new JSONObject(json);
	    JSONObject info = topicmap.getJSONObject("info");

	    JSONArray assocsArray = topicmap.getJSONArray("assocs");
	    JSONArray topicsArray = topicmap.getJSONArray("topics");
	    
	    Topic importedTopicmap = topicmapsService.createTopicmap("importedTopicmap5","dm4.webclient.default_topicmap_renderer", null);
	    long topicmapId = importedTopicmap.getId();

	    Map<Long, Long> mapTopicIds = new HashMap();
	    
	    // Import topics
	    
	    for (int i = 0, size = topicsArray.length(); i < size; i++)
		{

		    JSONObject topic =  topicsArray.getJSONObject(i);
		    CompositeValueModel viewProps =new CompositeValueModel(topic.getJSONObject("view_props"));
		     TopicModel model = new TopicModel(topic);
		     Topic newTopic =  dms.createTopic(model, null);

		     long topicId = newTopic.getId();
		     long origTopicId = topic.getLong("id");
		     mapTopicIds.put(origTopicId, topicId);
		     topicmapsService.addTopicToTopicmap(topicmapId, topicId, viewProps);
		}
		
	    // Import associations
	    
	    for (int i=0, size = assocsArray.length(); i< size; i++)
		{		    
		    JSONObject assoc =  assocsArray.getJSONObject(i);
		    String uri = assoc.getString("uri");
		    String typeUri = assoc.getString("type_uri");
		    String value = assoc.getString("value");
		    JSONObject  role1 = assoc.getJSONObject("role_1");
		    JSONObject  role2 = assoc.getJSONObject("role_2");
		    Long role1_origTopicId = role1.getLong("topic_id");
		    Long role2_origTopicId = role2.getLong("topic_id");
		    Long role1_newTopicId = mapTopicIds.get(role1_origTopicId);
		    Long role2_newTopicId = mapTopicIds.get(role2_origTopicId);
		    String role1_roleTypeUri = role1.getString("role_type_uri");
		    String role2_roleTypeUri = role2.getString("role_type_uri");
 		    CompositeValueModel composite =new CompositeValueModel(assoc.getJSONObject("composite"));

		    AssociationModel assocModel = new AssociationModel(typeUri,
								  new TopicRoleModel(role1_newTopicId, role1_roleTypeUri),
								  new TopicRoleModel(role2_newTopicId, role2_roleTypeUri),
								  composite
								  );

   		    Association newAssociation = dms.createAssociation(assocModel, null);
   		    long assocId = newAssociation.getId();
		    topicmapsService.addAssociationToTopicmap(topicmapId, assocId);		 
		}

	} catch (Exception e) {
	    throw new RuntimeException("Import failed", e);
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