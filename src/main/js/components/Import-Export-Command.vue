<template>

    <div class="dm5-import-export-commands" v-if="loggedIn">
      <el-button type="text" class="fa fa-save" title="Export Topicmap" @click="exportTopicmap">
      </el-button>
    </div>

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
