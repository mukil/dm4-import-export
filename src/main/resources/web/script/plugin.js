
dm4c.add_plugin("net.abriraqui.import-export", function() {

    dm4c.add_listener("post_refresh_topicmap_menu", function(topicmap_menu) {

        // check if user is authenticated
        if (dm4c.get_plugin("de.deepamehta.accesscontrol").get_username()) {

            topicmap_menu.add_separator()

            topicmap_menu.add_item({
                label: "Export Topicmap to JSON",
                handler: export_topicmap_toJSON
            })

            topicmap_menu.add_item({
                label: "Import Topicmap from JSON",
                handler: import_topicmap
            })

            topicmap_menu.add_item({
                label: "Export Topicmap to SVG",
                handler: export_topicmap_toSVG
            })

        }
	
        function export_topicmap_toJSON() {
            var export_file = dm4c.restc.request("POST", "/import-export/export/json")
            dm4c.show_topic(new Topic(export_file), "show")
        }

        function export_topicmap_toSVG() {
            var svg_exported_file = JSON.parse(dm4c.restc.request("POST",
                "/import-export/export/svg", undefined, undefined, undefined, "text"))
            dm4c.show_topic(new Topic(svg_exported_file), "show")
        }

	    function import_topicmap() {
            dm4c.get_plugin("de.deepamehta.files")
                .open_upload_dialog("/import-export/import", function(importedTopicmap) {
                    dm4c.get_plugin("de.deepamehta.topicmaps").add_topicmap(importedTopicmap.id)
                })
        }
	})

})

