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
    theme: { type: String, default: 'vs' }
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
    // ... 其他 watch 保持不变
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
        automaticLayout: true,
        fontSize: 14,
        minimap: { enabled: false },
        scrollBeyondLastLine: false,
        lineNumbers: 'on',
        contextmenu: true, // 启用右键菜单
      });

      this.editor.onDidChangeModelContent(() => {
        const value = this.editor.getValue();
        this.$emit('input', value);
        this.$emit('change', value);
      });

      // 注册快捷键 F9 运行 (传给父组件处理)
      this.editor.addCommand(monaco.KeyCode.F9, () => {
        this.$emit('execute');
      });
    },

    // 【新增】获取当前选中的文本，如果没有选中，则返回全部
    getSelectionOrAll() {
      const selection = this.editor.getSelection();
      const selectedText = this.editor.getModel().getValueInRange(selection);
      if (!selectedText || selectedText.trim() === '') {
        return this.editor.getValue();
      }
      return selectedText;
    },

    // 【新增】插入内容到光标处
    insertContent(text) {
      const selection = this.editor.getSelection();
      const range = new monaco.Range(selection.startLineNumber, selection.startColumn, selection.endLineNumber, selection.endColumn);
      const id = { major: 1, minor: 1 };
      const op = { identifier: id, range: range, text: text, forceMoveMarkers: true };
      this.editor.executeEdits("my-source", [op]);
      this.editor.focus();
    },

    // 【新增】触发格式化
    formatDocument() {
      this.editor.getAction('editor.action.formatDocument').run();
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
  /* 移除边框，由父容器控制 */
  overflow: hidden;
}
</style>