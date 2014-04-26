dm4c.add_plugin("net.abriraqui.import-export", funtion(){

    var topicmap_menu =dm4c.get_plugin("de.deepamehta.topicmaps").get_topicmap_menu()
    topicmap_menu.add_item({
	label: "Import Topicmap",
	handler: import_topicmap
    })

    function(){
	alert("Import topicmap XX")
    }


})