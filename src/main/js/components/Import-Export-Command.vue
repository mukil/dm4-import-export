<template>
    <div class="dm5-import-export-commands" v-if="loggedIn">
      <el-button type="text" class="fa fa-save" title="Export Topicmap" @click="showExportTopicmapDialog">
      </el-button>
    </div>
</template>

<script>
export default {

  inject: {
    dm5: "dm5",
    http: 'axios'
  },

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

<style>
</style>
