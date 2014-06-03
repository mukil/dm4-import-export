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
import de.deepamehta.plugins.topicmaps.model.TopicViewmodel;
import de.deepamehta.plugins.topicmaps.model.AssociationViewmodel;

import de.deepamehta.plugins.files.service.FilesService;
import de.deepamehta.plugins.files.UploadedFile;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.transform.OutputKeys;


import java.io.Writer;
import java.io.FileWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.ByteArrayInputStream;

import java.util.logging.Logger;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

import org.codehaus.jettison.json.JSONObject;
import org.codehaus.jettison.json.JSONArray;

import javax.ws.rs.POST;

import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.Consumes;
import javax.ws.rs.CookieParam;
import javax.ws.rs.core.MediaType;

@Path("/import-export")
@Produces("application/json")
public class ImportExportPlugin extends PluginActivator {
    
    private TopicmapsService topicmapsService;
    private FilesService filesService;

    private Logger log = Logger.getLogger(getClass().getName());

    // Service implementation //

    @POST
    @Path("/export/json")
    public Topic exportTopicmapToJSON(@CookieParam("dm4_topicmap_id") long topicmapId) {

	try {
	    log.info("Exporting topicmap #########" + topicmapId);
	    TopicmapViewmodel topicmap = topicmapsService.getTopicmap(topicmapId, true);
	    String json = topicmap.toJSON().toString();
	    InputStream in = new ByteArrayInputStream(json.getBytes("UTF-8"));
	    Topic createdFile = filesService.createFile(in, "/topicmap-" + topicmapId + ".txt");
	    return createdFile;
	} catch (Exception e) {
	    throw new RuntimeException("Export failed", e );
	} 
    }



    @POST
    @Path("/export/svg")
    public void exportTopicmapToSVG(@CookieParam("dm4_topicmap_id") long topicmapId)  throws XMLStreamException {
	try {
	    log.info("Exporting topicmap #########" + topicmapId);
	    TopicmapViewmodel topicmap = topicmapsService.getTopicmap(topicmapId, true);
	    Iterable<TopicViewmodel> topics =topicmap.getTopics();
            Iterable<AssociationViewmodel> associations = topicmap.getAssociations();

	    String SVGfileName = "ExportedTopicamap-" + topicmapId +".svg";
	    XMLOutputFactory xof = XMLOutputFactory.newInstance();
	    XMLStreamWriter svgWriter = null;
	    svgWriter = xof.createXMLStreamWriter(new FileWriter(SVGfileName));

	    svgWriter.writeStartDocument();
	    svgWriter.writeDTD("<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 20000802//EN\" " 
			    + "\"http://www.w3.org/TR/2000/CR-SVG-20000802/DTD/svg-20000802.dtd\">");
	    svgWriter.writeStartElement("svg");
	    svgWriter.writeAttribute("width", "1200");
	    svgWriter.writeAttribute("height", "1200");
	    svgWriter.writeAttribute("xmlns","http://www.w3.org/2000/svg");

            for (TopicViewmodel topic : topics) {
		JSONObject topicSVG =  topic.toJSON();
		CompositeValueModel viewProps =new CompositeValueModel(topicSVG.getJSONObject("view_props")); 
		String value= topic.getSimpleValue().toString();
		int x = viewProps.getInt("dm4.topicmaps.x");
		int y = viewProps.getInt("dm4.topicmaps.y");
		int valueWidth = value.length()*9;
		boolean visibility = viewProps.getBoolean("dm4.topicmaps.visibility");
		if (!visibility) { continue ;}
		svgWriter.writeEmptyElement("rect");
		svgWriter.writeAttribute("x", Integer.toString(x));
		svgWriter.writeAttribute("y", Integer.toString(y));
		svgWriter.writeAttribute("width", Integer.toString(valueWidth));
		svgWriter.writeAttribute("height", "20");


		svgWriter.writeAttribute("fill", color(topic.getTypeUri()));   

		svgWriter.writeStartElement("text");
		svgWriter.writeAttribute("x", Integer.toString(x+5));
		svgWriter.writeAttribute("y", Integer.toString(y+14));
		svgWriter.writeCharacters(value);
		svgWriter.writeEndElement();

	    }
	    
	    for (AssociationViewmodel association : associations) {
		
		RoleModel role1 = association.getRoleModel1();

		/*
		svgWriter.writeEmptyElement("line");
		svgWriter.writeAttribute("x1", x1);
		svgWriter.writeAttribute("x2", x1);
		svgWriter.writeAttribute("y1",  y1);
		svgWriter.writeAttribute("y2",  y2);
		svgWriter.writeAttribute("stroke", "lightgray");
		svgWriter.writeAttribute("stroke-width", "2");
		*/
	    }

	    svgWriter.writeEndDocument(); // closes svg element
	    svgWriter.flush();
	    svgWriter.close();
	    /*
    	    Topic createdFile = filesService.createFile(in, "/topicmap-" + topicmapId + ".txt");
	    return createdFile;
	    */

	} catch (Exception e) {
	    throw new RuntimeException("Export failed", e );
	} 
    }



    @POST
    @Path("/import")
    @Consumes("multipart/form-data")
    public Topic importTopicmap(UploadedFile file) {

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
	    
	    for (int i = 0, size = topicsArray.length(); i < size; i++)	{
		JSONObject topic =  topicsArray.getJSONObject(i);
		TopicModel model = new TopicModel(topic);
		CompositeValueModel viewProps =new CompositeValueModel(topic.getJSONObject("view_props")); 
		long origTopicId = model.getId();
		
		Topic newTopic =  dms.createTopic(model, null);
		long topicId = newTopic.getId();
		
		mapTopicIds.put(origTopicId, topicId);
		topicmapsService.addTopicToTopicmap(topicmapId, topicId, viewProps);
		    
	    }
		
	    // Import associations
	    
	    for (int i=0, size = assocsArray.length(); i< size; i++) {		    

		AssociationModel assocModel = new AssociationModel(assocsArray.getJSONObject(i));
		RoleModel role1 = assocModel.getRoleModel1();
		role1.setPlayerId(mapTopicIds.get(role1.getPlayerId()));
		RoleModel role2 = assocModel.getRoleModel2();
		role2.setPlayerId(mapTopicIds.get(role2.getPlayerId()));
		
		Association newAssociation = dms.createAssociation(assocModel, null);
		long assocId = newAssociation.getId();
		topicmapsService.addAssociationToTopicmap(topicmapId, assocId);		 
		
	    }
	    
	    return importedTopicmap;

	} catch (Exception e) {
	    throw new RuntimeException("Import failed", e);
	} 
    }


    // Hook implementation //
    
    @Override
    @ConsumesService({
	    "de.deepamehta.plugins.topicmaps.service.TopicmapsService",
	    "de.deepamehta.plugins.files.service.FilesService"      
    })

    public void serviceArrived(PluginService service) {
	if (service instanceof TopicmapsService) {
            topicmapsService = (TopicmapsService) service;
        } else if (service instanceof FilesService) {
            filesService = (FilesService) service;
        }
    }
   
    @Override
    public void serviceGone(PluginService service) {
	if (service == topicmapsService) {
	    topicmapsService = null;
        } else if (service == filesService) {
	    filesService = null;
        }
    }


    private String color(String typeUri) {
	if (typeUri.equals("dm4.contacts.institution")) {
	    return "red";
	} else if (typeUri.equals("dm4.contacts.person")) {
	    return "blue";
	} else if (typeUri.equals("dm4.notes.note")) {
	    return "yellow";
	} else {
	    return "lightblue";
	}
    }
}