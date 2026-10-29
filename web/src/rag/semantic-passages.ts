import type {Chunk} from '../types';
import {evidenceUnits} from './evidence';
export interface SemanticPassage {id:string;documentId:string;page:number;chunkIndex:number;section:string;text:string;orders:number[];units:ReturnType<typeof evidenceUnits>;searchText:string;sourceBody:string;vector:number[]}
/** Retrieval units keep the original bullet's condition/action together. The
 * enclosing section is restored only after retrieval, outside model context. */
export function knowledgePassages(chunks:Chunk[]):SemanticPassage[]{
 return chunks.flatMap(chunk=>{
  const units=evidenceUnits({chunk,score:0});const groups=new Map<number,typeof units>();
  for(const unit of units){const key=unit.group??unit.order;groups.set(key,[...(groups.get(key)??[]),unit]);}
  const sections=new Map<string,typeof units>();for(const unit of units)sections.set(unit.section,[...(sections.get(unit.section)??[]),unit]);
  const retrievalGroups=[...groups.values(),...[...sections.values()].filter(group=>new Set(group.map(u=>u.group)).size>1)];
  return retrievalGroups.filter(group=>group.map(u=>u.text).join(' ').length>15).map((group,index)=>{
   const text=group.map(u=>u.text).join(' '),section=group[0].section;
   return {id:`${chunk.documentId}:${chunk.pageNumber}:${chunk.chunkIndex}/${index}`,documentId:chunk.documentId,page:chunk.pageNumber,chunkIndex:chunk.chunkIndex,section,text,orders:group.map(u=>u.order),units:group,searchText:`${chunk.documentTitle}\n${section}\n${text}`,sourceBody:chunk.body,vector:[]};
  });
 });
}
