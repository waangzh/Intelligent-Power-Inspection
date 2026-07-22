import { describe, expect, it } from 'vitest'
import {
  DEFAULT_GEO_CENTER,
  defaultGeoCenter,
  isSuspectedCoordinateSwap,
  isValidGeoCoordinate,
} from './geoCoordinate'

describe('geoCoordinate', () => {
  it('accepts valid boundary coordinates', () => {
    expect(isValidGeoCoordinate({ lat: -90, lng: 180 })).toBe(true)
    expect(isValidGeoCoordinate({ lat: 90, lng: -180 })).toBe(true)
  })

  it('rejects invalid or non-finite coordinates', () => {
    expect(isValidGeoCoordinate({ lat: 90.0001, lng: 120 })).toBe(false)
    expect(isValidGeoCoordinate({ lat: 30, lng: 180.0001 })).toBe(false)
    expect(isValidGeoCoordinate({ lat: Number.NaN, lng: 120 })).toBe(false)
  })

  it('only flags unambiguous latitude overflow as a suspected swap', () => {
    expect(isSuspectedCoordinateSwap({ lat: 120.1551, lng: 30.2741 })).toBe(true)
    expect(isSuspectedCoordinateSwap({ lat: 30.2741, lng: 120.1551 })).toBe(false)
    expect(isSuspectedCoordinateSwap({ lat: 120, lng: 100 })).toBe(false)
  })

  it('returns an independent default center object', () => {
    const center = defaultGeoCenter()
    center.lat = 0
    expect(DEFAULT_GEO_CENTER.lat).toBe(30.2741)
  })
})
