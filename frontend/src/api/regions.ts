import { get } from './http'

export interface Region {
  code: string
  parentCode: string | null
  level: number
  name: string
}

export interface RegionOption extends Region {
  children?: RegionOption[]
}

/**
 * 按上级编码查询下一级区域；parentCode 为空时返回省级。
 */
export async function listRegions(parentCode?: string): Promise<Region[]> {
  return get<Region[]>('/regions', parentCode ? { parentCode } : undefined)
}

let treeCache: RegionOption[] | null = null

/**
 * 在已加载的树中按县级编码反查省 / 市 / 县路径，用于回显和筛选标签。
 */
export function findRegionPath(countyCode: string): RegionOption[] | null {
  if (!countyCode || !treeCache) return null
  for (const province of treeCache) {
    for (const city of province.children ?? []) {
      for (const county of city.children ?? []) {
        if (county.code === countyCode) return [province, city, county]
      }
    }
  }
  return null
}

/**
 * 加载省 / 市 / 县完整树。
 * 区域是字典级数据（当前仅安徽省 / 淮南市），一次性加载后全项目缓存，
 * 保证编辑回显时级联能直接定位到县一级。
 */
export async function loadRegionTree(): Promise<RegionOption[]> {
  if (treeCache) return treeCache
  const provinces: RegionOption[] = await listRegions()
  for (const province of provinces) {
    const cities: RegionOption[] = await listRegions(province.code)
    for (const city of cities) {
      city.children = await listRegions(city.code)
    }
    province.children = cities
  }
  treeCache = provinces
  return treeCache
}
