import { openDB, type IDBPDatabase } from 'idb';
import type { Chat, Checklist, ModelRecord } from '../types';
import { transitionWork, type Actor, type WorkAction, type WorkOrder } from '../workflow/work';

let connection:Promise<IDBPDatabase>|undefined;
const database = () => connection ??= openDB('fieldguard-web', 2, {
  upgrade(db,oldVersion) {
    if(oldVersion<1){
    db.createObjectStore('chats', { keyPath: 'id' });
    db.createObjectStore('checklists', { keyPath: 'id' });
    db.createObjectStore('settings');
    db.createObjectStore('files');
    }
    if(oldVersion<2)db.createObjectStore('workOrders',{keyPath:'id'});
  },
}).then(db=>{db.addEventListener('versionchange',()=>{db.close();connection=undefined;});return db;}).catch(error=>{connection=undefined;throw error;});
export async function allChats(): Promise<Chat[]> { return (await (await database()).getAll('chats')).sort((a,b) => b.createdAt.localeCompare(a.createdAt)); }
export async function saveChat(chat: Chat) { await (await database()).put('chats', chat); }
export async function deleteChat(id: string) { await (await database()).delete('chats',id); }
export async function allChecklists(): Promise<Checklist[]> { return (await (await database()).getAll('checklists')).sort((a,b) => b.createdAt.localeCompare(a.createdAt)); }
export async function saveChecklist(record: Checklist) { await (await database()).put('checklists',record); }
export async function getSetting<T>(key: string): Promise<T | undefined> { return (await database()).get('settings',key); }
export async function setSetting(key: string, value: unknown) { await (await database()).put('settings',value,key); }
export async function storeModelBlob(blob: Blob) { await (await database()).put('files',blob,'model'); }
export async function getModelBlob(): Promise<Blob | undefined> { return (await database()).get('files','model'); }
export async function commitModel(metadata: ModelRecord, blob?: Blob) {
  const db = await database(); const tx = db.transaction(['settings','files'],'readwrite');
  if (blob) await tx.objectStore('files').put(blob,'model');
  await tx.objectStore('settings').put(metadata,'model'); await tx.done;
}
export async function modelMetadata() { return getSetting<ModelRecord>('model'); }
export async function removeModelData() {
  const db = await database(); const tx = db.transaction(['settings','files'],'readwrite');
  await tx.objectStore('settings').delete('model'); await tx.objectStore('files').delete('model'); await tx.done;
}
export async function clearRecords() {
  const db = await database(); const tx = db.transaction(['chats','checklists','settings','workOrders'],'readwrite');
  await tx.objectStore('chats').clear(); await tx.objectStore('checklists').clear(); await tx.objectStore('workOrders').clear(); await tx.objectStore('settings').delete('checklist-draft'); await tx.done;
}
export async function allWorkOrders():Promise<WorkOrder[]> { return (await (await database()).getAll('workOrders')).sort((a,b)=>b.updatedAt.localeCompare(a.updatedAt)); }
export async function saveNewWorkOrder(order:WorkOrder) { await (await database()).add('workOrders',order); }
export async function applyWorkAction(id:string,actor:Actor,action:WorkAction,revision:number):Promise<WorkOrder> {
  const tx=(await database()).transaction('workOrders','readwrite');
  try{
    const current=await tx.store.get(id) as WorkOrder|undefined;
    if(!current||current.revision!==revision)throw new Error('다른 화면에서 작업이 갱신되었어요. 새로 불러온 내용을 확인하고 다시 진행해 주세요.');
    const updated=transitionWork(current,actor,action);await tx.store.put(updated);await tx.done;return updated;
  }catch(error){try{tx.abort();}catch{/* Transaction may already have ended. */}await tx.done.catch(()=>{});throw error;}
}
