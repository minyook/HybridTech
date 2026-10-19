import { expect, it } from 'vitest';
import { createChatViewport, type ChatViewportSample } from '../src/chat/viewport';

const phone:ChatViewportSample={width:390,height:844,layoutHeight:844,offsetTop:0,scale:1,screenHeight:844,editorFocused:false};
it('Safari visual viewport shrink puts the composer above the keyboard, then restores even with focus retained',()=>{
  const update=createChatViewport(phone);
  expect(update({...phone,editorFocused:true}).keyboardOpen).toBe(false);
  expect(update({...phone,height:460,offsetTop:20,editorFocused:true})).toEqual({height:460,offsetTop:20,keyboardOpen:true});
  expect(update({...phone,editorFocused:true}).keyboardOpen).toBe(false);
});
it('Chrome layout resize and browsers without VisualViewport use the remembered unobstructed height',()=>{
  const update=createChatViewport(phone);
  update({...phone,editorFocused:true});
  expect(update({...phone,height:470,layoutHeight:470,editorFocused:true}).keyboardOpen).toBe(true);
  expect(update({...phone,height:600,layoutHeight:600}).keyboardOpen).toBe(true);
  expect(update(phone).keyboardOpen).toBe(false);
});
it('browser toolbar changes, hardware keyboard focus and unfocused window resizing do not hide navigation',()=>{
  const update=createChatViewport(phone);
  expect(update({...phone,height:780,editorFocused:true}).keyboardOpen).toBe(false);
  expect(update({...phone,height:400,layoutHeight:400}).keyboardOpen).toBe(false);
});
it('pinch zoom neither collapses the chat nor opens keyboard mode',()=>{
  const update=createChatViewport(phone);
  expect(update({...phone,height:422,offsetTop:180,scale:2,editorFocused:true})).toEqual({height:844,offsetTop:0,keyboardOpen:false});
  expect(update(phone).keyboardOpen).toBe(false);
});
it('rotation preserves an open keyboard and closing in the new orientation restores navigation',()=>{
  const update=createChatViewport(phone);
  update({...phone,height:460,editorFocused:true});
  expect(update({...phone,width:844,height:200,layoutHeight:200,screenHeight:390,editorFocused:true}).keyboardOpen).toBe(true);
  expect(update({...phone,width:844,height:390,layoutHeight:390,screenHeight:390,editorFocused:true}).keyboardOpen).toBe(false);
});
it('rotation without a keyboard and loss of focus restore normal baseline measurements',()=>{
  const update=createChatViewport(phone);
  expect(update({...phone,width:844,height:390,layoutHeight:390,screenHeight:390}).keyboardOpen).toBe(false);
  expect(update(phone).keyboardOpen).toBe(false);
  expect(update({...phone,height:460,editorFocused:true}).keyboardOpen).toBe(true);
  expect(update(phone).keyboardOpen).toBe(false);
});
