<template>
  <section>
    <h2>群组聊天</h2>
    <el-alert :title="notice || status" :closable="false" />
    <el-space wrap>
      <el-input v-model="groupName" maxlength="100" placeholder="新群名称" />
      <el-button :disabled="groupBusy" @click="create">创建群组</el-button>
      <el-button :disabled="groupBusy" @click="loadGroups(true)">刷新群组</el-button>
      <el-button v-if="hasMoreGroups" :disabled="groupBusy" @click="loadGroups(false)">加载更多群组</el-button>
    </el-space>
    <el-select v-model="chosen" placeholder="选择群组，未加入的群会先加入" @change="choose">
      <el-option v-for="g in groups" :key="g.id" :value="g.id" :label="`${g.name} · ${g.memberCount}人 · ${g.joined ? '已加入' : '可加入'}`" />
    </el-select>
    <p v-if="historyLoading">正在补齐历史，完成后可发送…</p>
    <article v-for="m in visibleMessages" :key="keyOf(m)" class="bubble">
      <b>{{ m.senderName || m.senderId }}</b><small> · {{ m.sentAt }}</small>
      <p>{{ m.content }}</p><small>{{ labels[m.deliveryStatus] || m.deliveryStatus }}</small>
      <el-button v-if="m.senderId===userId && m.deliveryStatus==='failed'" :disabled="!canSend" @click="retry(m)">使用原编号重试</el-button>
    </article>
    <el-input v-model="input" type="textarea" :rows="3" maxlength="2000" show-word-limit placeholder="输入消息" @keydown="keydown" />
    <el-button type="primary" :disabled="!canSend || !input.trim()" @click="send">发送</el-button>
    <el-button :disabled="!selected || !ready" @click="subscribe">重新补齐当前群历史</el-button>
  </section>
</template>
<script>
import { getGroups, createGroup, joinGroup, getHistory } from '../api/chat'
import { messageId, messageKey, mergeMessages } from '../utils/chat'
import { expireSession } from '../api/request'
export default {
  name: 'GroupChat',
  data: () => ({ groups: [], cursor: 0, hasMoreGroups: true, groupBusy: false,
    groupName: '', chosen: null, selected: null, input: '', messages: [],
    ready: false, subscribed: false, historyLoading: false, status: '尚未连接', notice: '',
    labels: {sending:'发送中',pending:'已接收，正在保存',saved:'已保存',failed:'未确认或未保存成功，可重试'} }),
  computed: {
    userId() { return this.$store.state.userInfo?.id },
    visibleMessages() { return this.messages.filter(m=>m.groupId===this.selected) },
    canSend() { return this.ready && this.subscribed && !this.historyLoading && !!this.selected }
  },
  created() {
    this.ws=null; this.generation=0; this.selection=0; this.subscription=''
    this.retryCount=0; this.stopped=false; this.timers=new Map(); this.outbox=[]
    this.cacheKey=`work-admin.outbox.${this.userId}`
    try {
      const cached=JSON.parse(sessionStorage.getItem(this.cacheKey) || '[]')
      this.outbox=Array.isArray(cached) ? cached.filter(m=>m.senderId===this.userId && typeof m.content==='string').slice(-100) : []
      this.messages=this.outbox.map(m=>({...m}))
    } catch { this.outbox=[] }
    this.loadGroups(true); this.connect()
  },
  mounted() { window.addEventListener('pagehide',this.shutdown) },
  beforeUnmount() { this.shutdown(); window.removeEventListener('pagehide',this.shutdown) },
  watch: { '$store.state.token'() { this.shutdown() } },
  methods: {
    keyOf:messageKey,
    persist() {
      try { sessionStorage.setItem(this.cacheKey,JSON.stringify(this.outbox)) }
      catch { this.notice='本地缓存不可用，刷新后未确认消息可能需要手动核对' }
    },
    async loadGroups(reset=true) {
      if(this.groupBusy) return
      this.groupBusy=true
      try {
        const page=await getGroups(reset ? 0 : this.cursor)
        const map=new Map((reset ? [] : this.groups).map(g=>[g.id,g]))
        page.forEach(g=>map.set(g.id,g)); this.groups=[...map.values()]
        if(page.length) this.cursor=page[page.length-1].id; else if(reset) this.cursor=0
        this.hasMoreGroups=page.length===50
      } catch {} finally { this.groupBusy=false }
    },
    async create() {
      const name=this.groupName.trim()
      if(!name || this.groupBusy) return
      this.groupBusy=true
      try {
        const group=await createGroup(name)
        this.groups.push({...group,joined:true,memberCount:1}); this.groupName=''
        this.chosen=group.id; await this.choose(group.id)
      } catch {} finally { this.groupBusy=false }
    },
    async choose(id) {
      const selection=++this.selection
      this.selected=null; this.subscribed=false; this.historyLoading=false; this.notice=''
      try {
        const group=this.groups.find(g=>g.id===id)
        if(!group?.joined) {
          await joinGroup(id)
          if(selection!==this.selection || this.stopped) return
          if(group) { group.joined=true; group.memberCount+=1 }
        }
        if(selection!==this.selection || this.stopped) return
        this.selected=id
        if(this.ready) this.subscribe()
      } catch { if(selection===this.selection) this.notice='加入失败，请重新选择或刷新群列表' }
    },
    write(frame) {
      if(this.ws?.readyState!==WebSocket.OPEN) return false
      this.ws.send(JSON.stringify(frame)); return true
    },
    connect() {
      if(this.stopped || !this.$store.state.token) return
      const generation=++this.generation
      const url=new URL('/ws/chat',window.location.href)
      url.protocol=location.protocol==='https:' ? 'wss:' : 'ws:'
      const socket=new WebSocket(url); this.ws=socket
      this.ready=false; this.subscribed=false; this.status='连接中…'
      this.lastData=Date.now()
      const authTimer=setTimeout(()=>socket.close(),12000)
      socket.onopen=()=> {
        if(generation!==this.generation) return
        this.write({type:'auth',token:this.$store.state.token})
      }
      socket.onmessage=event=> {
        if(generation!==this.generation || this.stopped) return
        this.lastData=Date.now()
        try {
          const frame=JSON.parse(event.data)
          if(frame.type==='ready') {
            clearTimeout(authTimer); this.ready=true; this.retryCount=0; this.status='连接正常'
            clearInterval(this.heartbeat)
            this.heartbeat=setInterval(()=> {
              if(Date.now()-this.lastData>55000) socket.close()
              else this.write({type:'ping'})
            },25000)
            if(this.selected) this.subscribe()
          } else if(frame.type==='subscribed') {
            if(frame.requestId===this.subscription && frame.groupId===this.selected) {
              this.subscribed=true; this.syncHistory(frame.groupId,frame.requestId,generation)
            }
          } else if(['message','ack','saved','save_failed'].includes(frame.type)) {
            this.receive(frame.message)
          } else if(frame.type==='error') this.frameError(frame)
        } catch { this.notice='收到无效消息，请检查后端协议' }
      }
      socket.onerror=()=> { if(generation===this.generation) this.status='连接异常，等待重连' }
      socket.onclose=async event=> {
        clearTimeout(authTimer)
        if(generation!==this.generation || this.stopped) return
        clearInterval(this.heartbeat); this.ready=false; this.subscribed=false
        this.status='连接已断开'
        // 1008 可能是登录或权限失效；先向 HTTP 权限接口核实，避免无限重连。
        if(event.code===1008) {
          try { await this.$store.dispatch('refreshAccess') } catch {}
          if(generation!==this.generation || this.stopped) return
          if(!this.$store.state.token || !this.$store.getters.can('chat:access')) {
            this.notice='登录或群聊权限已失效'; this.stopped=true; return
          }
        }
        const delay=Math.min(1000*2**this.retryCount++,15000)
        this.status=`连接已断开，${delay/1000} 秒后重连`
        this.reconnect=setTimeout(()=>this.connect(),delay)
      }
    },
    subscribe() {
      if(!this.ready || !this.selected) return
      this.subscribed=false; this.historyLoading=true
      this.subscription=messageId()
      this.write({type:'subscribe',groupId:this.selected,requestId:this.subscription})
    },
    async syncHistory(groupId,requestId,generation) {
      this.historyLoading=true
      const current=()=>!this.stopped && this.selected===groupId && this.subscription===requestId && this.generation===generation
      try {
        let cursor=0, more=true
        while(more && current()) {
          const page=await getHistory(groupId,cursor)
          if(!current()) return
          page.items.forEach(m=>this.receive(m))
          if(page.hasMore && page.nextAfterId<=cursor) throw new Error('历史游标没有前进')
          cursor=page.nextAfterId; more=page.hasMore
        }
        if(!current()) return
        // 先核对历史与订阅推送，再重发仍没有确认的草稿；明确失败的草稿等手动重试。
        this.outbox.filter(m=>m.groupId===groupId && m.deliveryStatus==='sending').forEach(m=>this.transmit(m))
        this.notice=''
      } catch { if(current()) { this.subscribed=false; this.notice='历史补齐失败，请点击重新补齐当前群历史' } }
      finally { if(current()) this.historyLoading=false }
    },
    receive(message) {
      if(!message || !message.clientMessageId) return
      this.messages=mergeMessages(this.messages,message)
      const key=messageKey(message)
      clearTimeout(this.timers.get(key)); this.timers.delete(key)
      if(message.senderId===this.userId) {
        this.outbox=this.outbox.filter(m=>messageKey(m)!==key)
        const actual=this.messages.find(m=>messageKey(m)===key)
        if(actual?.deliveryStatus==='failed') this.outbox.push({...actual})
        this.persist()
      }
    },
    frameError(frame) {
      this.notice=frame.message || '消息操作失败'
      if(frame.status===401) { expireSession(this.$store.state.token); this.shutdown(); return }
      if(frame.requestId===this.subscription) { this.subscribed=false; this.historyLoading=false }
      const draft=this.outbox.find(m=>m.clientMessageId===frame.requestId)
      if(draft) this.receive({...draft,deliveryStatus:'failed'})
      if(frame.status===403) {
        this.$store.dispatch('refreshAccess').then(()=> {
          if(!this.$store.getters.can('chat:access')) this.shutdown()
        }).catch(()=>{})
      }
    },
    transmit(message) {
      if(!this.ready) return
      const key=messageKey(message)
      if(!this.write({type:'send',groupId:message.groupId,content:message.content,
        clientMessageId:message.clientMessageId,requestId:message.clientMessageId})) return
      clearTimeout(this.timers.get(key))
      this.timers.set(key,setTimeout(()=> {
        const current=this.messages.find(m=>messageKey(m)===key)
        if(current?.deliveryStatus==='sending') this.receive({...current,deliveryStatus:'failed'})
      },12000))
    },
    keydown(event) {
      if(event.key==='Enter' && !event.shiftKey && !event.isComposing && event.keyCode!==229) { event.preventDefault(); this.send() }
    },
    send() {
      const content=this.input.trim()
      if(!this.canSend || !content || content.length>2000) return
      if(this.outbox.length>=100) return this.$message.warning('请先处理未确认的消息')
      const message={id:null,groupId:this.selected,senderId:this.userId,
        senderName:this.$store.state.userInfo?.name || this.$store.state.userInfo?.account,
        content,clientMessageId:messageId(),sentAt:new Date().toISOString(),deliveryStatus:'sending'}
      this.messages=mergeMessages(this.messages,message)
      this.outbox.push(message); this.persist(); this.input=''; this.transmit(message)
    },
    retry(message) {
      if(!this.canSend) return
      const draft={...message,deliveryStatus:'sending'}
      const key=messageKey(draft)
      this.messages=this.messages.map(m=>messageKey(m)===key ? draft : m)
      this.outbox=this.outbox.filter(m=>messageKey(m)!==key); this.outbox.push(draft)
      this.persist(); this.transmit(draft)
    },
    shutdown() {
      this.stopped=true; ++this.generation; this.ready=false; this.subscribed=false
      clearTimeout(this.reconnect); clearInterval(this.heartbeat)
      this.timers.forEach(timer=>clearTimeout(timer)); this.timers.clear()
      this.ws?.close(); this.ws=null
    }
  }
}
</script>
<style scoped>
.bubble { background:white; border:1px solid #e5e7eb; margin:12px 0; padding:12px; border-radius:8px; }
p { white-space:pre-wrap; overflow-wrap:anywhere; } small { color:#777; }
.el-space, .el-select, .el-textarea { margin:8px 0; }
</style>