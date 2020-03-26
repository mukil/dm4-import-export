<template>

    <div>

        <div class="dm5-import-export-commands">
          <el-button type="text" class="fa fa-save" title="Export Topicmap to SVG" @click="exportTopicmapSVG">
          </el-button>
        </div>

        <div v-if="bookmarksDialogVisible">
            <el-dialog :close="listenClose" visible="true" title="Import Bookmarks"
                width="30%" :show-close="false">
              <span>Select your browser's name</span>
              <div class="block">
                <el-radio v-model="browserName" label="chromium">Chromium</el-radio>
                <el-radio v-model="browserName" label="firefox">Firefox</el-radio>
                <el-radio v-model="browserName" label="zotero">Zotero</el-radio>
              </div>
              <span slot="footer" class="dialog-footer">
                <el-button @click="closeDialog">Cancel</el-button>
                <el-button type="primary" @click="importBookmarks">Import Bookmarks</el-button>
              </span>
            </el-dialog>
        </div>

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
        browserName: 'chromium'
    }
  },

  computed: {
    bookmarksDialogVisible () {
      return this.$store.state.importexport.bookmarksDialogVisible
    },
    file () {
      return this.$store.state.importexport.topic
    }
  },

  methods: {
    importBookmarks() {
      console.log("[Import Export] Bookmarks", this.file.value)
      /** this.http.post('/import-export/import/bookmarks/chromium/' + this.file.id)
        .then(response => {
          console.log("IO Response", response.data)
          this.$store.dispatch("closeResizeDialog")
      }).catch(response => {
          console.warn("[Images] Resize operation failed", response)
          this.$store.dispatch("closeResizeDialog")
      }) **/
    },
    exportTopicmapSVG() {
      // Utilizes "dmx_topicmap_id" cookie
      this.http.post('/import-export/topicmap/export/svg')
        .then(response => {
          const fileTopic = response.data
          this.$store.dispatch("revealTopic", { topic: new this.dm5.Topic(fileTopic), select: true })
        })
    },
    listenClose() {
      this.$store.dispatch("closeResizeDialog")
    },
    closeDialog() {
      this.$store.dispatch("closeResizeDialog")
    }
  }
}
</script>

<style>
</style>
