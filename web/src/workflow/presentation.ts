/* 소스 안내
 * 현재 작업/종료 이력과 선택 작업을 화면 목록에 맞춰 정리한다. 과거 승인을 새 작업의 승인으로 재사용하지 않는다.
 * 연결 모듈: ./work.
 */
import type { WorkOrder } from './work';

export function workQueue(orders: WorkOrder[], history: boolean) {
  return orders.filter(order => history ? order.status === 'closed' : order.status !== 'closed');
}

export function selectedWork(orders: WorkOrder[], selected: string, newRequest: boolean) {
  // A new request never inherits the selected previous job's checks/approval.
  return newRequest ? undefined : orders.find(order => order.id === selected) ?? orders[0];
}
