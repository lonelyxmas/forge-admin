import dagre from 'dagre'

const DEFAULT_OPTIONS = Object.freeze({
  nodeWidth: 288,
  nodeHeight: 92,
  nodeGap: 112,
  rankGap: 126,
  marginX: 120,
  marginY: 64,
  routeLaneGap: 14,
  routeStub: 30,
  boundsPadding: 80,
})

/**
 * 应用业务流程专用 DAG 布局。
 *
 * 业务节点始终按卡片尺寸布局；条件/审批的端口只影响连线锚点，不能像 BPMN
 * 网关一样把节点缩成菱形。dagre 只负责节点分层，边路径由本文件按端口和
 * 汇合入边独立计算，避免多条结果线重叠或串到错误卡片。
 */
export function layoutBusinessProcess(schema, options = {}) {
  const config = { ...DEFAULT_OPTIONS, ...options }
  const nodes = Array.isArray(schema?.nodes)
    ? schema.nodes.filter(node => typeof node?.id === 'string' && node.id)
    : []
  const nodeById = new Map(nodes.map(node => [node.id, node]))
  const edges = Array.isArray(schema?.edges)
    ? schema.edges.filter(edge => nodeById.has(edge?.source) && nodeById.has(edge?.target))
    : []
  const result = emptyLayout()
  if (!nodes.length)
    return result

  const graph = new dagre.graphlib.Graph({ multigraph: true })
  graph.setGraph({
    rankdir: 'TB',
    ranker: 'network-simplex',
    acyclicer: 'greedy',
    nodesep: config.nodeGap,
    ranksep: config.rankGap,
    marginx: 0,
    marginy: 0,
  })
  graph.setDefaultEdgeLabel(() => ({}))

  nodes.forEach((node) => {
    graph.setNode(node.id, {
      width: config.nodeWidth,
      height: config.nodeHeight,
    })
  })
  edges.forEach((edge, index) => {
    if (edge.source === edge.target)
      return
    graph.setEdge(
      edge.source,
      edge.target,
      { edgeId: edge.id },
      `${edge.id || 'edge'}\u0000${index}`,
    )
  })

  try {
    dagre.layout(graph)
    positionDagreNodes(graph, nodes, result.nodePositions, config)
    alignBranchTargets(nodes, edges, result.nodePositions)
  }
  catch {
    positionFallbackNodes(nodes, result.nodePositions, config)
  }

  const outgoing = groupEdges(edges, 'source')
  const incoming = groupEdges(edges, 'target')
  const pairGroups = groupEdgePairs(edges)

  for (const edge of edges) {
    const source = result.nodePositions.get(edge.source)
    const target = result.nodePositions.get(edge.target)
    if (!source || !target)
      continue

    const sourceEdges = sortOutgoingEdges(
      outgoing.get(edge.source) || [],
      nodeById.get(edge.source),
    )
    const targetEdges = sortIncomingEdges(
      incoming.get(edge.target) || [],
      nodeById,
      result.nodePositions,
    )
    const pairEdges = pairGroups.get(edgePairKey(edge)) || [edge]
    const sourceIndex = sourceEdges.indexOf(edge)
    const targetIndex = targetEdges.indexOf(edge)
    const pairIndex = pairEdges.indexOf(edge)
    const lane = resolveRouteLane(
      sourceIndex,
      sourceEdges.length,
      targetIndex,
      targetEdges.length,
      pairIndex,
      pairEdges.length,
    )
    const start = bottomAnchor(source, sourceIndex, sourceEdges.length)
    const end = topAnchor(target, targetIndex, targetEdges.length)
    let points = edge.source === edge.target
      ? selfLoopPoints(source, pairIndex, config)
      : routePoints(start, end, source, target, lane.ratio, config)

    if (edge.source !== edge.target
      && pathIntersectsNodes(points, result.nodePositions, edge.source, edge.target)) {
      points = routeAroundNodes(
        start,
        end,
        source,
        target,
        lane,
        result.nodePositions,
        edge.source,
        edge.target,
        config,
      )
    }

    result.edgePaths.set(edge.id, {
      points,
      type: points.length === 2 ? 'straight' : 'orthogonal',
    })
  }

  result.canvasBounds = calculateBounds(result, config.boundsPadding)
  return result
}

/**
 * dagre 会尽量减少交叉，但不会把业务端口顺序视为强约束。这里仅交换同层、
 * 单一父节点的分支卡片横坐标，确保“条件 1 → 条件 2 → 默认”的视觉顺序稳定。
 */
function alignBranchTargets(nodes, edges, positions) {
  const incoming = groupEdges(edges, 'target')
  const outgoing = groupEdges(edges, 'source')

  nodes.forEach((node) => {
    const orderedEdges = sortOutgoingEdges(outgoing.get(node.id) || [], node)
    const targets = orderedEdges
      .map(edge => edge.target)
      .filter((targetId, index, values) => values.indexOf(targetId) === index)
      .filter(targetId => (incoming.get(targetId) || []).length === 1)
    if (targets.length < 2)
      return

    const targetPositions = targets.map(targetId => positions.get(targetId))
    if (targetPositions.some(position => !position))
      return
    const firstY = targetPositions[0].y
    if (targetPositions.some(position => position.y !== firstY))
      return

    const slots = targetPositions.map(position => position.x).sort((left, right) => left - right)
    targets.forEach((targetId, index) => {
      const current = positions.get(targetId)
      positions.set(targetId, { ...current, x: slots[index] })
    })
  })
}

function emptyLayout() {
  return {
    nodePositions: new Map(),
    edgePaths: new Map(),
    canvasBounds: { minX: 0, minY: 0, maxX: 0, maxY: 0 },
  }
}

function positionDagreNodes(graph, nodes, positions, config) {
  const raw = nodes.map((node, index) => {
    const position = graph.node(node.id)
    if (!position)
      return fallbackPosition(node, index, config)
    return {
      id: node.id,
      x: position.x - config.nodeWidth / 2,
      y: position.y - config.nodeHeight / 2,
    }
  })
  const minX = Math.min(...raw.map(item => item.x))
  const minY = Math.min(...raw.map(item => item.y))
  const shiftX = config.marginX - minX
  const shiftY = config.marginY - minY

  raw.forEach((item) => {
    positions.set(item.id, {
      x: Math.round(item.x + shiftX),
      y: Math.round(item.y + shiftY),
      width: config.nodeWidth,
      height: config.nodeHeight,
    })
  })
}

function positionFallbackNodes(nodes, positions, config) {
  nodes.forEach((node, index) => {
    positions.set(node.id, fallbackPosition(node, index, config))
  })
}

function fallbackPosition(node, index, config) {
  return {
    id: node.id,
    x: config.marginX,
    y: config.marginY + index * (config.nodeHeight + config.rankGap),
    width: config.nodeWidth,
    height: config.nodeHeight,
  }
}

function groupEdges(edges, key) {
  const result = new Map()
  edges.forEach((edge) => {
    const value = edge[key]
    if (!result.has(value))
      result.set(value, [])
    result.get(value).push(edge)
  })
  return result
}

function groupEdgePairs(edges) {
  const result = new Map()
  edges.forEach((edge) => {
    const key = edgePairKey(edge)
    if (!result.has(key))
      result.set(key, [])
    result.get(key).push(edge)
  })
  for (const group of result.values())
    group.sort(compareEdges)
  return result
}

function edgePairKey(edge) {
  return `${edge.source}\u0000${edge.target}`
}

function sortOutgoingEdges(edges, node) {
  const ports = Array.isArray(node?.ports) ? node.ports : []
  return [...edges].sort((left, right) => {
    const leftIndex = portIndex(ports, left.sourcePort)
    const rightIndex = portIndex(ports, right.sourcePort)
    return leftIndex - rightIndex || compareEdges(left, right)
  })
}

function sortIncomingEdges(edges, nodeById, positions) {
  return [...edges].sort((left, right) => {
    const leftSource = positions.get(left.source)
    const rightSource = positions.get(right.source)
    const leftCenter = leftSource ? leftSource.x + leftSource.width / 2 : 0
    const rightCenter = rightSource ? rightSource.x + rightSource.width / 2 : 0
    if (leftCenter !== rightCenter)
      return leftCenter - rightCenter
    const leftPorts = nodeById.get(left.source)?.ports || []
    const rightPorts = nodeById.get(right.source)?.ports || []
    return portIndex(leftPorts, left.sourcePort) - portIndex(rightPorts, right.sourcePort)
      || compareEdges(left, right)
  })
}

function portIndex(ports, port) {
  const index = ports.indexOf(port)
  return index < 0 ? Number.MAX_SAFE_INTEGER : index
}

function compareEdges(left, right) {
  return String(left.id || '').localeCompare(String(right.id || ''))
}

function bottomAnchor(position, index, count) {
  return {
    x: distributedX(position, index, count),
    y: position.y + position.height,
  }
}

function topAnchor(position, index, count) {
  return {
    x: distributedX(position, index, count),
    y: position.y,
  }
}

function distributedX(position, index, count) {
  if (count <= 1 || index < 0)
    return position.x + position.width / 2
  const padding = Math.min(34, position.width * 0.12)
  const usableWidth = position.width - padding * 2
  return position.x + padding + usableWidth * (index / (count - 1))
}

function resolveRouteLane(sourceIndex, sourceCount, targetIndex, targetCount, pairIndex, pairCount) {
  if (sourceCount > 1) {
    return {
      index: Math.max(0, sourceIndex),
      count: sourceCount,
      ratio: normalizedLaneRatio(sourceIndex, sourceCount),
    }
  }
  if (targetCount > 1) {
    return {
      index: Math.max(0, targetIndex),
      count: targetCount,
      ratio: normalizedLaneRatio(targetIndex, targetCount),
    }
  }
  return {
    index: Math.max(0, pairIndex),
    count: Math.max(1, pairCount),
    ratio: normalizedLaneRatio(pairIndex, pairCount),
  }
}

function normalizedLaneRatio(index, count) {
  if (count <= 1 || index < 0)
    return 0.5
  return index / (count - 1)
}

function routePoints(start, end, source, target, laneRatio, config) {
  const verticalGap = end.y - start.y
  if (verticalGap <= config.routeStub * 2) {
    return detourPoints(start, end, source, target, laneRatio, config)
  }

  const minMiddleY = start.y + config.routeStub
  const maxMiddleY = end.y - config.routeStub
  const middleY = minMiddleY + (maxMiddleY - minMiddleY) * laneRatio
  if (Math.abs(start.x - end.x) < 1 && laneRatio === 0.5)
    return [start, end]
  return simplifyOrthogonal([
    start,
    { x: start.x, y: middleY },
    { x: end.x, y: middleY },
    end,
  ])
}

function detourPoints(start, end, source, target, laneRatio, config) {
  const laneOffset = Math.round(laneRatio * 4) * config.routeLaneGap
  const laneX = Math.max(source.x + source.width, target.x + target.width)
    + config.nodeGap / 2
    + laneOffset
  return simplifyOrthogonal([
    start,
    { x: start.x, y: start.y + config.routeStub },
    { x: laneX, y: start.y + config.routeStub },
    { x: laneX, y: end.y - config.routeStub },
    { x: end.x, y: end.y - config.routeStub },
    end,
  ])
}

function pathIntersectsNodes(points, positions, sourceId, targetId) {
  const obstacles = [...positions.entries()]
    .filter(([nodeId]) => nodeId !== sourceId && nodeId !== targetId)
    .map(([, position]) => position)
  return obstacles.some(position => pathIntersectsRect(points, position))
}

function pathIntersectsRect(points, rect) {
  const padding = 6
  const left = rect.x - padding
  const right = rect.x + rect.width + padding
  const top = rect.y - padding
  const bottom = rect.y + rect.height + padding

  for (let index = 1; index < points.length; index += 1) {
    const start = points[index - 1]
    const end = points[index]
    if (start.x === end.x) {
      const minY = Math.min(start.y, end.y)
      const maxY = Math.max(start.y, end.y)
      if (start.x > left && start.x < right && maxY > top && minY < bottom)
        return true
    }
    else if (start.y === end.y) {
      const minX = Math.min(start.x, end.x)
      const maxX = Math.max(start.x, end.x)
      if (start.y > top && start.y < bottom && maxX > left && minX < right)
        return true
    }
  }
  return false
}

function routeAroundNodes(start, end, source, target, lane, positions, sourceId, targetId, config) {
  const relevant = [...positions.entries()]
    .filter(([nodeId]) => nodeId !== sourceId && nodeId !== targetId)
    .map(([, position]) => position)
    .filter(position => position.y < end.y && position.y + position.height > start.y)
  const bounds = relevant.length ? relevant : [source, target]
  const minimumX = Math.min(source.x, target.x, ...bounds.map(position => position.x))
  const maximumX = Math.max(
    source.x + source.width,
    target.x + target.width,
    ...bounds.map(position => position.x + position.width),
  )
  const laneOffset = lane.index * config.routeLaneGap
  const leftLaneX = minimumX - config.nodeGap / 2 - laneOffset
  const rightLaneX = maximumX + config.nodeGap / 2 + laneOffset
  const sourceCenterX = source.x + source.width / 2
  const preferLeft = start.x < sourceCenterX
    || (start.x === sourceCenterX
      && Math.abs(start.x - leftLaneX) < Math.abs(rightLaneX - start.x))
  const useLeft = preferLeft && leftLaneX >= config.boundsPadding / 2
  const laneX = useLeft ? leftLaneX : rightLaneX
  const maximumDetourOffset = Math.max(
    config.routeLaneGap,
    (end.y - start.y - config.routeStub * 2) / 4,
  )
  const detourOffset = maximumDetourOffset * ((lane.index + 1) / (lane.count + 1))
  const startApproachY = start.y + config.routeStub + detourOffset
  const endApproachY = end.y - config.routeStub - detourOffset

  return simplifyOrthogonal([
    start,
    { x: start.x, y: startApproachY },
    { x: laneX, y: startApproachY },
    { x: laneX, y: endApproachY },
    { x: end.x, y: endApproachY },
    end,
  ])
}

function selfLoopPoints(position, index, config) {
  const start = {
    x: position.x + position.width,
    y: position.y + position.height * 0.42,
  }
  const end = {
    x: position.x + position.width,
    y: position.y + position.height * 0.7,
  }
  const loopX = position.x + position.width + config.routeStub * 2 + index * config.routeLaneGap
  return [
    start,
    { x: loopX, y: start.y },
    { x: loopX, y: end.y },
    end,
  ]
}

function simplifyOrthogonal(points) {
  const compact = points.filter((point, index) => {
    if (index === 0)
      return true
    const previous = points[index - 1]
    return previous.x !== point.x || previous.y !== point.y
  })
  if (compact.length <= 2)
    return compact

  const result = [compact[0]]
  for (let index = 1; index < compact.length - 1; index += 1) {
    const previous = result[result.length - 1]
    const current = compact[index]
    const next = compact[index + 1]
    const sameVertical = previous.x === current.x && current.x === next.x
    const sameHorizontal = previous.y === current.y && current.y === next.y
    if (!sameVertical && !sameHorizontal)
      result.push(current)
  }
  result.push(compact[compact.length - 1])
  return result
}

function calculateBounds(result, padding) {
  const xs = []
  const ys = []
  for (const position of result.nodePositions.values()) {
    xs.push(position.x, position.x + position.width)
    ys.push(position.y, position.y + position.height)
  }
  for (const path of result.edgePaths.values()) {
    for (const point of path.points) {
      xs.push(point.x)
      ys.push(point.y)
    }
  }
  if (!xs.length)
    return { minX: 0, minY: 0, maxX: 0, maxY: 0 }
  return {
    minX: Math.max(0, Math.min(...xs) - padding),
    minY: Math.max(0, Math.min(...ys) - padding),
    maxX: Math.max(...xs) + padding,
    maxY: Math.max(...ys) + padding,
  }
}
