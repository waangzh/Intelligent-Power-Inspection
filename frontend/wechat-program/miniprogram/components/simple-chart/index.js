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
      const values = arr.map((x) => Number(x.value) || 0)
      const max = Math.max(...values, 0)
      this.setData({
        normalized: arr.map((x, i) => {
          const value = values[i]
          let pct = 0
          if (value > 0 && max > 0) {
            pct = Math.max(8, Math.round((value / max) * 100))
          }
          return { ...x, value, pct }
        }),
      })
    },
  },
})
