import type { WorkOrder } from './work';

export function workQueue(orders: WorkOrder[], history: boolean) {
  return orders.filter(order => history ? order.status === 'closed' : order.status !== 'closed');
}

export function selectedWork(orders: WorkOrder[], selected: string, newRequest: boolean) {
  // A new request never inherits the selected previous job's checks/approval.
  return newRequest ? undefined : orders.find(order => order.id === selected) ?? orders[0];
}
