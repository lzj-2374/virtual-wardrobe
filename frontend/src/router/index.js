import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录', standalone: true }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/Register.vue'),
    meta: { title: '注册', standalone: true }
  },
  {
    // 落地页：整屏视频，无侧边栏 / 无顶栏
    path: '/welcome',
    name: 'Landing',
    component: () => import('@/views/Landing.vue'),
    meta: { requiresAuth: true, title: '虚拟衣柜', standalone: true }
  },
  {
    // 浏览态：只看不改
    path: '/',
    component: () => import('@/components/Layout.vue'),
    meta: { requiresAuth: true },
    children: [
      { path: '', redirect: '/welcome' },
      { path: 'home', redirect: '/ai' },
      { path: 'ai', name: 'AiAssistant', component: () => import('@/views/AiAssistant.vue'), meta: { title: 'AI 穿搭助手' } },
      { path: 'wardrobe', name: 'WardrobeView', component: () => import('@/views/WardrobeView.vue'), meta: { title: '我的衣柜' } },
      // readOnly：浏览态只读，衣物详情不出现编辑 / 删除入口
      { path: 'wardrobe/:id', name: 'ClothingDetail', component: () => import('@/views/ClothingDetail.vue'), meta: { title: '衣物详情', readOnly: true } },
      { path: 'outfits', name: 'OutfitsView', component: () => import('@/views/OutfitsView.vue'), meta: { title: '我的搭配' } },
      { path: 'profile', name: 'Profile', component: () => import('@/views/Profile.vue'), meta: { title: '个人中心' } }
    ]
  },
  {
    // 工作台：本人衣柜的增删改与统计，独立布局
    path: '/workspace',
    component: () => import('@/components/WorkspaceLayout.vue'),
    meta: { requiresAuth: true, title: '工作台' },
    children: [
      { path: '', redirect: '/workspace/wardrobe' },
      { path: 'wardrobe', name: 'Wardrobe', component: () => import('@/views/Wardrobe.vue'), meta: { title: '衣物管理' } },
      { path: 'wardrobe/add', name: 'ClothingAdd', component: () => import('@/views/ClothingAdd.vue'), meta: { title: '添加衣物' } },
      { path: 'wardrobe/:id', name: 'WorkspaceClothing', component: () => import('@/views/ClothingDetail.vue'), meta: { title: '衣物维护', readOnly: false } },
      { path: 'outfits', name: 'Outfits', component: () => import('@/views/Outfits.vue'), meta: { title: '搭配管理' } },
      { path: 'outfits/edit/:id?', name: 'OutfitEdit', component: () => import('@/views/OutfitEdit.vue'), meta: { title: '搭配编辑' } },
      { path: 'stats', name: 'Statistics', component: () => import('@/views/Home.vue'), meta: { title: '数据统计' } }
    ]
  },
  {
    path: '/admin',
    component: () => import('@/components/AdminLayout.vue'),
    meta: { requiresAuth: true, requiresAdmin: true, title: '管理端' },
    children: [
      { path: '', redirect: '/admin/dashboard' },
      { path: 'dashboard', name: 'AdminDashboard', component: () => import('@/views/admin/AdminDashboard.vue'), meta: { title: '数据报表' } },
      { path: 'users', name: 'AdminUsers', component: () => import('@/views/admin/AdminUsers.vue'), meta: { title: '用户管理' } },
      { path: 'export', name: 'AdminExport', component: () => import('@/views/admin/AdminExport.vue'), meta: { title: '数据导出' } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  // 首页是整页滚动的，切走时回到顶部，避免残留滚动位置
  scrollBehavior(to, from, savedPosition) {
    return savedPosition || { top: 0 }
  }
})

router.beforeEach((to) => {
  const token = localStorage.getItem('token')
  if (to.meta.requiresAuth && !token) {
    return { name: 'Login', query: { redirect: to.fullPath } }
  }
  // 管理端权限：仅管理员（role=1）可进入
  if (to.meta.requiresAdmin) {
    const info = JSON.parse(localStorage.getItem('userInfo') || 'null')
    if (info?.role !== 1) {
      return { name: 'AiAssistant' }
    }
  }
  if ((to.name === 'Login' || to.name === 'Register') && token) {
    return { name: 'AiAssistant' }
  }
  document.title = to.meta.title ? `${to.meta.title} · 虚拟衣柜` : '虚拟衣柜'
})

export default router
