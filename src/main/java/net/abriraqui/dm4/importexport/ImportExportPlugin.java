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
import de.deepamehta.core.model.RoleModel;
import de.deepamehta.core.model.SimpleValue;
import de.deepamehta.core.storage.spi.DeepaMehtaTransaction;

import de.deepamehta.plugins.topicmaps.service.TopicmapsService;
import de.deepamehta.plugins.topicmaps.model.TopicmapViewmodel;
import de.deepamehta.plugins.files.UploadedFile;

import java.io.Writer;
import java.io.FileWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import java.util.logging.Logger;
import java.util.Map;
import java.util.HashMap;


import org.codehaus.jettison.json.JSONObject;
import org.codehaus.jettison.json.JSONArray;

import javax.ws.rs.POST;

import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.Consumes;
import javax.ws.rs.CookieParam;

@Path("/import-export")
@Produces("application/json")
public class ImportExportPlugin extends PluginActivator {
    
    private TopicmapsService topicmapsService;

    private Logger log = Logger.getLogger(getClass().getName());

    // Service implementation //

    @POST
    @Path("/export")
    public void exportTopicmap(@CookieParam("dm4_topicmap_id") long topicmapId) {
	try {
	    log.info("Exporting topicmap #########" + topicmapId);
	    TopicmapViewmodel topicmap = topicmapsService.getTopicmap(topicmapId, true);
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
    @Consumes("multipart/form-data")
    public ImportedTopicmap importTopicmap(UploadedFile file) {

	try {

	    String json = file.getString();

	    JSONObject topicmap = new JSONObject(json);
	    JSONObject info = topicmap.getJSONObject("info");

	    JSONArray assocsArray = topicmap.getJSONArray("assocs");
	    JSONArray topicsArray = topicmap.getJSONArray("topics");
	  
	    String origTopicmapName = info.getString("value");
	    Topic importedTopicmap = topicmapsService.createTopicmap("Imported Topicmap: "+ origTopicmapName,"dm4.webclient.default_topicmap_renderer", null);

	    long topicmapId = importedTopicmap.getId();
	    log.info("###### importedTopicapId " + topicmapId);

	    Map<Long, Long> mapTopicIds = new HashMap();
	    
	    // Import topics
	    
	    for (int i = 0, size = topicsArray.length(); i < size; i++)
		{
		    JSONObject topic =  topicsArray.getJSONObject(i);
		    TopicModel model = new TopicModel(topic);
		    CompositeValueModel viewProps =new CompositeValueModel(topic.getJSONObject("view_props")); 
	
		    Topic newTopic =  dms.createTopic(model, null);
	
		    long topicId = newTopic.getId();
		    long origTopicId = topic.getLong("id");
		    mapTopicIds.put(origTopicId, topicId);
		    topicmapsService.addTopicToTopicmap(topicmapId, topicId, viewProps);
		    
		}
		
	    // Import associations
	    
	    for (int i=0, size = assocsArray.length(); i< size; i++)
		{		    

		    AssociationModel assocModel = new AssociationModel(assocsArray.getJSONObject(i));

		    RoleModel role1 = assocModel.getRoleModel1();
		    role1.setPlayerId(mapTopicIds.get(role1.getPlayerId()));
		    RoleModel role2 = assocModel.getRoleModel2();
		    role2.setPlayerId(mapTopicIds.get(role2.getPlayerId()));

   		    Association newAssociation = dms.createAssociation(assocModel, null);
   		    long assocId = newAssociation.getId();
		    topicmapsService.addAssociationToTopicmap(topicmapId, assocId);		 
		    
		}
	    
	    return new ImportedTopicmap(topicmapId);

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