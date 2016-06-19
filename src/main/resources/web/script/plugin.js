
dm4c.add_plugin("net.abriraqui.import-export", function() {

    dm4c.add_listener("post_refresh_create_menu", function(create_menu) {
        create_menu.add_item({
            label: "Import Bookmarks",
            handler: open_bookmarks_dialog
        })

        var settingsDialog = undefined

        function open_bookmarks_dialog() {
            var dialogBody = $('<div class="message">').html("Here you can import a <em>file backup</em> of your Firefox bookmarks. "
                + "The file must be a file ending on \".json\" and you can generate yours via the <em>Bookmark Manager</em> in Firefox. Importing bookmarks from a Firefox HTML Export are not yet supported."
                + "<br/><br/><span class=\"field-label\">Note: The import may take a little while. You can import the same backup file over and over again without needing to worry about "
                + "ending up with duplicate entries for the same web resource in your database.</span>")
            settingsDialog = dm4c.ui.dialog({
                "id": 'bookmark-settings',
                "title": 'Importing Bookmarks Settings',
                "width": 360,
                "content": dialogBody,
                "button_label": "Open File Upload Dialog",
                "button_handler": function(e) {
                    import_ffox_bookmarks()
                },
                "auto_close": true
            })
        }

        function import_ffox_bookmarks() {
            dm4c.get_plugin("de.deepamehta.files")
                .open_upload_dialog("/import-export/import/bookmarks/firefox", function(response) {
                    console.log("Imported Firefox Bookmarks", response)
                    settingsDialog.close()
                    dm4c.ui.dialog({"title": "Fine!", "content": response.message,
                        "width": 300, "button_label": "OK",
                        "button_handler": function(){}, "auto_close": true })
                    if (response.hasOwnProperty("topic_id")) {
                        dm4c.do_reveal_topic(response.topic_id, "show")
                    }
                })
        }
    })

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
