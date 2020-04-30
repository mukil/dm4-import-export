const state = {
  exportDialogVisible: false,
  jsonDialogVisible: false,
  topic: undefined
}

const actions = {

  openExportDialog({state}) {
    state.exportDialogVisible = true
    console.log("exportTopicmapsDialog, state", state)
    /** if (topic) {
      state.topic = topic
      console.log("Set File Topic for Bookmarks Import", topic)
    } **/
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

export default {
  state,
  actions
}
