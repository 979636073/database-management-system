<template>
  <div class="monaco-editor-container" ref="editorContainer"></div>
</template>

<script>
import * as monaco from 'monaco-editor';

export default {
  name: 'SqlEditor',
  props: {
    value: { type: String, default: '' },
    language: { type: String, default: 'sql' },
    readOnly: { type: Boolean, default: false },
    theme: { type: String, default: 'vs' } // 'vs' (light), 'vs-dark'
  },
  data() {
    return {
      editor: null
    };
  },
  watch: {
    value(newValue) {
      if (this.editor && newValue !== this.editor.getValue()) {
        this.editor.setValue(newValue);
      }
    },
    language(newVal) {
      if (this.editor) {
        monaco.editor.setModelLanguage(this.editor.getModel(), newVal);
      }
    }
  },
  mounted() {
    this.initEditor();
    window.addEventListener('resize', this.handleResize);
  },
  beforeDestroy() {
    if (this.editor) {
      this.editor.dispose();
    }
    window.removeEventListener('resize', this.handleResize);
  },
  methods: {
    initEditor() {
      this.editor = monaco.editor.create(this.$refs.editorContainer, {
        value: this.value,
        language: this.language,
        theme: this.theme,
        readOnly: this.readOnly,
        automaticLayout: true, // 自动适应容器大小
        fontSize: 14,
        minimap: { enabled: false }, // 关闭右侧预览图
        scrollBeyondLastLine: false,
        lineNumbers: 'on',
        roundedSelection: false,
        scrollBeyondLastLine: false,
      });

      // 监听内容变化，同步给父组件
      this.editor.onDidChangeModelContent(() => {
        const value = this.editor.getValue();
        this.$emit('input', value);
        this.$emit('change', value);
      });
    },
    handleResize() {
      if (this.editor) {
        this.editor.layout();
      }
    }
  }
};
</script>

<style scoped>
.monaco-editor-container {
  width: 100%;
  height: 100%;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  overflow: hidden;
}
</style>