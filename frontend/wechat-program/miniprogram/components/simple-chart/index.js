Component({
  properties: {
    type: { type: String, value: 'bar' },
    data: { type: Array, value: [] },
    value: { type: Number, value: 0 },
    title: { type: String, value: '' },
  },
  data: { normalized: [] },
  observers: {
    data(d) {
      const arr = d || []
      const max = Math.max(...arr.map((x) => x.value), 1)
      this.setData({
        normalized: arr.map((x) => ({
          ...x,
          pct: Math.max(8, Math.round((x.value / max) * 100)),
        })),
      })
    },
  },
})
