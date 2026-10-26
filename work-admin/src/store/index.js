import { createStore } from 'vuex'
const KEY = 'work-admin.session.v2'
function readSession() {
  try {
    const saved = localStorage.getItem(KEY)
    const value = saved === null
      ? { token: localStorage.getItem('token'), user: JSON.parse(localStorage.getItem('userInfo') || 'null') }
      : JSON.parse(saved)
    return value && typeof value.token === 'string' && value.token
      ? value : { token: '', user: null }
  } catch { return { token: '', user: null } }
}
function persist(state) {
  localStorage.setItem(KEY, JSON.stringify(state.token ? { token: state.token, user: state.userInfo } : null))
  localStorage.removeItem('token')
  localStorage.removeItem('userInfo')
}
const session = readSession()
const emptyAccess = () => ({ role: null, permissions: [], menus: [] })
export default createStore({
  state: { token: session.token, userInfo: session.user, access: emptyAccess() },
  getters: {
    can: state => permission => state.access.permissions.includes('*') || state.access.permissions.includes(permission)
  },
  mutations: {
    setSession(state, result) {
      state.token = result.tokenValue; state.userInfo = result.user
      state.access = emptyAccess(); persist(state)
    },
    setUserInfo(state, user) { state.userInfo = user; persist(state) },
    setAccess(state, access) { state.access = access },
    clearUserInfo(state) {
      state.token = ''; state.userInfo = null; state.access = emptyAccess(); persist(state)
    }
  },
  actions: {
    async refreshAccess({ state, commit }) {
      const token = state.token
      // 延迟导入，避免 store、request、api 初始化时相互依赖。
      const [{ getAccess }, { getCurrentUser }] = await Promise.all([import('../api/access'), import('../api/auth')])
      const [access, user] = await Promise.all([getAccess(), getCurrentUser()])
      if (token !== state.token) throw new Error('登录账号已变更')
      commit('setAccess', access); commit('setUserInfo', user)
      return access
    }
  }
})
export function installSessionSync(store) {
  const synchronize = () => {
    if (readSession().token !== store.state.token) window.location.reload()
  }
  window.addEventListener('storage', event => {
    if (event.storageArea === localStorage && (event.key === KEY || event.key === null)) synchronize()
  })
  window.addEventListener('pageshow', event => { if (event.persisted) window.location.reload() })
}