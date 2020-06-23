<template>

    <el-dialog :visible="exportTopicmapDialog" :close="listenClose" title="Export Topicmap"
        width="400px" :show-close="false">
      <div class="block">
        <el-radio v-model="exportFileType" label="json">JSON</el-radio>
        <el-radio v-model="exportFileType" label="svg">SVG</el-radio>
      </div>
      <span slot="footer" class="dialog-footer">
        <el-button @click="closeDialog">Cancel</el-button>
        <el-button type="primary" @click="exportTopicmap">Export Topicmap</el-button>
      </span>
    </el-dialog>

</template>

<script>
export default {

  inject: {
    dm5: "dm5",
    http: 'axios'
  },

  data () {
    return {
        entries: [],
        selectedItem: '',
        exportFileType: 'chromium'
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
    showExportTopicmapDialog() {
        this.$store.dispatch("openExportDialog")
    },
    exportTopicmap() {
      // Utilizes "dmx_topicmap_id" cookie
      console.log("exportTopicmap type=>", this.exportFileType)
      this.http.post('/import-export/topicmap/export/json')
        .then(response => {
          const fileTopic = response.data
          this.$store.dispatch("revealTopic", { topic: new this.dm5.Topic(fileTopic), select: true })
        })
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

<style>
</style>

