dm4c.add_plugin("net.abriraqui.import-export", function(){
    dm4c.add_listener("post_refresh_topicmap_menu", function(topicmap_menu) {
	topicmap_menu.add_separator()
	topicmap_menu.add_item({
	    label: "Import Topicmap",
	    handler: import_topicmap
	})
	
	function import_topicmap(){
	    alert("Import topicmap XX")
	}
    })
})
