dm4c.add_plugin("net.abriraqui.import-export", function(){

    dm4c.add_listener("post_refresh_topicmap_menu", function(topicmap_menu) {
	topicmap_menu.add_separator()
	topicmap_menu.add_item({
	    label: "Export Topicmap",
	    handler: import_topicmap
	})
	
	function import_topicmap(){
	    var topicmap = dm4c.get_plugin("de.deepamehta.topicmaps").get_topicmap()
	    dm4c.restc.request("POST", "/import-export/export")
	    alert("Export of topicmap " + topicmap.get_id() + " complete")
	}
    })
})
