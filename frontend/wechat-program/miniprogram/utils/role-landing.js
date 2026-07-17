function getRoleLandingPath(role) {
  if (role === 'DISPATCHER') return '/pages/workorders/index'
  return '/pages/dashboard/index'
}

module.exports = { getRoleLandingPath }
