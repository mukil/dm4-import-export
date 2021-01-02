export default function ({dmx, axios}) {

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
      // Todo: Utilize "topicmapId", Utilizes "dmx_topicmap_id" cookie
      axios.post('/import-export/topicmap/'+state.topicmapTopicId+'/export/' + state.exportFileType)
        .then(response => {
          console.log("exportedTopicmap", response.data, state.exportFileType)
          const fileTopic = response.data
          this.dispatch("revealTopic", { topic: new dmx.Topic(fileTopic), select: true })
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

