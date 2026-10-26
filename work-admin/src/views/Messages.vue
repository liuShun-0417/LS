<template>
  <section>
    <h2>AI 流式对话</h2>
    <el-alert title="实际模型由后端配置；这里只展示返回的模型标识。未完成的回答不会带入下一轮上下文。" :closable="false" />
    <article v-for="(turn,index) in turns" :key="index" class="turn">
      <b>我：</b><p>{{ turn.question }}</p>
      <b>{{ turn.model || 'AI' }}：</b><p>{{ turn.answer || '等待回复…' }}</p>
      <small>{{ turn.note }}</small>
      <el-button v-if="index===turns.length-1 && ['failed','stopped'].includes(turn.status)" :disabled="busy" @click="generate(turn)">重新生成</el-button>
    </article>
    <el-select v-model="effort" :disabled="busy">
      <el-option v-for="v in ['low','medium','high','xhigh','max']" :key="v" :label="v" :value="v" />
    </el-select>
    <el-input v-model="input" type="textarea" :rows="4" maxlength="2000" show-word-limit
      placeholder="Enter 发送，Shift + Enter 换行" @keydown="keydown" />
    <el-space>
      <el-button type="primary" :disabled="busy || !input.trim()" @click="send">发送</el-button>
      <el-button v-if="busy" @click="stop">停止生成</el-button>
      <el-button :disabled="busy" @click="turns=[]">清空对话</el-button>
    </el-space>
  </section>
</template>
<script>
import { streamMessage } from '../api/message'
import { buildHistory } from '../utils/sse'
export default {
  name: 'AiMessages',
  data: () => ({ turns: [], input: '', effort: 'medium', busy: false }),
  created() { this.controller = null },
  mounted() { window.addEventListener('pagehide', this.stop) },
  beforeUnmount() { this.stop(); window.removeEventListener('pagehide', this.stop) },
  watch: { '$store.state.token'() { this.stop() } },
  methods: {
    stop() { this.controller?.abort() },
    keydown(event) {
      if (event.key==='Enter' && !event.shiftKey && !event.isComposing && event.keyCode!==229) {
        event.preventDefault(); this.send()
      }
    },
    send() {
      const question=this.input.trim()
      if(this.busy || !question || question.length>2000) return
      const turn={ question, answer:'', status:'pending', note:'', model:'', effort:this.effort,
        history:buildHistory(this.turns, question) }
      this.turns.push(turn); this.input=''
      // 取得 Vue 数组中的响应式代理，后续增量修改才能更新视图。
      this.generate(this.turns[this.turns.length-1])
    },
    async generate(turn) {
      if(this.busy) return
      this.busy=true; turn.answer=''; turn.status='pending'; turn.note='正在连接…'
      const controller=new AbortController(); this.controller=controller
      try {
        await streamMessage({message:turn.question,history:turn.history,effort:turn.effort}, {
          signal:controller.signal,
          onEvent:(event,data)=>{
            if(event==='start') { turn.model=data.model; turn.note='AI 正在思考…' }
            if(event==='delta') {
              if(typeof data.text!=='string') throw new Error('文本增量格式错误')
              turn.answer+=data.text; turn.note='正在输出…'
            }
          }
        })
        if(!turn.answer) throw new Error('未收到文本回答')
        turn.status='completed'; turn.note='回复完成'
      } catch(error) {
        const stopped=controller.signal.aborted
        turn.status=stopped ? 'stopped' : 'failed'
        turn.note=stopped ? '已停止' : error.message
      } finally { this.controller=null; this.busy=false }
    }
  }
}
</script>
<style scoped>
.turn { background:white; margin:16px 0; padding:16px; border-radius:8px; }
p { white-space:pre-wrap; overflow-wrap:anywhere; } small { color:#777; }
.el-textarea { margin:12px 0; }
</style>