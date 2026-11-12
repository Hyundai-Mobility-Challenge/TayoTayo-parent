/* Viewport-only OSM background. Vehicle observations/ETA remain Pleos data.
 * No routing, geolocation, external scripts, prefetch or offline download. */
(function(global){
 'use strict';
 const instances=new WeakMap(), TILE=256, LIMIT=85.05112878;
 const clamp=(n,a,b)=>Math.min(b,Math.max(a,n));
 const valid=p=>p&&Number.isFinite(p.latitude)&&Number.isFinite(p.longitude)&&Math.abs(p.latitude)<=90&&Math.abs(p.longitude)<=180;
 function project(p){const s=Math.sin(clamp(p.latitude,-LIMIT,LIMIT)*Math.PI/180);return {x:(p.longitude+180)/360,y:.5-Math.log((1+s)/(1-s))/(4*Math.PI)};}
 const wrap=x=>((x%1)+1)%1;
 const near=(x,c)=>x+Math.round(c-x);
 function node(tag,parent,text){const n=document.createElement(tag);if(text)n.textContent=text;if(parent)parent.append(n);return n;}
 function clear(container){const s=instances.get(container);if(s){s.dead=true;if(s.observer)s.observer.disconnect();instances.delete(container);}container.replaceChildren();}
 function create(container){
  container.replaceChildren();
  const root=node('div',container);root.style.cssText='position:relative;width:100%;height:100%;min-height:240px;overflow:hidden;background:#e7eee9;border-radius:14px;font:13px/1.4 system-ui,sans-serif;color:#173b31';
  const viewport=node('div',root);viewport.style.cssText='position:absolute;inset:0;touch-action:none;overflow:hidden;cursor:grab';viewport.setAttribute('aria-label','차량과 픽업 장소 지도');
  const tiles=node('div',viewport);tiles.style.cssText='position:absolute;inset:0;pointer-events:none';
  const markers=node('div',viewport);markers.style.cssText='position:absolute;inset:0;pointer-events:none';
  const controls=node('div',root);controls.style.cssText='position:absolute;top:10px;left:10px;display:flex;gap:5px;z-index:2';
  const status=node('div',root);status.setAttribute('role','status');status.style.cssText='position:absolute;left:10px;right:10px;bottom:30px;font-size:12px;pointer-events:none';
  const attribution=node('a',root,'© OpenStreetMap contributors');attribution.href='https://www.openstreetmap.org/copyright';attribution.target='_blank';attribution.rel='noopener noreferrer';attribution.style.cssText='position:absolute;right:0;bottom:0;padding:3px 6px;background:rgba(255,255,255,.95);color:#176e56;font-size:11px;z-index:3';
  const s={root,viewport,tiles,markers,status,images:new Map(),pins:new Map(),zoom:15,center:{x:.5,y:.5},follow:true,dead:false};
  function button(label,title,action){const b=node('button',controls,label);b.type='button';b.setAttribute('aria-label',title);b.title=title;b.style.cssText='width:auto;min-width:36px;min-height:36px;padding:6px 10px;border:1px solid #bbcfc3;border-radius:8px;background:white;color:#173b31;font:600 14px system-ui;cursor:pointer';b.onclick=action;return b;}
  function dimensions(){return {w:Math.max(240,container.clientWidth||400),h:Math.max(240,container.clientHeight||280)};}
  function points(){return [s.view.pickup].concat(valid(s.view.location)?[s.view.location]:[]).map(project);}
  s.fit=()=>{const ps=points(),xs=ps.map(p=>near(p.x,ps[0].x)),ys=ps.map(p=>p.y),d=dimensions();s.center={x:wrap((Math.min(...xs)+Math.max(...xs))/2),y:(Math.min(...ys)+Math.max(...ys))/2};s.zoom=16;while(s.zoom>1&&((Math.max(...xs)-Math.min(...xs))*TILE*2**s.zoom>d.w-100||(Math.max(...ys)-Math.min(...ys))*TILE*2**s.zoom>d.h-110))s.zoom--;};
  function tileStatus(){if(s.dead)return;const values=[...s.images.values()],failed=values.filter(n=>n.failed).length,pending=values.filter(n=>!n.loaded&&!n.failed).length;status.textContent=failed?'배경지도를 불러오지 못했습니다. 연결 확인 후 ↻를 눌러 주세요.':pending?'배경지도 불러오는 중…':'';status.style.background=failed||pending?'rgba(255,255,255,.95)':'transparent';}
  s.render=()=>{
   if(s.dead)return;
   const d=dimensions(),count=2**s.zoom,world=TILE*count,cx=s.center.x*world,cy=s.center.y*world,left=cx-d.w/2,top=cy-d.h/2,needed=new Set();
   for(let y=Math.max(0,Math.floor(top/TILE));y<=Math.min(count-1,Math.floor((top+d.h-1)/TILE));y++)for(let x=Math.floor(left/TILE);x<=Math.floor((left+d.w-1)/TILE);x++){
    const xx=((x%count)+count)%count,key=`${s.zoom}/${xx}/${y}`;needed.add(key);let img=s.images.get(key);
    if(!img){img=node('img',tiles);img.alt='';img.draggable=false;img.referrerPolicy='origin';img.style.cssText='position:absolute;width:256px;height:256px;max-width:none';img.onload=()=>{img.loaded=true;tileStatus();};img.onerror=()=>{img.failed=true;tileStatus();};s.images.set(key,img);img.src=`https://tile.openstreetmap.org/${key}.png`;}
    img.style.left=`${Math.round(x*TILE-left)}px`;img.style.top=`${Math.round(y*TILE-top)}px`;
   }
   for(const [key,img] of s.images)if(!needed.has(key)){img.remove();s.images.delete(key);}
   function marker(key,p,label,color){const q=project(p);let n=s.pins.get(key);if(!n){n=node('div',markers);n.dot=node('div',n);n.caption=node('span',n);n.style.cssText='position:absolute;transform:translate(-50%,-50%);text-align:center';n.caption.style.cssText='position:absolute;top:25px;left:50%;transform:translateX(-50%);white-space:nowrap;background:white;border-radius:6px;padding:2px 6px;box-shadow:0 1px 4px #3333';s.pins.set(key,n);}
    n.style.transition=key==='vehicle'&&s.animate&&!s.view.stale?'left 4.5s linear, top 4.5s linear':'none';n.style.left=`${near(q.x,s.center.x)*world-left}px`;n.style.top=`${q.y*world-top}px`;n.caption.textContent=label;n.dot.style.cssText=`width:20px;height:20px;margin:auto;border-radius:50%;background:${color};border:3px solid white;box-shadow:0 1px 6px #3338`;n.setAttribute('data-marker',label);n.setAttribute('data-color',color);}
   marker('pickup',s.view.pickup,'내 픽업','#eea648');if(valid(s.view.location))marker('vehicle',s.view.location,s.view.stale?'마지막 위치':'차량',s.view.stale?'#8b9692':'#176e56');else if(s.pins.has('vehicle')){s.pins.get('vehicle').remove();s.pins.delete('vehicle');}s.animate=false;tileStatus();
  };
  s.zoomBy=delta=>{s.follow=false;s.zoom=clamp(s.zoom+delta,1,18);s.render();};
  button('+','지도 확대',()=>s.zoomBy(1));button('−','지도 축소',()=>s.zoomBy(-1));button('전체 보기','차량과 픽업 장소 함께 보기',()=>{s.follow=true;s.fit();s.render();});button('↻','배경지도 다시 불러오기',()=>{s.tiles.replaceChildren();s.images.clear();s.render();});
  let drag=null;
  viewport.onpointerdown=e=>{if(e.button!==undefined&&e.button!==0)return;s.follow=false;drag={id:e.pointerId,x:e.clientX,y:e.clientY,center:{...s.center}};if(viewport.setPointerCapture)viewport.setPointerCapture(e.pointerId);};
  viewport.onpointermove=e=>{if(!drag||drag.id!==e.pointerId)return;const world=TILE*2**s.zoom;s.center={x:wrap(drag.center.x-(e.clientX-drag.x)/world),y:clamp(drag.center.y-(e.clientY-drag.y)/world,0,1)};s.render();};
  viewport.onpointerup=viewport.onpointercancel=()=>{drag=null;};
  if(global.ResizeObserver){s.observer=new global.ResizeObserver(()=>{if(s.view){if(s.follow)s.fit();s.render();}});s.observer.observe(container);}
  instances.set(container,s);return s;
 }
 function draw(container,view){
  if(!valid(view.pickup)){clear(container);return '픽업 장소 위치를 확인하고 있습니다.';}
  let s=instances.get(container);if(s&&s.view.tripId!==view.tripId){clear(container);s=null;}
  s=s||create(container);const first=!s.view;s.view=view;
  const world=TILE*2**s.zoom,w=Math.max(240,container.clientWidth||400),h=Math.max(240,container.clientHeight||280);
  const outside=[view.pickup,view.location].filter(valid).map(project).some(p=>Math.abs(near(p.x,s.center.x)-s.center.x)*world>w/2-40||Math.abs(p.y-s.center.y)*world>h/2-50);
  const refit=first||(s.follow&&outside);if(refit)s.fit();s.animate=!refit;s.render();
  return (view.stale&&view.location?'회색 마커는 마지막 수신 위치입니다. ':'')+'배경지도: OpenStreetMap · 차량 위치: '+(view.source==='SIMULATED'?'모의 시연':'Pleos')+'. Pleos 실제 경로선은 제공되지 않습니다.';
 }
 global.ShuttleRouteMap={draw,clear};
})(window);
