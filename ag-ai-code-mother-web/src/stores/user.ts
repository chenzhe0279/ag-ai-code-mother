import { defineStore } from 'pinia'
import { getLoginUser, userLogin, userLogout, type LoginUserVO } from '@/api/user'

export const useUserStore = defineStore('user', {
  state: () => ({
    loginUser: null as LoginUserVO | null,
    userLoading: false,
  }),
  actions: {
    async fetchLoginUser() {
      this.userLoading = true
      try {
        const res = await getLoginUser()
        this.loginUser = res
      } catch {
        this.loginUser = null
      } finally {
        this.userLoading = false
      }
    },
    async login(account: string, password: string) {
      const res = await userLogin({ userAccount: account, userPassword: password })
      this.loginUser = res
      return res
    },
    async logout() {
      await userLogout()
      this.loginUser = null
    },
  },
})
