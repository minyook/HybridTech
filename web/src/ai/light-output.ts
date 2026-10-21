/** Qwen's non-thinking template can echo an empty <think> wrapper. Keep any
 * template/thinking prelude out of visible Markdown, including partial chunks. */
export function visibleLightAnswer(raw:string){
  const start=raw.trimStart();
  if('<think>'.startsWith(start))return '';
  if(start.startsWith('<think>')){const end=start.indexOf('</think>');return end<0?'':start.slice(end+8).trimStart();}
  return raw;
}

/** The small model can restart an already completed numbered procedure. Keep
 * the first list, rather than showing the repeated list and a cut-off suffix. */
export function cleanLightSummary(raw:string,truncated=false){
  let lines=visibleLightAnswer(raw).split('\n');let first='',maxStep=0;
  const comparable=(text:string)=>text.replace(/[^0-9a-z가-힣]/gi,'').toLowerCase();
  for(let i=0;i<lines.length;i++){
    const match=lines[i].match(/^\s*(\d+)[.)]\s+(.+)$/);if(!match)continue;
    const step=Number(match[1]),text=comparable(match[2]);
    if(step===1&&first&&maxStep>=3&&Math.min(text.length,first.length)>=12&&(first.startsWith(text)||text.startsWith(first))){lines=lines.slice(0,i);break;}
    if(step===1){first=text;maxStep=1;}else maxStep=Math.max(maxStep,step);
  }
  while(lines.length&&!lines.at(-1)?.trim())lines.pop();
  if(truncated&&lines.length&&!/[.!?。]$/.test(lines.at(-1)!.trim()))lines.pop();
  while(lines.length&&(!lines.at(-1)?.trim()||/^\s*#{1,6}\s+|^[^.!?。]{1,80}:\s*$/.test(lines.at(-1)!)))lines.pop();
  return lines.join('\n').trim();
}
