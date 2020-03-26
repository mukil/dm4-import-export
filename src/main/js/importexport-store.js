const state = {
  bookmarksDialogVisible: false,
  jsonDialogVisible: false,
  topic: undefined
}

const actions = {

  openBookmarksImportDialog({state}, topic) {
    state.bookmarksDialogVisible = true
    state.topic = topic
  },
  closeBookmarksImportDialog() {
    state.bookmarksDialogVisible = false
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
