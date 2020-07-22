export default function ({dm5, axios}) {

  const state = {
    exportDialogVisible: false,
    jsonDialogVisible: false,
    topicmapTopicId: undefined,
    exportFileType: 'json'
  }

  const actions = {

    openExportDialog(context) {
      state.exportDialogVisible = true
      state.topicmapTopicId = context.rootState.topicmaps.topicmap.id
    },
    exportTopicmap() {
      // Todo: Utilize "topicmapId"
      console.log("doExportTopicmap Action", state.topicmapTopicId, "type", state.exportFileType)
      // Utilizes "dmx_topicmap_id" cookie
      axios.post('/import-export/topicmap/'+state.topicmapTopicId+'/export/' + state.exportFileType)
        .then(response => {
          console.log("exportedTopicmap", response.data, state.exportFileType)
          const fileTopic = response.data
          this.dispatch("revealTopic", { topic: new dm5.Topic(fileTopic), select: true })
          this.dispatch("closeExportDialog")
        })
    },
    closeExportDialog() {
      state.exportDialogVisible = false
    },
    openJsonImportDialog({state}, topic) {
      state.jsonDialogVisible = true
      state.topic = topic
    },
    closeJsonImportDialog() {
      state.jsonDialogVisible = false
    }

  }

  return { state, actions }
}

