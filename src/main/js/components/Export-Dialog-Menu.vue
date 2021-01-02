<template>
  <el-dialog :visible="exportTopicmapDialog" :close="listenClose" title="Export Topicmap"
      width="400px" :show-close="false">
    <div class="block">
      <el-radio v-model="exportFileType" label="json">JSON</el-radio>
      <el-radio v-model="exportFileType" label="svg">SVG</el-radio>
    </div>
    <span slot="footer" class="dialog-footer">
      <el-button @click="closeDialog">Cancel</el-button>
      <el-button type="primary" @click="doExportTopicmap">Export Topicmap</el-button>
    </span>
  </el-dialog>
</template>

<script>
export default {

  data () {
    return {
      exportFileType: 'json'
    }
  },

  computed: {
    exportTopicmapDialog () {
      return this.$store.state.importexport.exportDialogVisible
    },
    file () {
      return this.$store.state.importexport.topic
    },
    loggedIn () {
      return (this.$store.state.accesscontrol.username)
    }
  },

  methods: {
    doExportTopicmap() {
      // copy dialog state into plugin store
      this.$store.state.importexport.exportFileType = this.exportFileType
      this.$store.dispatch("exportTopicmap")
    },
    listenClose() {
      this.$store.dispatch("closeExportDialog")
    },
    closeDialog() {
      this.$store.dispatch("closeExportDialog")
    }
  }
}
</script>
