export default ({store, dm5, axios, Vue}) => ({

  components: [{
    comp: require('./components/Import-Export-Menu').default,
    mount: 'toolbar-left'
  }]

})
