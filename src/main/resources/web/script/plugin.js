dm4c.add_plugin("net.abriraqui.import-export", function(){

    dm4c.add_listener("post_refresh_topicmap_menu", function(topicmap_menu) {
	if (dm4c.has_create_permission("dm4.topicmaps.topicmap")) {
	    topicmap_menu.add_separator()
	    topicmap_menu.add_item({
		label: "Export Topicmap to JSON",
		handler: export_topicmap_toSJON
	    })

	    topicmap_menu.add_item({
		label: "Export Topicmap to SVG",
		handler: export_topicmap_toSVG
	    })

	    topicmap_menu.add_item({
		label: "Import Topicmap",
		handler: import_topicmap
	    })
            
            topicmap_menu.add_item({
		label: "Import Address Book (Thunderbird CSV)",
		handler: import_thunderbird_contacts
	    })
	} 
	
	function export_topicmap_toSJON(){
	   var export_file = dm4c.restc.request("POST", "/import-export/export/json")
	    dm4c.show_topic(new Topic(export_file), "show")

	}

	function export_topicmap_toSVG(){
	   var export_file = dm4c.restc.request("POST", "/import-export/export/svg")
	    alert("Topicmap exported to SVG")
	}


	function import_topicmap(){
	    dm4c.get_plugin("de.deepamehta.files").open_upload_dialog("/import-export/import/topicmap", 
            function(importedTopicmap){
		dm4c.get_plugin("de.deepamehta.topicmaps").add_topicmap(importedTopicmap.id)
	    })
	}
        
        function import_thunderbird_contacts(){
	    dm4c.get_plugin("de.deepamehta.files")
                    .open_upload_dialog("/import-export/import/contacts/thunderbird/csv/tab/en", 
                function(response){
                    console.log("Imported thunderbird contacts .. ", response)
                })
	}

    })
})
