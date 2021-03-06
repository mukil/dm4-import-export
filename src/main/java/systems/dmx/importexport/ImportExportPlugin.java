package systems.dmx.importexport;

import com.sun.jersey.core.util.Base64;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import javax.xml.stream.XMLStreamException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.File;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import systems.dmx.core.Assoc;
import static systems.dmx.core.Constants.ASSOCIATION;
import static systems.dmx.core.Constants.CHILD;
import static systems.dmx.core.Constants.COMPOSITION;
import static systems.dmx.core.Constants.DEFAULT;
import static systems.dmx.core.Constants.PARENT;
import systems.dmx.core.RelatedTopic;
import systems.dmx.core.Topic;
import systems.dmx.core.TopicType;
import systems.dmx.core.model.AssocModel;
import systems.dmx.core.model.ChildTopicsModel;
import systems.dmx.core.model.PlayerModel;
import systems.dmx.core.model.SimpleValue;
import systems.dmx.core.model.TopicModel;
import systems.dmx.core.model.topicmaps.ViewAssoc;
import systems.dmx.core.model.topicmaps.ViewProps;
import systems.dmx.core.model.topicmaps.ViewTopic;
import systems.dmx.core.osgi.PluginActivator;
import systems.dmx.core.service.Inject;
import systems.dmx.core.service.Transactional;
import systems.dmx.core.service.accesscontrol.SharingMode;
import systems.dmx.core.util.DMXUtils;
import systems.dmx.files.FilesService;
import systems.dmx.files.StoredFile;
import systems.dmx.files.UploadedFile;
import systems.dmx.accesscontrol.AccessControlService;
import static systems.dmx.core.Constants.ASSOC_TYPE;
import static systems.dmx.core.Constants.TOPIC_TYPE;
import static systems.dmx.topicmaps.Constants.TOPICMAP;
import systems.dmx.topicmaps.Topicmap;
import systems.dmx.topicmaps.TopicmapsService;
import static systems.dmx.workspaces.Constants.WORKSPACE;
import systems.dmx.workspaces.WorkspacesService;

@Path("/import-export")
@Produces("application/json")
public class ImportExportPlugin extends PluginActivator {

    @Inject
    private TopicmapsService topicmaps;
    @Inject
    private AccessControlService acl;
    @Inject
    private FilesService files;
    @Inject
    private WorkspacesService workspaces;

    private Logger log = Logger.getLogger(getClass().getName());

    private Hashtable<Long, Long> topicIds = new Hashtable();
    private Hashtable<Long, Long> assocIds = new Hashtable();

    // Keep generic importer fail-state
    private List<JSONObject> lostTopics = new ArrayList<>();

    private static final String DMX_TIME_CREATED = "dmx.timestamps.created";
    private static final String DMX_TIME_MODIFIED = "dmx.timestamps.modified";
    
    private static final String SYMBOLIC_BUNDLE_URI = "systems.dmx.import-export";

    // Zotero Report Column Header Names seem to be internationalized...
    private static final String ZOTERO_ENTRY_TYPE_COLUMN_KEY = "Typ";
    private static final String ZOTERO_ENTRY_URL_COLUMN_KEY = "URL";
    private static final String ZOTERO_MODIFIED_AT_COLUMN_KEY = "Geändert am";
    private static final String ZOTERO_ADDED_AT_COLUMN_KEY = "Hinzugefügt am";

    // Service implementation //

    @GET
    @Transactional
    @Path("/configured/content")
    public Topic exportConfiguredTopicTypes() {
        String jsonFileName = "dmx-import-export-configured-"+new Date().toString()+".txt";
        InputStream in = null;
        try {
            log.info("######## Start exporting topics of all configured topic and assoc types to JSON ######### ");
            JSONObject json = new JSONObject();
            // 1) fetch all workspace topics
            addWorkspaceTopicsToExport(json);
            // 2) fetch configured assoc and topic types to be included in export
            List<RelatedTopic> configuredTypes = getConfiguredTopicTypesForExport();
            List<RelatedTopic> configuredAssocTypes = getConfiguredAssocTypesForExport();
            // 3) export topics and associations of configured topic types
            json.put("topics", new JSONArray());
            for (RelatedTopic type : configuredTypes) {
                exportTopicsAndAssocsToJSON(type.getUri(), configuredAssocTypes, json);
            }
            // 4) export topicmaps topics with their current workspace assignment
            List<Topic> topicmaps = dmx.getTopicsByType(TOPICMAP);
            json.put("topicmaps", new JSONArray());
            for (Topic topicmap : topicmaps) {
                log.info("### Exporting topicmap \"" + topicmap.getSimpleValue() + "\" ...");
                exportTopicmapToJSON(topicmap, json);
            }
            in = new ByteArrayInputStream(json.toString().getBytes("UTF-8"));
        } catch (JSONException ex) {
            Logger.getLogger(ImportExportPlugin.class.getName()).log(Level.SEVERE, null, ex);
        } catch (UnsupportedEncodingException ex) {
            Logger.getLogger(ImportExportPlugin.class.getName()).log(Level.SEVERE, null, ex);
        }
        // 5) Write export file
        return files.createFile(in, files.pathPrefix() + "/" + jsonFileName);
    }

    private void addWorkspaceTopicsToExport(JSONObject json) {
        try {
            json.put("workspaces", new JSONArray());
            Iterable<Topic> allWorksapces = dmx.getTopicsByType(WORKSPACE);
            Iterator<Topic> workspaces = allWorksapces.iterator();
            log.info("### Exporting workspace topics ...");
            while (workspaces.hasNext()) {
                try {
                    JSONArray jsonTopics = json.getJSONArray("workspaces");
                    jsonTopics.put(workspaces.next().toJSON());
                } catch (AccessControlException ex) {
                    log.warning("### Workspace read permission denied => " + ex.getLocalizedMessage().toString());
                }
            }
        } catch (JSONException ex) {
            Logger.getLogger(ImportExportPlugin.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private List<RelatedTopic> getConfiguredTopicTypesForExport() {
        Topic plugin = dmx.getTopicByUri("systems.dmx.import-export");
        log.info("Loaded " + plugin.getSimpleValue() + " plugin topic for inspecting export type configuration");
        List<RelatedTopic> configuredTypes = plugin.getRelatedTopics(ASSOCIATION, DEFAULT,
                DEFAULT, TOPIC_TYPE);
        return configuredTypes;
    }

    private List<RelatedTopic> getConfiguredAssocTypesForExport() {
        Topic plugin = dmx.getTopicByUri(SYMBOLIC_BUNDLE_URI);
        List<RelatedTopic> configuredAssocTypes = plugin.getRelatedTopics(ASSOCIATION, DEFAULT,
                DEFAULT, ASSOC_TYPE);
        return configuredAssocTypes;
    }

    private void exportTopicmapToJSON(Topic topicmapTopic, JSONObject json) {
        try {
            JSONArray jsonTopics = json.getJSONArray("topicmaps");
            // fetch topicmap with all children
            Topicmap topicmap = topicmaps.getTopicmap(topicmapTopic.getId(), true);
            // fetch workspace assignment for topicmap
            Topic ws = workspaces.getAssignedWorkspace(topicmapTopic.getId());
            ws.loadChildTopics();
            // add workspace topic to Topicmap JSONObject
            JSONObject tm = topicmap.toJSON();
            tm.put("workspace", ws.toJSON());
            // add topicmap + workspace topic to export file
            jsonTopics.put(tm);
        } catch (AccessControlException ex) {
            log.warning("Topicmap read permission denied => " + ex.getLocalizedMessage().toString());
        } catch (JSONException ex) {
            Logger.getLogger(ImportExportPlugin.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /** Fixme: Check if we the consumed entity is actually ever used by a HTTP client and refactor resource. **/
    @GET
    @Transactional
    @Path("/content/json/{typeUri}")
    public Topic exportTopicsAndAssocsToJSON(@PathParam("typeUri") String typeUri, List<RelatedTopic> assocTypes, JSONObject givenJson) {
        try {
            log.info("### Exporting topics of type \""+typeUri+"\" with associations ...");
            JSONObject json = (givenJson != null) ? givenJson : new JSONObject();
            Iterable<Topic> allTopics = dmx.getTopicsByType(typeUri);
            Iterator<Topic> topics = allTopics.iterator();
            while (topics.hasNext()) {
                try {
                    JSONArray jsonTopics = json.getJSONArray("topics");
                    JSONObject topic = createTopicWithAssociationsJSON(topics.next(), assocTypes);
                    if (topic != null) jsonTopics.put(topic);
                } catch (AccessControlException ex) {
                    log.warning("### Topic read permission denied => " + ex.getLocalizedMessage().toString());
                }
            }
            if (givenJson == null) {
                InputStream in = new ByteArrayInputStream(json.toString().getBytes("UTF-8"));
                String jsonFileName = "dmx-"+typeUri + "-topics-"+new Date().toString()+".txt";
                return files.createFile(in, files.pathPrefix() + "/" + jsonFileName);
            } else {
                return null;
            }
        } catch (Exception e) {
            throw new RuntimeException("Creating Topics and Associations JSON Export failed", e);
        }
    }

    private JSONObject createTopicWithAssociationsJSON(Topic topic, List<RelatedTopic> assocTypes) throws JSONException {
        // 1) Load all childs of the topic in memory
        topic.loadChildTopics();
        Topic ws = null;
        // 2) Fetch all relating association of configured assocTypes
        List<Assoc> assocs = new ArrayList<Assoc>();
        for (RelatedTopic assocType : assocTypes) {
            List<RelatedTopic> relatedTopics = topic.getRelatedTopics(assocType.getUri(), null, null, null);
            for (RelatedTopic relatedTopic : relatedTopics) {
                assocs.add(relatedTopic.getRelatingAssoc().loadChildTopics());
            }
        }
        return new JSONObject()
                .put("topic", topic.toJSON())
                .put("associations", DMXUtils.toJSONArray(assocs));
    }

    @POST
    @Path("/import/{htmlOrJson}")
    @Consumes("multipart/form-data")
    @Transactional
    public Topic uploadFile(UploadedFile file, @PathParam("htmlOrJson") String htmlOrJson) {
        String operation = "Uploaded File " + file + " for importing " + htmlOrJson.toUpperCase();
        try {
            log.info(operation);
            StoredFile storedFile = files.storeFile(file, "/");
            return dmx.getTopic(storedFile.getFileTopicId());
        } catch (Exception e) {
            throw new RuntimeException(operation + " failed", e);
        }
    }



    // --- DMX Generic Content Import Functionalities

    /**
     * Should be run as logged in user `admin` with a workspace cookie set.
     * Handles **DMX** Exported JSON Content Files!
     * @param fileTopicId
     * @return fileTopicId
     */
    @GET
    @Path("/import/file/{fileTopidId}")
    @Transactional
    public Response importContentsBackupJson(@PathParam("fileTopidId") long fileTopicId) {
        try {
            log.info("FileTopicID to be imported: " + fileTopicId);
            String content = getFileContentsAsString(fileTopicId);
            JSONObject dump = new JSONObject(content);
            log.info("File Topic " + fileTopicId + " contents read in and parsed as JSON");
            // 1) re-create all workspaces (with admin as owner)
            JSONArray spaces = dump.getJSONArray("workspaces");
            createWorkspacesFromDM4JSON(spaces);
            // 2) Todo: assign topics to the right workspaces
            JSONArray objects = dump.getJSONArray("topics");
            createTopicsFromDM4JSON(objects);
            createAssociationsFromDM4JSON(objects);
            log.info("#### Part 1/2 of import operation - Topics an Associations - finished #### ");
            // log.info("# Starting import of Topicmaps into Workspaces ");
            // importTopicmapsFromJsonFile(fileTopicId);
        } catch (JSONException ex) {
            Logger.getLogger(ImportExportPlugin.class.getName()).log(Level.SEVERE, null, ex);
        }
        return Response.ok(fileTopicId).build();
    }

    /**
     * Fixme: Has to be run as logged in user `admin` as it queries for the `System` workspace.
     * Handles **DMX** Exported JSON Content Files!
     * @param fileTopicId
     * @return fileTopicId
     */
    @GET
    @Path("/import/file/{fileTopidId}/topicmaps")
    @Transactional
    public Response importTopicmapsBackupJson(@PathParam("fileTopidId") long fileTopicId) {
        try {
            log.info("FileTopicID to be imported: " + fileTopicId);
            String content = getFileContentsAsString(fileTopicId);
            JSONObject dump = new JSONObject(content);
            log.info("File Topic " + fileTopicId + " contents read in and parsed as JSON");
            JSONArray maps = dump.getJSONArray("topicmaps");
            for (int k = 0; k < maps.length(); k++) {
                try {
                    JSONObject topicmap = maps.getJSONObject(k);
                    // Recreate Topicmap <-> Topics, Assocs
                    Topic map = createTopicmapFromDM4JSON(topicmap);
                    if (map != null) {
                        log.info("Topicmap Topic recreated " + map.getSimpleValue() + "SUCCESSFULLY");
                        JSONArray assocs = topicmap.getJSONArray("assocs");
                        JSONArray topics = topicmap.getJSONArray("topics");
                        JSONObject workspace = topicmap.getJSONObject("workspace");
                        log.info("Topicmap contains " + topics.length() + " topics & " + assocs.length() + " associations");
                        log.info("Adding topics into topicmap ...");
                        addTopicsToTopicmapFromJSON(topics, map);
                        log.info("Adding assocs into topicmap ...");
                        addAssocsToTopicmapFromJSON(assocs, map);
                        log.info("Topicmap assocs and topics SUCCESSFULLY restored");
                        // Recreate Topicmap <-> Workspace Assignment
                        Topic ws = getWorkspaceByName(workspace.getString("value"));
                        if (ws != null) {
                            log.info("Topicmap assigned to Workspace => " + ws.getSimpleValue());
                            workspaces.assignToWorkspace(map, ws.getId());
                        }
                    } else {
                        log.warning("Recreating topicmap topic FAILED using " + topicmap.toString());
                    }
                } catch (JSONException ex) {
                    throw new RuntimeException(ex);
                }
            }
            log.info("#### Part 2/2 of import operation - Topicmaps - finished ####");
        } catch (JSONException ex) {
            throw new RuntimeException(ex);
        }
        return Response.ok(fileTopicId).build();
    }

    private Topic getWorkspaceByName(String name) {
        Topic realWs = null;
        Topic ws = dmx.getTopicByValue("dmx.workspaces.name", new SimpleValue(name)); // ### It worked once but does this still work?
        if (ws == null) {
                // double check if it really does not exist yet
                List<Topic> existingWs = dmx.getTopicsByType("dmx.workspaces.workspace");
                for (Topic topic : existingWs) {
                    if (topic.getSimpleValue().toString().equals(name)) {
                        return topic;
                    }
                }
        } else {
            realWs = ws.getRelatedTopic(null, CHILD, PARENT, "dmx.workspaces.workspace");
            return realWs;
        }
        return realWs;
    }

    private Topic getOrCreateWorkspace(JSONObject workspace) {
        Topic ws = null;
        try {
            String wsName = workspace.getString("value");
            // ### ACL Exception is thrown when "System" workspace arrives
            ws = dmx.getTopicByValue("dmx.workspaces.name", new SimpleValue(wsName)); // ### It worked once but does this still work?
            if (ws == null) {
                // double check if it really does not exist yet
                List<Topic> existingWs = dmx.getTopicsByType("dmx.workspaces.workspace");
                for (Topic topic : existingWs) {
                    if (topic.getSimpleValue().toString().equals(wsName)) {
                        return topic;
                    }
                }
                // create workspace
                log.info("Re-creating workspace on instance-level \"" + wsName + "\"");
                JSONObject childs = workspace.getJSONObject("childs");
                JSONObject sharingMode = childs.getJSONObject("dm4.workspaces.sharing_mode");
                String wsSharing = sharingMode.getString("value");
                if (wsSharing.equals("Public")) {
                    ws = workspaces.createWorkspace(wsName, null, SharingMode.PUBLIC);
                } else if (wsSharing.equals("Collaborative")) {
                    ws = workspaces.createWorkspace(wsName, null, SharingMode.COLLABORATIVE);
                } else if (wsSharing.equals("Confidential")) {
                    ws = workspaces.createWorkspace(wsName, null, SharingMode.CONFIDENTIAL);
                } else if (wsSharing.equals("Common")) {
                    ws = workspaces.createWorkspace(wsName, null, SharingMode.COMMON);
                }
                log.info("Setting new owner for workspace \"" + wsName + "\"");
                acl.setWorkspaceOwner(ws, acl.getUsername());
            } else {
                Topic realWs = ws.getRelatedTopic(null, CHILD, PARENT, "dmx.workspaces.workspace");
                log.info("Workspace exists already - " + realWs.toJSON());
                return realWs;
            }
        } catch (JSONException ex) {
            throw new RuntimeException(ex);
        }
        return ws;
    }

    private String getFileContentsAsString(long fileTopicId) {
        File export = files.getFile(fileTopicId);
        StringBuilder content = new StringBuilder();
        try {
            FileReader fileReader = new FileReader(export.getAbsolutePath());
            BufferedReader reader = new BufferedReader(fileReader);
            String         line = null;
            while((line = reader.readLine()) != null) {
                content.append(line);
            }
        } catch (FileNotFoundException ex) {
            Logger.getLogger(ImportExportPlugin.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(ImportExportPlugin.class.getName()).log(Level.SEVERE, null, ex);
        }
        return content.toString();
    }

    private void createWorkspacesFromDM4JSON(JSONArray topics) {
        try {
            for (int i = 0; i < topics.length(); i++) {
                JSONObject object = topics.getJSONObject(i);
                String wsName = object.getString("value");
                String wsUri = object.getString("uri");
                long wsId = object.getLong("id");
                try {
                    // ### Fixme: with or without workspace assignment ???
                    Topic workspace = workspaces.createWorkspace(wsName, wsUri, SharingMode.COLLABORATIVE);
                    log.info("### Imported \"" + workspace.getType().getUri() + "\" workspace \""
                            + workspace.getSimpleValue() +"\" (" + workspace.getId()+") - configuring " + acl.getUsername() + " as owner");
                    topicIds.put(wsId, workspace.getId());
                    acl.setWorkspaceOwner(workspace, acl.getUsername());
                } catch (RuntimeException re) {
                    Logger.getLogger(ImportExportPlugin.class.getName()).log(Level.SEVERE, "Workspace " + wsId + " (" + object.getString("value") + ") could not be created from DM4 JSON", re);
                }
            }
        } catch (JSONException ex) {
            Logger.getLogger(ImportExportPlugin.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void createTopicsFromDM4JSON(JSONArray topics) {
        int count = 0;
        for (int i = 0; i < topics.length(); i++) {
            try {
                JSONObject object = topics.getJSONObject(i);
                JSONObject topic = object.getJSONObject("topic");
                String wsName = object.getString("workspaceName");
                long created = object.getLong("created");
                long modified = object.getLong("modified");
                long formerId = topic.getLong("id");
                String topicJSON = buildDMXJSONTopicModel(topic);
                try {
                    Topic newTopic = dmx.createTopic(mf.newTopicModel(new JSONObject(topicJSON)));
                    log.info("### Imported \"" + newTopic.getType().getUri() + "\" topic \""
                            + newTopic.getSimpleValue() +"\" (" + newTopic.getId()+")");
                    topicIds.put(formerId, newTopic.getId());
                    // ### Fixme: set Created and Modified-Timestamps, Created: " + created + ", Last Modified: " + modified);
                    workspaces.assignToWorkspace(newTopic, getWorkspaceByName(wsName).getId());
                    count++;
                } catch (RuntimeException re) {
                    Logger.getLogger(ImportExportPlugin.class.getName()).log(Level.SEVERE, "Topic " + formerId + " (" + object.getJSONObject("topic") + ") could not be created from DM4 JSON", re);
                    lostTopics.add(new JSONObject(topicJSON));
                }
            } catch (JSONException ex) {
                Logger.getLogger(ImportExportPlugin.class.getName()).log(Level.SEVERE, "Topic could not be parsed from DM4 JSON", ex);
            }
        }
        log.info("##### ---- DM4 Content Restoration Report ---- ######");
        for (JSONObject t : lostTopics) {
            try {
                log.info("=> Lost " + t.getString("typeUri") + ", " + t.getString("value"));
            } catch (JSONException ex) {
                Logger.getLogger(ImportExportPlugin.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        log.info("=> " + count + " Topics Created, " + lostTopics.size() + " Topics Lost (!) during DM4 Content Restoration Efforts");
    }

    private void createAssociationsFromDM4JSON(JSONArray topics) {
        // ### Fixme: Certain topics may be already pre-installed (such as "Standard Website" e.g. by webpages)
        // and thus can't be linked to by topicId (as that is unknown to the dump).
        // Solution: We would need to support to re-create assocs not only by ID, but also by URI.
        // Which would need to be retrofitted to the exporter in the dm4 branch.
        for (int k = 0; k < topics.length(); k++) {
            try {
                JSONObject topic = topics.getJSONObject(k);
                JSONArray associations = topic.getJSONArray("associations");
                for (int m = 0; m < associations.length(); m++) {
                    JSONObject assoc = associations.getJSONObject(m);
                    String assocTypeUri = buildDMXJSONAssocTypeUri(assoc.getString("type_uri"));
                    long formerId = assoc.getLong("id");
                    // String assocValue = assoc.getString("value");
                    JSONObject assocChilds = assoc.getJSONObject("childs");
                    String assocChildsJSON = buildDMXJSONTopicModel(assocChilds);
                    long formerPlayer1 = 0;
                    long formerPlayer2 = 0;
                    try {
                        JSONObject role1 = assoc.getJSONObject("role_1");
                        JSONObject role2 = assoc.getJSONObject("role_2");
                        formerPlayer1 = role1.getLong("topic_id");
                        formerPlayer2 = role2.getLong("topic_id");
                        long newPlayer1Id = topicIds.get(formerPlayer1);
                        long newPlayer2Id = topicIds.get(formerPlayer2);
                        String newRoleType1 = role1.getString("role_type_uri").replace("dm4", "dmx");
                        String newRoleType2 = role2.getString("role_type_uri").replace("dm4", "dmx");
                        try {
                            // Check if assoc already exists between player1 and player2
                            // Todo: Implement this for the case of assocs between Topics and Assocs and between Assocs and Assocs
                            Assoc exists = dmx.getAssocBetweenTopicAndTopic(assocTypeUri, newPlayer1Id, newPlayer2Id, newRoleType1, newRoleType2);
                            Assoc newAssoc = null;
                            if (exists == null) {
                                PlayerModel player1 = mf.newTopicPlayerModel(newPlayer1Id, newRoleType1);
                                PlayerModel player2 = mf.newTopicPlayerModel(newPlayer2Id, newRoleType2);
                                ChildTopicsModel assocModel = mf.newChildTopicsModel(new JSONObject(assocChildsJSON));
                                try {
                                    // ### Fixme: with or without workspace assignment ???
                                    newAssoc = dmx.createAssoc(mf.newAssocModel(assocTypeUri, player1, player2, assocModel));
                                    assocIds.put(formerId, newAssoc.getId());
                                    log.info("### Imported " + newAssoc.getType().getUri()
                                            + " Association with label " + newAssoc.getSimpleValue());
                                // As associations are contained twice in the dump we need to catch this
                                } catch (RuntimeException re) {
                                    log.warning("> Association " + newAssoc + " could not be imported caused by \""
                                            + re.getCause().getMessage() + "\"");
                                }
                            } else {
                                log.info("> Association of type \"" + assocTypeUri + "\" was not created since it already exists");
                            }
                        } catch (RuntimeException rea) {
                            Logger.getLogger(ImportExportPlugin.class.getName()).log(Level.SEVERE, "Assoc existence check could not fetch assoc", rea);
                        }
                    } catch (NullPointerException npe) {
                        log.warning("> Player information is not contained in export file (Topic1: " + formerPlayer1 + " <-> Topic2: " + formerPlayer2 + " ) - Association not imported!");
                    }
                }
            } catch (JSONException ex) {
                Logger.getLogger(ImportExportPlugin.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    private Topic createTopicmapFromDM4JSON(JSONObject topicmap) {
        try {
            JSONObject state = topicmap.getJSONObject("info");
            // get map translation
            JSONObject stateChilds = state.getJSONObject("childs");
            JSONObject translation = stateChilds.getJSONObject("dm4.topicmaps.state");
            String translationValuePair = translation.getString("value");
            String[] pos = translationValuePair.split(" ");
            // creating topicmap
            Topic map = topicmaps.createTopicmap(state.getString("value"), TOPICMAP,
                    mf.newViewProps(Integer.parseInt(pos[0]), Integer.parseInt(pos[1])));
            log.info("### Imported Topicmap " + state.getString("value") + "... new Topicmap ID => " + map.getId());
            log.info("Topicmap Translation Set: " + translationValuePair.toString()
                    + " Position X: " + Integer.parseInt(pos[0]) + " Position Y: " + Integer.parseInt(pos[1]));
            return map;
        } catch (JSONException ex) {
            Logger.getLogger(ImportExportPlugin.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    private Topic createTopicmapFromJSON(JSONObject topicmap) {
        try {
            JSONObject state = topicmap.getJSONObject("topic");
            // get map translation
            JSONObject stateChilds = state.getJSONObject("children");
            JSONObject translation = stateChilds.getJSONObject("dmx.topicmaps.state");
            String translationValuePair = translation.getString("value");
            String[] pos = translationValuePair.split(" ");
            // creating topicmap
            Topic map = topicmaps.createTopicmap(state.getString("value"), TOPICMAP,
                    mf.newViewProps(Integer.parseInt(pos[0]), Integer.parseInt(pos[1])));
            log.info("### Imported Topicmap " + state.getString("value") + "... new Topicmap ID => " + map.getId());
            log.info("Topicmap Translation Set: " + translationValuePair.toString()
                    + " Position X: " + Integer.parseInt(pos[0]) + " Position Y: " + Integer.parseInt(pos[1]));
            return map;
        } catch (JSONException ex) {
            Logger.getLogger(ImportExportPlugin.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    private void addTopicsToTopicmapFromJSON(JSONArray topics, Topic map) {
        // adding topics to topicmap
        for (int t=0; t < topics.length(); t++) {
            JSONObject topic = null;
            try {
                topic = topics.getJSONObject(t);
                long formerTopicId = topic.getLong("id");
                String typeUri = topic.getString("type_uri");
                JSONObject viewProps = topic.getJSONObject("view_props");
                // ### String boxColor = viewProps.getString("dm4.boxrenderer.color");
                int posX = viewProps.getInt("dm4.topicmaps.x");
                int posY = viewProps.getInt("dm4.topicmaps.y");
                boolean visibility = viewProps.getBoolean("dm4.topicmaps.visibility");
                try {
                    long newTopicId = topicIds.get(formerTopicId); // all topics are imported as not pinned
                    topicmaps.addTopicToTopicmap(map.getId(), newTopicId, mf.newViewProps(posX, posY, visibility, false));
                    log.fine("Added topic " + topic.getString("value") + " to topicmap " + map.getSimpleValue());
                } catch (Exception e) {
                    log.warning("> Error adding " + topic.getString("value") + " to topicmap (formerId=" + formerTopicId + ", \""+ typeUri+ "\") formerId was not imported " + e + " - Skipping...");
                }
            } catch (JSONException ex) {
                log.severe("> Problem inspecting topic " + topic + " to add to topicmap " + map + " caused by " + ex.getCause().getMessage());
            }
        }
    }

    private void addAssocsToTopicmapFromJSON(JSONArray assocs, Topic map) {
        for (int t=0; t < assocs.length(); t++) {
            try {
                JSONObject assoc = assocs.getJSONObject(t);
                long formerAssocId = -1;
                try {
                    formerAssocId = assoc.getLong("id");
                    topicmaps.addAssocToTopicmap(map.getId(), assocIds.get(formerAssocId), mf.newViewProps(true, false));
                } catch (NullPointerException npe) {
                    log.warning("> Assoc=> " + assoc.toString() + " (id=" + formerAssocId + ") was not imported " + npe + " - Skipped adding to Topicmap");
                }
            } catch (JSONException ex) {
                Logger.getLogger(ImportExportPlugin.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    private String buildDMXJSONTopicModel(JSONObject topic) {
        String topicJSON = topic.toString();
        topicJSON = topicJSON.replaceAll("dm4", "dmx");
        topicJSON = topicJSON.replaceAll("childs", "children");
        topicJSON = topicJSON.replaceAll("type_uri", "typeUri");
        topicJSON = topicJSON.replaceAll("\"id\":[0-9]{1,10},", "\"id\":-1,");
        topicJSON = topicJSON.replaceAll("\"topic_id\":[0-9]{1,10},", "\"topic_id\":-1,");
        topicJSON = topicJSON.replaceAll("dmx.core.aggregation", COMPOSITION);
        topicJSON = topicJSON.replaceAll("dmx.contacts.institution", "dmx.contacts.organization");
        topicJSON = topicJSON.replaceAll("dmx.contacts.institution_name", "dmx.contacts.organization_name");
        topicJSON = topicJSON.replaceAll("dmx.events.title", "dmx.events.event_name");
        topicJSON = topicJSON.replaceAll("dmx.events.notes", "dmx.events.event_description");
        topicJSON = topicJSON.replaceAll("dmx.webbrowser.web_resource", "dmx.bookmarks.bookmark");
        topicJSON = topicJSON.replaceAll("dmx.webbrowser.webpage", "dmx.bookmarks.bookmark");
        topicJSON = topicJSON.replaceAll("dmx.webbrowser.url", "dmx.base.url");
        topicJSON = topicJSON.replaceAll("dmx.webbrowser.web_resource_description", "dmx.bookmarks.description");
        topicJSON = topicJSON.replaceAll("dmx.events.to", "dmx.datetime.to");
        topicJSON = topicJSON.replaceAll("dmx.events.from", "dmx.datetime.from");
        topicJSON = topicJSON.replaceAll("org.deepamehta.notification", "dmx.notification");
        topicJSON = topicJSON.replaceAll("org.deepamehta.notification_seen", "dmx.notification_seen");
        topicJSON = topicJSON.replaceAll("org.deepamehta.notification_title", "dmx.notification_title");
        topicJSON = topicJSON.replaceAll("org.deepamehta.notification_body", "dmx.notification_body");
        topicJSON = topicJSON.replaceAll("org.deepamehta.involved_item_id", "dmx.involved_item_id");
        topicJSON = topicJSON.replaceAll("org.deepamehta.subscribed_item_id", "dmx.subscribed_item_id");
        // General Modeling: "Contacts Note" (HTML) Add to "Person" and "Organization"
        // Dogfood Modeling: "Tag" (URI: "domain.project.topic_type_19953" (Simple Text) as Child of Note)
        return topicJSON;
    }
    
    private String buildDMXJSONAssocTypeUri(String assoc) {
        String assocJSON = assoc.toString();
        assocJSON = assocJSON.replaceAll("dm4", "dmx");
        assocJSON = assocJSON.replaceAll("dmx.events.participant", "dmx.events.event_involvement");
        assocJSON = assocJSON.replaceAll("dmx.contacts.organization_association", "dmx.contacts.organization_involvement");
        assocJSON = assocJSON.replaceAll("org.deepamehta.subscription_type", "dmx.subscription_type");
        assocJSON = assocJSON.replaceAll("org.deepamehta.notification_subscription_edge", "dmx.notification_subscription_edge");
        assocJSON = assocJSON.replaceAll("org.deepamehta.notification_recipient_edge", "dmx.notification_recipient_edge");
        return assocJSON;
    }


    // --- Topicmap Import / Export Functionalities

    @POST
    @Transactional
    @Path("/topicmap/{topicmapId}/export/json")
    public Topic exportTopicmapToJSON(@PathParam("topicmapId") long topicmapId, @CookieParam("dmx_topicmap_id") long topicmapCookie) {
        try {
            long useTopicmapId = (topicmapId != 0) ? topicmapId : topicmapCookie;
            log.info("Exporting Topicmap JSON ######### " + useTopicmapId);
            Topicmap topicmap = topicmaps.getTopicmap(useTopicmapId, true);
            String json = topicmap.toJSON().toString();
            InputStream in = new ByteArrayInputStream(json.getBytes("UTF-8"));
            String jsonFileName = "topicmap-" + useTopicmapId + ".json";
            return files.createFile(in, files.pathPrefix() + "/" + jsonFileName);
            // return filesService.createFile(in, jsonFileName);
        } catch (Exception e) {
            throw new RuntimeException("Export failed", e);
        }
    }

    @POST
    @Path("/topicmap/{topicmapId}/export/svg")
    @Transactional
    public Topic exportTopicmapToSVG(@PathParam("topicmapId") long topicmapId, @CookieParam("dmx_topicmap_id") long topicmapCookie) throws XMLStreamException {
        final int BOX_HEIGHT = 20;
        final int MARGIN_LEFT = 5;
        final int MARGIN_TOP = 14;
        final int ICON_WIDTH = 16;
        final int ICON_HEIGHT = 16;
        try {
            long useTopicmapId = (topicmapId != 0) ? topicmapId : topicmapCookie;
            log.info("Exporting Topicmap SVG ######### " + useTopicmapId);
            // 0) Fetch topicmap data
            Topicmap topicmap = topicmaps.getTopicmap(useTopicmapId, true);
            Iterable<ViewTopic> topics = topicmap.getTopics();
            Iterable<ViewAssoc> associations = topicmap.getAssocs();
            // 1) Setup default file name of SVG to write to
            String svgFileName = "Exported_Topicmap_" + useTopicmapId + ".svg";
            // 2) Get DM4 filerepo configuration setting and write to document to root folder
            String documentPath = files.getFile("/") + "/" + svgFileName;
            // 3) Create SVGWriter
            SVGRenderer svg = new SVGRenderer(documentPath);
            svg.startGroupElement(useTopicmapId);
            // 4) Create all associations
            for (ViewAssoc association : associations) {
                String valueAssoc = association.getSimpleValue().toString();
                long topic1Id = association.getPlayer1().getId();
                long topic2Id = association.getPlayer2().getId();
                ViewTopic topic1 = topicmap.getTopic(topic1Id);
                int x1 = topic1.getX();
                int y1 = topic1.getY();
                ViewTopic topic2 = topicmap.getTopic(topic2Id);
                int x2 = topic2.getX();
                int y2 = topic2.getY();
                // 
                int dx = x2 - x1;
                int dy = y2 - y1;
                int label_x = dx / 2;
                int label_y = dy / 2;
                double assocLine = Math.sqrt(Math.pow(dx, 2) + Math.pow(dy, 2));
                double alpha = Math.asin(dy / assocLine) * 180 / Math.PI;
                if (dx < 0) {
                    alpha = -alpha;
                }
                svg.startGroupElement(association.getId());
                svg.line(x1, x2, y1, y2);
                svg.text(label_x, label_y, x1 + 10, y1 + 10, valueAssoc, "grey", alpha);
                svg.endElement();
            }
            // 5) Create all topics
            for (ViewTopic topic : topics) {
                String value = topic.getSimpleValue().toString();
                int x = topic.getX();
                int y = topic.getY();
                boolean visibility = topic.getVisibility();
                int boxWidth = value.length() * 9;
                if (!visibility) {
                    continue;
                }
                svg.startGroupElement(topic.getId());
                svg.rectangle(x - boxWidth / 2, y - BOX_HEIGHT / 2, boxWidth, BOX_HEIGHT, color(topic.getTypeUri()));
                svg.text(x - boxWidth / 2 + MARGIN_LEFT, y - BOX_HEIGHT / 2 + MARGIN_TOP, value, "black");
                // ### Fixme: svg.image(x + boxWidth / 2, y, ICON_WIDTH, ICON_HEIGHT, typeIconDataUri(topic.getTypeUri()));
                svg.endElement();
            }
            // 6) Close SVGWriter
            svg.endElement();
            svg.closeDocument();
            // 7) Create and return new file topic for the exported document
            return files.getFileTopic(files.pathPrefix() + "/" + svgFileName);
        } catch (Exception e) {
            throw new RuntimeException("Export Topicmap to SVG failed", e);
        }
    }

    /**
     * Understands and imports a Topicmap JSON export document created with DMX.x.
     * @param file A de.deepamehta.files.UploadedFile object
     * @return A JSON Object encoded as a plain text String containing two properties used by the dm4-webclient: "message" and "topic_id".
     */
    @POST
    @Path("/import/topicmap")
    @Transactional
    @Consumes("multipart/form-data")
    public String importTopicmap(UploadedFile file) {
        try {
            String json = file.getString();

            JSONObject topicmap = new JSONObject(json);
            JSONObject info = topicmap.getJSONObject("topic");

            JSONArray assocsArray = topicmap.getJSONArray("assocs");
            JSONArray topicsArray = topicmap.getJSONArray("topics");

            String origTopicmapName = info.getString("value");
            // ### Todo: Currently we import topicmaps without their viewprops (translation)
            Topic importedTopicmap = topicmaps.createTopicmap("Imported: " + origTopicmapName, TOPICMAP, null);
            long topicmapId = importedTopicmap.getId();
            Map<Long, Long> mapTopicIds = new HashMap();
            importTopics(topicsArray, mapTopicIds, topicmapId);
            importAssociations(assocsArray, mapTopicIds, topicmapId);
            log.info("Created Topicmap \"" + importedTopicmap.getSimpleValue()+ "\"");
            // Todo: Add error reporting
            return "{\"message\": \"Topicmap successfully restored. \", \"topic_id\": "+importedTopicmap.getId()+"}";
        } catch (Exception e) {
            throw new RuntimeException("Importing Topicmap FAILED", e);
        }
    }

    /**
     * Imports a Firefox Bookmark Backup &amp; Restore JSON document generated via the Firefox Bookmark Manager. Imports bookmark folders as dmx.tags and 
     * related web resources to those tags. Tested with Mozilla Firefox 53.0.
     * @param file A de.deepamehta.files.UploadedFile object
     * @return A JSON Object encoded as a plain text String containing two properties used by the dm4-webclient: "message" and "topic_id".
     */
    @POST
    @Path("/import/bookmarks/firefox")
    @Transactional
    @Consumes("multipart/form-data")
    public String importFirefoxBookmarks(UploadedFile file) {
        try {
            String json = file.getString("UTF-8");
            JSONObject fileContent = new JSONObject(json);
            JSONArray firstChildren = fileContent.getJSONArray("children");
            log.info("###### Starting to map Firefox Bookmark Backup Entries to DMX Bookmarks ######");
            Topic importedNote = createNoteImportTopic(file.getName());
            int webResourcesCreatedCount = 0;
            for (int i = 0; i < firstChildren.length(); i++) {
                JSONObject entry = firstChildren.getJSONObject(i);
                if (entry.has("children")) {
                    JSONArray entryChilds = entry.getJSONArray("children");
                    for (int k = 0; k < entryChilds.length(); k++) {
                        JSONObject childEntry = entryChilds.getJSONObject(k);
                        Topic webResource = transformMozillaBookmarkEntry(childEntry, importedNote, null, 0);
                        if (webResource != null) webResourcesCreatedCount++;
                    }
                }
            }
            log.info("#### Mapping Firefox Bookmarks Backup COMPLETE: Created " + webResourcesCreatedCount + " new web resources ####");
            return "{\"message\": \"All valid entries contained in the Firefox backup file were imported as "
                + "Bookmarks.\", \"topic_id\": "+importedNote.getId()+"}";
        } catch (Exception e) {
            throw new RuntimeException("Importing Bookmarks from Firefox Bookmarks Backup FAILED", e);
        }
    }

    /**
     * Understands and processes a HTML style bookmark export file generated by the Chromium Bookmark Manager.
     * Works with Chromium build number 53.0.2785.x.
     * @param file A de.deepamehta.files.UploadedFile object
     * @return A JSON Object encoded as a plain text String containing two properties used by the dm4-webclient: "message" and "topic_id".
     */
    @POST
    @Path("/import/bookmarks/chromium")
    @Transactional
    @Consumes("multipart/form-data")
    public String importChromiumBookmarks(UploadedFile file) {
        try {
            String htmlString = file.getString("UTF-8");
            Document doc = Jsoup.parse(htmlString);
            Elements folderNames = doc.getElementsByTag("dt");
            log.info("###### Starting to map Chromium Bookmark HTML Export to DMX Bookmarks ######");
            Topic importedNote = createNoteImportTopic(file.getName());
            log.info("### Iterating " + folderNames.size() + " chromium bookmark entries (flattened).");
            if (folderNames.size() > 0) {
                Element dummyEntry = folderNames.get(0);
                List<Element> nodes = dummyEntry.children();
                for (Element element : nodes) {
                    transformChromiumResourceEntry(importedNote, element, null);
                }
            }
            log.info("#### Mapping Chromium Bookmarks Backup to Bookmarks COMPLETED ####");
            return "{\"message\": \"All valid entries contained in the Chromium backup file were turned imported as "
                + "Bookmarks.\", \"topic_id\": "+importedNote.getId()+"}";
        } catch (Exception e) {
            throw new RuntimeException("Importing Bookmarks from Chromium Bookmarks file FAILED", e);
        }
    }

    /**
     * Understands and processes a HTML style report on a collection created with a german build of Zotero 4.0.29.18.
     * @param file A de.deepamehta.files.UploadedFile object
     * @return A JSON Object encoded as a plain text String containing two properties used by the dm4-webclient: "message" and "topic_id".
     */
    @POST
    @Path("/import/bookmarks/zotero-report")
    @Transactional
    @Consumes("multipart/form-data")
    public String importZoteroReportBookmarks(UploadedFile file) {
        try {
            String htmlString = file.getString("UTF-8");
            Document doc = Jsoup.parse(htmlString);
            Elements webpages = doc.getElementsByClass("webpage");
            log.info("###### Starting to map Zotero Report Bookmarks to DMX Bookmarks ######");
            Topic importedNote = createNoteImportTopic(file.getName());
            log.info("### Iterating " + webpages.size() + " webpages in zotero report.");
            for (Element webpage : webpages) {
                transformZoteroWebpageEntry(importedNote, webpage);
            }
            log.info("#### Mapping Zotero Report Bookmarks to Bookmarks COMPLETED ####");
            return "{\"message\": \"All valid webpage entries in the zotero report file were imported as "
                + "Bookmarks.\", \"topic_id\": "+importedNote.getId()+"}";
        } catch (Exception e) {
            throw new RuntimeException("Importing Bookmarks from Zotero Report Bookmarks file FAILED", e);
        }
    }

    // --- Private Importer Transformation Methods ---

    private Topic transformMozillaBookmarkEntry(JSONObject childEntry, Topic importedNote, Topic folderNameTag, int levelCount) {
        Topic webResource = null;
        int recursionCount = levelCount;
        try {
            String entryType = childEntry.getString("type");
            if (entryType.equals("text/x-moz-place")) {
                // Check if (folderNameTag != null), associate item with tag too
                if (childEntry.has("title") && childEntry.has("uri")) {
                    webResource = transformFirefoxResourceEntry(childEntry);
                    if (webResource != null) { // Topic was either fetched or newly created succcesfully
                        createBookmarkRelations(importedNote, webResource, folderNameTag);
                    } else {
                        // An exception has occured.
                        log.warning("Bookmark Entry could not be created with JSONObject: " + childEntry.toString());
                    }
                } else {
                    log.warning("Skipping Bookmark entry due to missing Title or URL, " + childEntry.toString());
                }
            } else if (entryType.equals("text/x-moz-place-container")) {
                log.warning("Bookmarking Container Detected - Mapping Bookmarker Folders to Tags...");
                // 1) Get or create folderName tag topic
                String folderName = childEntry.getString("title");
                // ### Fixme: Support for caseInsensitive tag matching
                Topic folderTopic = createTagTopic(folderName);
                if (folderNameTag != null) {
                    getOrCreateSimpleAssoc(folderNameTag, folderTopic);
                }
                if (childEntry.has("children")){
                    JSONArray entryChildsChilds = childEntry.getJSONArray("children");
                    log.info("  "+recursionCount+ "ndLevel Bookmark Folder " + folderName + " - TODO: Transform \""+folderName+"\" into TAG");
                    // 2.0) If entry is of type bookmark, create a tag for it, associate it with the parent tag and then
                        // go over all its children and (recursively) call transformMozillaBookmarkEntry on them
                    for (int m = 0; m < entryChildsChilds.length(); m++) {
                        recursionCount++;
                        JSONObject childChildEntry = entryChildsChilds.getJSONObject(m);
                        transformMozillaBookmarkEntry(childChildEntry, importedNote, folderTopic, recursionCount);
                    }
                }
            }
        } catch (JSONException ex) {
            Logger.getLogger(ImportExportPlugin.class.getName()).log(Level.SEVERE, null, ex);
        }
        return webResource;
    }

    private Topic createTagTopic(String tagName) {
        return dmx.createTopic(mf.newTopicModel("dmx.tags.tag",
                mf.newChildTopicsModel().set("dmx.tags.tag_name", tagName)));
    }
    
    private Topic transformFirefoxResourceEntry(JSONObject childEntry) {
        try {
            String bookmarkDescription = childEntry.getString("title");
            String bookmarkUrl = childEntry.getString("uri");
            long dateAdded = 0;
            if (bookmarkUrl.startsWith("place:") || bookmarkUrl.startsWith("chrome:")
                || bookmarkUrl.startsWith("about:")) return null; // do not import browser internal bookmarks
            if (childEntry.has("dateAdded")) {
                dateAdded = childEntry.getLong("dateAdded");
                dateAdded = new Date(dateAdded / 1000).getTime();
            } else {
                log.warning("Could not detect " + bookmarkDescription + " dateAdded timestamp, setting it NOW, DEBUG: " + childEntry.toString());
            }
            long lastModified = 0;
            if (childEntry.has("lastModified")) {
                childEntry.getLong("lastModified");
                lastModified = new Date(lastModified / 1000).getTime();
            } else {
                log.warning("Could not detect " + bookmarkDescription + " lastModified timestamp, setting it NOW, DEBUG: " + childEntry.toString());
            }
            log.info("### Processing firefox link entry  \"" + bookmarkUrl + "\", Added: "
                + new Date(dateAdded).toLocaleString() +  ", Modified: " + new Date(lastModified).toLocaleString());
            return getOrCreateWebResource(bookmarkUrl, bookmarkDescription, dateAdded, lastModified);
        } catch (JSONException ex) {
            Logger.getLogger(ImportExportPlugin.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }

    private Topic getTagTopic(String name) {
        Topic tagName = dmx.getTopicByValue("dmx.tags.tag_name", new SimpleValue(name));
        return getTagTopic(tagName);
    }

    private Topic getTagTopic(Topic name) {
        return (name != null) ? name.getRelatedTopic(null, CHILD, PARENT, "dmx.tags.tag") : null;
    }
    
    private void transformChromiumResourceEntry(Topic importedNote, Element element, Topic toBeRelated) {
        if (element.nodeName().equals("a")) {
            String linkHref = element.attr("href");
            String linkName = element.text();
            String linkAddedValue = element.attr("add_date");
            String linkModifiedValue = element.attr("last_modified");
            long linkAdded = 0, linkModified = 0;
            Date customDateAdded = new Date();
            Date customDateModified = new Date();
            if (!linkAddedValue.isEmpty()) {
                linkAdded = Long.parseLong(linkAddedValue)*1000;
                customDateAdded = new Date(linkAdded);
            }
            if (!linkModifiedValue.isEmpty()) {
                log.info("Chromium linkedModified => " + linkModifiedValue);
                linkModified = Long.parseLong(linkModifiedValue)*1000;
                customDateModified = new Date(linkModifiedValue);
            }
            /* String associatedWithMessage = (toBeRelated != null) ? ", Associate with : " + toBeRelated.getSimpleValue() + "" : ""; **/
            log.info("### Processing chromium link entry  \"" + linkName + "\" (" + linkHref + "), Added: "
                + customDateAdded.toLocaleString() +  " (value=" + linkAddedValue+ "), Modified: " + customDateModified.toLocaleString() + "(value=" + linkModifiedValue+ ")");
            Topic webResource = getOrCreateWebResource(linkHref, linkName, customDateAdded.getTime(), customDateModified.getTime());
            createBookmarkRelations(importedNote, webResource, toBeRelated);
        } else if (element.nodeName().equals("h3")) {
            String text = element.ownText().trim();
            long folderAdded = Long.parseLong(element.attr("add_date"));
            String linkModifiedValue = element.attr("last_modified");
            long folderModified = new Date().getTime();
            if (!linkModifiedValue.isEmpty()) {
                folderModified = Long.parseLong(linkModifiedValue);
            }
            log.info("### Processing chromium bookmark folder element named \"" + text + "\"");
            Topic tag = getTagTopic(text);
            if (tag == null) {
                tag = createTagTopic(text);
                tag.setProperty(DMX_TIME_CREATED, folderAdded, true);
                if (folderModified != 0) {
                    tag.setProperty(DMX_TIME_MODIFIED, folderModified, true);
                }
                log.info("NEW tag \""+text+"\" created during import");
            }
            // mirror bookmark folder hierarchy through a simply tag to tag association
            if (toBeRelated != null) {
                getOrCreateSimpleAssoc(toBeRelated, tag);
            }
            // associate tag with imported note
            if (importedNote != null && tag != null) {
                getOrCreateSimpleAssoc(importedNote, tag);
            }
            transformChromiumResourceEntry(importedNote, element.nextElementSibling(), tag);
        } else if (element.nodeName().equals("dt") || element.nodeName().equals("dl")) {
            List<Element> childNodes = element.children();
            log.info("### Processing chromium list element with name " + element.nodeName() + " and "+childNodes.size()+" childs");
            for (Element childNode : childNodes) {
                transformChromiumResourceEntry(importedNote, childNode, toBeRelated);
            }
        }
    }

    private Topic transformZoteroWebpageEntry(Topic importedNote, Element listItem) {
        // 0) Declare parsers goal
        String webpageTitle = null, webpageUrl = null, entryType = null;
        long bookmarkCreated = new Date().getTime();
        long bookmarkModified = new Date().getTime();
        Topic webpage = null;
        // 1) Collect Entry Name
        List<Element> childs = listItem.children();
        for (Element element : childs) {
            if (element.nodeName().equals("h2")) {
                String text = element.ownText().trim();
                webpageTitle = text;
                log.info("### Processed zotero report webpage title \"" + text + "\"");
            }
        }
        // 2) Collect tags of an entry
        List<String> bookmarkTags = new ArrayList<String>();
        List<Element> tagElements = listItem.getElementsByClass("tags");
        for (Element tagElement : tagElements) {
            if (tagElement.nodeName().equals("ul")) {
                List<Element> tagEntries = tagElement.getElementsByTag("li");
                for (Element tagEntry : tagEntries) {
                    bookmarkTags.add(tagEntry.ownText().trim());
                }
            }
        }
        // 3) Collect attribute values of an entry
        List<Element> attributes = listItem.getElementsByTag("tr");
        for (Element attribute : attributes) { // each tr stands for an attribute and has exactly 1 th and 1 td
            Element keyCell = attribute.child(0); // th
            Element valueCell = attribute.child(1); // td
            String keyOne = keyCell.ownText().trim();
            if (keyOne.equals(ZOTERO_ENTRY_URL_COLUMN_KEY)) {
                List<Element> ahrefs = valueCell.getElementsByTag("a");
                if (ahrefs.size() > 0) {
                    webpageUrl = ahrefs.get(0).attr("href");
                    log.fine("### Parsed zotero report webpage URL: " + webpageUrl);
                }
            } else if (keyOne.equals(ZOTERO_ENTRY_TYPE_COLUMN_KEY)) {
                entryType = valueCell.ownText();
                // log.info("### Skipped zotero report entry of type: \"" + entryType + "\"");
            } else if (keyOne.equals(ZOTERO_ADDED_AT_COLUMN_KEY)) {
                String entryAdded = valueCell.ownText();
                if (!entryAdded.isEmpty()) {
                    try {
                        bookmarkCreated = new Date().parse(entryAdded);
                    } catch (IllegalArgumentException iex) {
                        log.warning("Could not parse date of bookmark created \"" + entryAdded + "\", cause " + iex.getMessage());
                    }
                }
            } else if (keyOne.equals(ZOTERO_MODIFIED_AT_COLUMN_KEY)) {
                String entryModified = valueCell.ownText();
                if (!entryModified.isEmpty()) {
                    try {
                        bookmarkModified = new Date().parse(entryModified);
                    } catch (IllegalArgumentException iex) {
                        log.warning("Could not parse date of bookmark last modified \"" + entryModified + "\", cause " + iex.getMessage());
                    }
                }
            }
        }
        // We import anything that has _some value_ in the URL column
        if (webpageUrl != null) {
            webpage = getOrCreateWebResource(webpageUrl, webpageTitle + "<br/>" + entryType, bookmarkCreated, bookmarkModified);
            if (bookmarkTags.size() > 0) {
                log.fine("### Importing " + bookmarkTags + " as tags for this webpage from zotero report");
            }
            for (String tagValue : bookmarkTags) {
                Topic tag = getTagTopic(tagValue);
                if (tag == null) {
                    tag = createTagTopic(tagValue);
                    log.info("NEW tag \""+tagValue+"\" created during import");
                }
                if (tag != null) {
                    createBookmarkRelations(importedNote, webpage, tag);
                }
            }
        }
        return webpage;
    }

    // --- Private Utilty Methods ---

    private Topic createNoteImportTopic(String fileName) {
        ChildTopicsModel childValues = mf.newChildTopicsModel();
        childValues.set("dmx.notes.title", "Bookmarks Import, " + fileName + " by " + acl.getUsername());
        childValues.set("dmx.notes.text", "This note relates all bookmarks created through an import process done by " + acl.getUsername() + " "
            + "(" + fileName +"). Please do not delete this note as it might become helpful if you need to identify which "
            + "bookmarks where imported when, by whom using which file.");
        Topic importerNote = dmx.createTopic(mf.newTopicModel("dmx.notes.note", childValues));
        log.info("### Importer Note Topic for \""+fileName+"\" CREATED");
        return importerNote;
    }

    private Assoc createBookmarkRelations(Topic importerNote, Topic webResource, Topic folderNameTag) {
        Assoc importedAssoc = null;
        if (importerNote != null) {
            // 1) Check if association to "importerNote" exists
            // Todo: Implement this for the case of assocs between Topics and Assocs and between Assocs and Assocs
            importedAssoc = dmx.getAssocBetweenTopicAndTopic(ASSOCIATION, importerNote.getId(), webResource.getId(), DEFAULT, DEFAULT);
            if (importedAssoc == null) {
                // 2) Create association to "importerNote" exists
                importedAssoc = dmx.createAssoc(mf.newAssocModel(ASSOCIATION,
                    mf.newTopicPlayerModel(importerNote.getId(), DEFAULT),
                    mf.newTopicPlayerModel(webResource.getId(), DEFAULT)));
            }
        }
        if (folderNameTag != null) {
            getOrCreateSimpleAssoc(folderNameTag, webResource); // choosing to set the tag as parent
        }
        return importedAssoc;
    }

    private Assoc getOrCreateSimpleAssoc(Topic defaultPlayer1, Topic defaultPlayer2) {
        // 3) Check and create assoc to folderNameTag
        // Todo: Implement this for the case of assocs between Topics and Assocs and between Assocs and Assocs
        Assoc folderTagAssoc = dmx.getAssocBetweenTopicAndTopic(ASSOCIATION, defaultPlayer1.getId(), defaultPlayer2.getId(),
            PARENT, CHILD);
        if (folderTagAssoc == null) {
            // 4) Create assoc from webResource to folderNameTag
            folderTagAssoc = dmx.createAssoc(mf.newAssocModel(ASSOCIATION,
                mf.newTopicPlayerModel(defaultPlayer1.getId(), PARENT),
                mf.newTopicPlayerModel(defaultPlayer2.getId(), CHILD)));
            log.info("NEW relation from \"" + defaultPlayer1.getTypeUri() + "\" created to \"" + defaultPlayer2.getTypeUri()+ "\"");
        }
        return folderTagAssoc;
    }

    private Topic getOrCreateWebResource(String url, String description, long created, long modified) {
        // 1) Check if a Bookmark Topic with that URL already exists
        Topic webResource;
        try {
            webResource = dmx.getTopicByValue("dmx.base.url", new SimpleValue(url.trim()));
            if (webResource != null) {
                log.info("### Bookmark \""+url+"\" EXISTS - NOT UPDATED");
                Topic webRsrcParent = webResource.getRelatedTopic(COMPOSITION, CHILD, PARENT, "dmx.bookmarks.bookmark");
                return (webRsrcParent != null) ? webRsrcParent : webResource;
            }
        } catch (RuntimeException re) {
            // This could be an AccessControlExcception or a runtime exception pointing at ambiguity of a
            // java.util.NoSuchElementException: More than one element in org.neo4j.index.impl.lucene.LuceneIndex
            // In any way, we cannot do any thing more about the circumstances which lead us here but noting them.
            log.warning("Bookmark could not be created, either due to an access control issue or a "
                + "messed up lucene KEY index (allowing web resources to exists just once in a DB), caused by: "
                + re.getLocalizedMessage());
        }
        // 2) Create new Bookmark Topic
        ChildTopicsModel childValues = mf.newChildTopicsModel();
        childValues.set("dmx.base.url", url.trim());
        childValues.set("dmx.bookmarks.description", description);
        webResource = dmx.createTopic(mf.newTopicModel("dmx.bookmarks.bookmark", childValues));
        if (created != 0) webResource.setProperty(DMX_TIME_CREATED, created, true);
        // lastModified is anyway overwritten by dm4-times plugin as (i guess) setting the timepropery is an udpate in itself
        if (modified != 0) webResource.setProperty(DMX_TIME_MODIFIED, modified, true);
        log.info("### Bookmark \""+url+"\" CREATED");
        return webResource;
    }

    // Import topics
    private void importTopics(JSONArray topicsArray, Map<Long, Long> mapTopicIds, long topicmapId) {
        int created = 0, failed = 0;
        for (int i = 0, size = topicsArray.length(); i < size; i++) {
            try {
                JSONObject topic = topicsArray.getJSONObject(i);
                createTopic(topic, mapTopicIds, topicmapId);
                created++;
            } catch (Exception e) {
                log.warning("Topic NOT imported, caused by \"" + e.getCause().getLocalizedMessage().toString() + "\"");
                failed++;
            }
        }
        log.info("### " + created + " Topics created, " + failed + " Topics failed during topicmap import");
    }

    // Import associations
    private void importAssociations(JSONArray assocsArray, Map<Long, Long> mapTopicIds, long topicmapId) {
        int created = 0, failed = 0;
        for (int i = 0, size = assocsArray.length(); i < size; i++) {
            try {
                JSONObject association = assocsArray.getJSONObject(i);
                createAssociation(association, mapTopicIds, topicmapId);
                created++;
            } catch (Exception e) {
                if (e.getCause() != null) {
                    log.warning("Assoc NOT imported, caused by \"" + e.getCause().getLocalizedMessage().toString() + "\"");
                    failed++;
                } else {
                    log.warning("Assoc NOT imported, caused by \"" + e.getLocalizedMessage() + "\"");
                    throw new RuntimeException(e);
                }
            }
        }
        log.info("### " + created + " Assocs created, " + failed + " Assocs failed during topicmap import");
    }

    private String color(String typeUri) {
        if (typeUri.equals("dmx.contacts.institution")) {
            return "lightblue";
        } else if (typeUri.equals("dmx.contacts.person")) {
            return "lightblue";
        } else if (typeUri.equals("dmx.notes.note")) {
            return "lightblue";
        } else {
            return "lightblue";
        }
    }

    /** ### Make this work for custom icons too, this works currently just with icons included in the standard
     * distribution. */
    private String typeIconDataUri(String typeUri) throws IOException {
        TopicType topicType = dmx.getTopicType(typeUri);
        String iconPath = (String) topicType.getViewConfigValue("dmx.webclient.view_config", "dmx.webclient.icon");
        InputStream iconIS = null;
        // TODO: Load icons bundled in other plugins
        // String pluginPath = iconPath.substring(1, sep);
        // Plugin plugin = dmx.getPlugin(pluginPath);
        try {
            int sep = iconPath.indexOf("/", 2); // Note: iconPath may be null and throw a NPE
            String imagePath = "web" + iconPath.substring(sep);
            iconIS = getStaticResource(imagePath);
            log.fine("##### IconIS " + iconIS);
        } catch (Exception e) {
            // Icon resource not found in this plugin
            log.info("### FALLBACK to standard grey icon as typeIcon for \""
                    + typeUri + "\" icon could not be determined " + "during SVG Export");
            iconIS = dmx.getPlugin("systems.dmx.webclient").getStaticResource("web/images/ball-gray.png");
        }
        // create base64 representation of the current type icon
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int count = 0;
        while ((count = iconIS.read(buffer)) != -1) {
            baos.write(buffer, 0, count);
        }
        byte[] fileContent = baos.toByteArray();
        // all chars in encoded are guaranteed to be 7-bit ASCII
        byte[] encoded = Base64.encode(fileContent);
        String imgBase64Str = new String(encoded);
        log.fine("##### IMG BASE64 " + imgBase64Str);
        //
        return "data:image/png;base64," + imgBase64Str;
    }

    private void createTopic(JSONObject topic, Map<Long, Long> mapTopicIds, long topicmapId) throws JSONException {
        TopicModel model = mf.newTopicModel(topic);
        ViewProps viewProps = mf.newViewProps(topic.getJSONObject("viewProps"));
        viewProps.set("dmx.topicmaps.pinned", false);
        // maybe replace "dm4" prefixes
        long origTopicId = model.getId();
        Topic newTopic = dmx.createTopic(model);
        long topicId = newTopic.getId();
        mapTopicIds.put(origTopicId, topicId);
        topicmaps.addTopicToTopicmap(topicmapId, topicId, viewProps);
    }

    /** Todo: Currently only associations between topics are re-created, not assocs to/from an assoc. **/
    private void createAssociation(JSONObject association, Map<Long, Long> mapTopicIds, long topicmapId) {
        AssocModel oldAssocModel = mf.newAssocModel(association);
        PlayerModel player1 = oldAssocModel.getPlayer1();
        PlayerModel player2 = oldAssocModel.getPlayer2();
        if (player1 != null && player2 != null) {
            long newPlayer1Id = mapTopicIds.getOrDefault(player1.getId(), new Long(-1));
            long newPlayer2Id = mapTopicIds.getOrDefault(player2.getId(), new Long(-1));
            if (newPlayer1Id == -1 || newPlayer2Id == -1) {
                AssocModel newAssocModel = mf.newAssocModel(oldAssocModel.getTypeUri(),
                    mf.newTopicPlayerModel(newPlayer1Id, player1.getRoleTypeUri()),
                    mf.newTopicPlayerModel(newPlayer2Id, player2.getRoleTypeUri()),
                            oldAssocModel.getChildTopics());
                Assoc newAssociation = dmx.createAssoc(newAssocModel);
                long assocId = newAssociation.getId();
                topicmaps.addAssocToTopicmap(topicmapId, assocId, mf.newViewProps(true, false));
            } else  {
               log.warning("Could not re-create assoc due to failure reading topicIds from topcIds map for "
                        + "oldAssocModel=\"" + oldAssocModel + "\", probably some topics could not be "
                        + "imported succesfully and the topicIds printed here are involved in previous errors."); 
            }
        } else {
            log.warning("Could not re-create assoc due to failure reading players from "
                    + "oldAssocModel=\"" + oldAssocModel + "\"");
        }
    }

}
