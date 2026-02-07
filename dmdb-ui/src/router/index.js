import Vue from 'vue'
import VueRouter from 'vue-router'
import MainLayout from '../views/MainLayout.vue'

Vue.use(VueRouter)

const routes = [
  {
    path: '/',
    name: 'MainLayout',
    component: MainLayout
  },
//   {
//     path: '/users',
//     name: 'UserList',
//     component: () => import('@/views/UserList.vue')
// }
]

const router = new VueRouter({
  routes
})

export default router
