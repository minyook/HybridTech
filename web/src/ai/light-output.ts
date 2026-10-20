/** Qwen's non-thinking template can echo an empty <think> wrapper. Keep any
 * template/thinking prelude out of visible Markdown, including partial chunks. */
export function visibleLightAnswer(raw:string){
  const start=raw.trimStart();
  if('<think>'.startsWith(start))return '';
  if(start.startsWith('<think>')){const end=start.indexOf('</think>');return end<0?'':start.slice(end+8).trimStart();}
  return raw;
}
