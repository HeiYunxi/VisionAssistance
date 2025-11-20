import router from '../../router/index'
export default {
  // 路由跳转
  changeView(url, queryParams) {
    router.push({
      path: url,
      query: queryParams,
    })
  }
}
