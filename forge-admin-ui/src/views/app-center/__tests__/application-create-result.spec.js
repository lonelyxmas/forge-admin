import { describe, expect, it } from 'vitest'
import { resolveApplicationCreateResult } from '../components/application-create-result'

describe('application create result', () => {
  it('uses the server-generated application code', () => {
    expect(resolveApplicationCreateResult({
      id: '2081963897782308865',
      applicationCode: 'procurement_warehouse_2',
    })).toEqual({
      id: '2081963897782308865',
      applicationCode: 'procurement_warehouse_2',
    })
  })

  it('keeps compatibility with the previous numeric response for an explicit code', () => {
    expect(resolveApplicationCreateResult('101', 'crm_center')).toEqual({
      id: '101',
      applicationCode: 'crm_center',
    })
  })

  it('fails clearly when neither the server nor caller provides a code', () => {
    expect(() => resolveApplicationCreateResult('101')).toThrow('未返回应用编码')
  })
})
