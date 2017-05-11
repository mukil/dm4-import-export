
dm4c.add_plugin("net.abriraqui.import-export", function() {

    var CHROMIUM_ENDPOINT = "/import-export/import/bookmarks/chromium"
    var FIREFOX_ENDPOINT = "/import-export/import/bookmarks/firefox"
    var ZOTERO_ENDPOINT = "/import-export/import/bookmarks/zotero-report"

    var selectedEndpoint = FIREFOX_ENDPOINT

    dm4c.add_listener("post_refresh_create_menu", function(create_menu) {
        create_menu.add_item({
            label: "Import Bookmarks",
            handler: open_bookmarks_dialog
        })

        var settingsDialog = undefined

        function open_bookmarks_dialog() {
            // 0) Create modal options dialog
            var dialogBody = $('<div class="message">').html("<h3>Info</h3><p>You can import a <em>file backup</em> of your browsers bookmarks. "
                + "The backup file must be either ending on \".json\" (<em>Firefox</em>) or \".html\" (<em>Chrome/Chromium</em>). "
                + "You can create a backup of your browsers bookmark via the resp. <em>Bookmark Manager</em> in your browser.</p>"
                + "<h3>Browser</h3><div class='options-menu'></div>"
                + "<p><span class='field-label'><b>Please note</b><br/>1. The import may take some minutes, please leave your browser window open. 2. You can import the same backup file over "
                + "and over again without needing to worry about ending up with duplicate entries for the same web resource in your database.</span></p>")
            settingsDialog = dm4c.ui.dialog({
                "id": 'bookmark-settings',
                "title": 'Importing Bookmarks Settings',
                "width": 360,
                "content": dialogBody,
                "button_label": "Open File Upload Dialog",
                "button_handler": function() {
                    import_bookmarks(selectedEndpoint)
                },
                "auto_close": true
            })
            // Note: The following two steps are necessary because we need to register a
            // handler for the select menu function'(get an output for an option)
            // 1) Create Browser vendor selection menu ...
            var $browserSelect = $("<select id='vendor' class='ui select'>")
                $browserSelect.append("<option value='chromium'>Chromium</option>")
                $browserSelect.append("<option value='firefox' selected>Mozilla Firefox</option>")
                $browserSelect.append("<option value='zotero'>Zotero Report Webpages</option>")
                $browserSelect.on('change', function(e) {
                    var selectMenu = $browserSelect[0]
                    if (selectMenu.value === "firefox") {
                        selectedEndpoint = FIREFOX_ENDPOINT
                    } else if (selectMenu.value === "chromium") {
                        selectedEndpoint = CHROMIUM_ENDPOINT
                    } else if (selectMenu.value === "zotero") {
                        selectedEndpoint = ZOTERO_ENDPOINT
                    }
                })
            // 2) Hook browser vendor selection menu into our modal dialog
            $('.options-menu').append($browserSelect)
        }

        function import_bookmarks() {
            console.log("Import bookmarks method for the following vendor", selectedEndpoint)
            dm4c.get_plugin("de.deepamehta.files")
                .open_upload_dialog(selectedEndpoint, function(response) {
                    console.log("Imported Bookmarks", response)
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
