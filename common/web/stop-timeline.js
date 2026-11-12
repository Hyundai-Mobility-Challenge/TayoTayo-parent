/* Child-scoped schematic stop progress. No map, routing, network or GPS inference. */
(function(global){
 'use strict';
 const states=new WeakMap(),clamp=(n,a,b)=>Math.max(a,Math.min(b,n));
 function model(v){
  const steps=Array.isArray(v.progress)?v.progress:[],t=v.timeline||{},elapsed=Math.max(0,v.clientElapsedSeconds||0);
  const current=clamp(Number.isFinite(t.current)?t.current:steps.findIndex(p=>p.current),0,steps.length);
  const age=t.nextEta?(v.serverTime+elapsed-t.nextEta.receivedAt/1000):Infinity;
  const paused=!!v.stale||!!t.paused||elapsed>30||age>30||!t.nextEta;
  const seconds=paused?null:Math.max(0,t.nextEta.seconds-elapsed);
  let fraction=Number.isFinite(t.fraction)?clamp(t.fraction,0,1):0;
  if(!paused&&t.totalSeconds>0)fraction=clamp(1-seconds/t.totalSeconds,0,1);
  // Reaching the ETA does not establish arrival or a completed handover.
  const position=clamp(current+(v.phase==='ALIGHTING'?1:Math.min(.95,fraction)),0,steps.length);
  return {steps,current,position,paused,seconds,simulated:v.source==='SIMULATED'};
 }
 function node(tag,parent,text){const n=document.createElement(tag);if(text)n.textContent=text;if(parent)parent.append(n);return n;}
 function clear(container){states.delete(container);container.replaceChildren();}
 function draw(container,v){
  const m=model(v),key=[v.tripId,v.routeVersion,m.steps.length,v.pickup&&v.pickup.name].join('/');
  if(!m.steps.length){clear(container);node('p',container,'오늘의 하차 순서를 확인하고 있습니다.');return;}
  let s=states.get(container);
  if(!s||s.key!==key){
   clear(container);const root=node('div',container);root.style.cssText='position:relative;height:100%;overflow:auto;border-radius:16px;background:#f3f8f5;font:14px/1.45 system-ui,sans-serif;color:#173b31';
   const body=node('div',root);body.style.cssText=`position:relative;min-height:${(m.steps.length+1)*72+20}px;padding:16px 12px 12px 82px;box-sizing:border-box`;
   const rail=node('div',body);rail.style.cssText=`position:absolute;left:32px;top:36px;width:6px;height:${m.steps.length*72}px;background:#cbdcd2;border-radius:8px`;
   const fill=node('div',rail);fill.style.cssText='width:100%;height:0;background:#176e56;border-radius:8px';
   const rows=[];
   for(let i=0;i<=m.steps.length;i++){
    const row=node('div',body);row.style.cssText='height:72px;position:relative;padding-top:7px;box-sizing:border-box';
    const dot=node('span',row);dot.style.cssText='position:absolute;left:-56px;top:14px;width:14px;height:14px;background:white;border:3px solid #93b9a5;border-radius:50%';
    const title=node('strong',row),detail=node('div',row);detail.style.cssText='font-size:12px;color:#577168;margin-top:4px';rows.push({title,detail,dot});
   }
   const bus=node('div',body,'🚌');bus.setAttribute('role','img');bus.style.cssText='position:absolute;left:16px;top:20px;width:38px;height:32px;border-radius:10px;background:#176e56;color:white;box-shadow:0 2px 5px #0003;text-align:center;font-size:23px;line-height:32px;z-index:2';
   s={key,root,body,fill,bus,rows,legKey:null};states.set(container,s);
  }
  const t=v.timeline||{},sameLeg=s.legKey===t.key;
  s.bus.style.transition=!m.paused&&sameLeg?'top 1s linear':'none';s.fill.style.transition=s.bus.style.transition.replace('top','height');
  s.bus.style.top=`${20+m.position*72}px`;s.fill.style.height=`${m.position*72}px`;
  s.bus.style.background=m.paused?'#7e8e86':'#176e56';s.bus.setAttribute('aria-label',m.paused?'진행 정보 갱신 대기':'예상 시간 기준 버스 진행');s.bus.setAttribute('data-bus-position',String(m.position));s.legKey=t.key;
  s.rows[0].title.textContent='출발';s.rows[0].detail.textContent='오늘의 하원';
  m.steps.forEach((p,i)=>{const row=s.rows[i+1],own=p.kind==='PICKUP';row.title.textContent=own?'내 아이 하차 · '+(v.pickup?.name||'픽업 장소'):`정류장 ${i+1}`;
   row.dot.style.borderColor=own?'#e0a138':p.visited?'#176e56':'#93b9a5';
   row.detail.textContent=p.visited?'방문 완료':p.current?(v.phase==='ALIGHTING'?'정차 · 인계 확인 중':m.seconds===null?'현재 향하는 곳 · 시간 확인 중':m.seconds===0?'도착 확인 대기':`현재 향하는 곳 · ${m.simulated?'시연 ':''}약 ${Math.ceil(m.seconds/60)}분`):'방문 예정';
   if(own&&!p.visited&&!p.current){const eta=t.ownEta;const fresh=eta&&!v.stale&&v.serverTime+(v.clientElapsedSeconds||0)-eta.receivedAt/1000<=30;row.detail.textContent=fresh?`${m.simulated?'시연 ':''}약 ${eta.minutes}분`:'내 아이 도착 시간 확인 중';}
  });
 }
 global.ShuttleStopTimeline={draw,clear,model};
})(window);
