// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** listNotices GET /api/notice/list */
export async function listNoticesUsingGet(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.listNoticesUsingGETParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponsePageReviewNoticeVO_>('/api/notice/list', {
    method: 'GET',
    params: {
      // page has a default value: 1
      page: '1',
      // size has a default value: 10
      size: '10',
      ...params,
    },
    ...(options || {}),
  })
}

/** markAsRead POST /api/notice/read/${param0} */
export async function markAsReadUsingPost(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.markAsReadUsingPOSTParams,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params
  return request<API.BaseResponseBoolean_>(`/api/notice/read/${param0}`, {
    method: 'POST',
    params: { ...queryParams },
    ...(options || {}),
  })
}

/** getUnreadCount GET /api/notice/unread-count */
export async function getUnreadCountUsingGet(options?: { [key: string]: any }) {
  return request<API.BaseResponseLong_>('/api/notice/unread-count', {
    method: 'GET',
    ...(options || {}),
  })
}
