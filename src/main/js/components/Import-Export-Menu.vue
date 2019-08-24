<template>
  <div class="dm5-import-export-commands">
    <el-button type="text" class="fa fa-save" title="Export Topicmap to SVG" @click="exportTopicmapSVG">
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
        selectedItem: ''
    }
  },

  methods: {
    exportTopicmapSVG() {
      // Uses on "dmx_topicmap_id" cookie
      this.http.post('/import-export/topicmap/export/svg')
        .then(response => {
          const fileTopic = response.data
          console.log("Created File Topic", fileTopic)
          this.$store.dispatch("revealTopic", { topic: new this.dm5.Topic(fileTopic), select: true })
        })
    }
  }
}
</script>

<style>
</style>
