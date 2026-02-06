<template>
  <div id="app">
    <MultiWindowLayout />
  </div>
</template>

<script>
import MultiWindowLayout from './components/MultiWindowLayout.vue'

export default {
  name: 'App',
  components: {
    MultiWindowLayout
  }
}
</script>

<style>
html,
body {
  margin: 0;
  padding: 0;
  width: 100%;
  height: 100%;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial;
  background-color: #f0f2f5;
  overflow: hidden;
}

#app {
  width: 100%;
  height: 100%;
  position: absolute;
  top: 0;
  left: 0;
}

*,
*:before,
*:after {
  box-sizing: border-box;
}
</style>