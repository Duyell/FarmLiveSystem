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

export async function listAllRegions(): Promise<Region[]> {
  return get<Region[]>('/regions/all')
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
 * 一次拉取全部启用区域，在本地组树；全国数据也只发一个请求。
 */
export async function loadRegionTree(): Promise<RegionOption[]> {
  if (treeCache) return treeCache
  const all = await listAllRegions()
  const byParent = new Map<string | null, RegionOption[]>()
  for (const region of all) {
    const node: RegionOption = { ...region, children: [] }
    const list = byParent.get(region.parentCode) ?? []
    list.push(node)
    byParent.set(region.parentCode, list)
  }
  for (const region of all) {
    const node = byParent.get(region.parentCode)?.find((item) => item.code === region.code)
    if (node) node.children = byParent.get(region.code) ?? []
  }
  treeCache = byParent.get(null) ?? []
  return treeCache
}
