export default ({store, dmx, axios, Vue}) => {

  return {

    init () {
      store.dispatch("registerUploadHandler", {
        mimeTypes: ["application/json", "text/html"],   // mimeType or file name ending in UPPERCASE, Fixme: multiple values, e.g. PNG;JPEG;JPG;
        action: "/import-export/import/topicmap",
        selected: function(file, fileList) {
          if (file.raw.type.indexOf("html") !== -1) {
            store.dispatch("setUploadDialogOptions", {
              options: [
                {value: "chrome", label: "Chromium", action: "/import-export/import/bookmarks/chromium"}, 
                {value: "zotero", label: "Zotero Report" , action: "/import-export/import/bookmarks/zotero-report"}
              ],
              optionsMessage: "What is the origin of this bookmarks file?"
            })
          } else if (file.raw.type.indexOf("json") !== -1) {
            store.dispatch("setUploadDialogOptions", {
              options: [
                {value: "topicmap", label: "DMX Topicmap", action: "/import-export/import/topicmap"},
                {value: "firefox", label: "Firefox Bookmarks", action: "/import-export/import/bookmarks/firefox"}
              ],
              optionsMessage: "What do you want to import?"
            })
          }
        },
        success: function(response, file, fileList) {
          store.dispatch("revealTopicById", response.topic_id)
          this.$notify({
            title: 'Import Successful', type: 'success', message: response.message
          })
          store.dispatch("closeUploadDialog")
        },
        error: function(error, file, fileList) {
          console.warn("[Import Export] file upload error", error)
          this.$notify.error({
            title: 'Import Failed', message: 'Error: ' + JSON.stringify(error)
          })
          store.dispatch("closeUploadDialog")
        }
      })
    },

    storeModule: {
      name: 'importexport',
      module: require('./importexport-store').default
    },

    components: [
      {
        comp: require('./components/Export-Dialog-Menu').default,
        mount: 'webclient'
      },
      {
        comp: require('./components/Import-Export-Command').default,
        mount: 'toolbar-left'
      }
    ]
  }

}
