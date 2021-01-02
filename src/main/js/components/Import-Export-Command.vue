<template>
  <div class="dmx-import-export-commands" v-if="loggedIn">
    <el-button type="text" class="fa fa-save" title="Export Topicmap"   @click="showExportTopicmapDialog">
    </el-button>
  </div>
</template>

<script>
export default {
  computed: {
    loggedIn () {
      return (this.$store.state.accesscontrol.username)
    }
  },
  methods: {
    showExportTopicmapDialog() {
      this.$store.dispatch("openExportDialog")
    }
  }
}
</script>
