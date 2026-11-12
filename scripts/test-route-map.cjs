// Synthetic renderer tests: no network requests or real child/location data.
const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const source=fs.readFileSync(path.join(__dirname,'../common/web/route-map.js'),'utf8');
let requested=[];
class Element {
 constructor(tag){this.tag=tag;this.children=[];this.attrs={};this.style={};this.clientWidth=400;this.clientHeight=280;}
 append(x){x.parent=this;this.children.push(x)} replaceChildren(){this.children=[]} setAttribute(k,v){this.attrs[k]=v}
 remove(){this.parent.children=this.parent.children.filter(c=>c!==this)}
 set src(v){this.url=v;requested.push(v)} get src(){return this.url}
}
const document={createElement:t=>new Element(t)},window={};vm.runInNewContext(source,{window,document,Math,Number,Map,WeakMap,Set});
const api=window.ShuttleRouteMap,container=new Element('div');
const all=(n=container)=>[n,...n.children.flatMap(c=>all(c))];
const nodes=tag=>all().filter(n=>n.tag===tag),markers=()=>all().filter(n=>n.attrs['data-marker']);
const view={pickup:{latitude:37.54,longitude:127.03,name:'PRIVATE_CHILD'},location:{latitude:37.55,longitude:127.01},stale:false,shareToken:'SECRET',roadRoute:{geometry:[[{latitude:37.55,longitude:127.01},{latitude:37.54,longitude:127.03}]]}};
api.draw(container,view);
assert.equal(markers().length,2);assert.ok(nodes('img').length<=9);
assert.ok(requested.every(url=>/^https:\/\/tile\.openstreetmap\.org\/\d+\/\d+\/\d+\.png$/.test(url)));
assert.ok(nodes('img').every(n=>n.referrerPolicy==='origin'));
assert.ok(nodes('a').some(n=>n.href==='https://www.openstreetmap.org/copyright'));
assert.ok(!all().some(n=>['path','polyline','line','script'].includes(n.tag)));
const count=requested.length,images=nodes('img');for(let i=0;i<30;i++)api.draw(container,view);
assert.equal(requested.length,count);assert.equal(nodes('img')[0],images[0]);
const pin=markers()[1],oldLeft=pin.style.left;
api.draw(container,{...view,source:'SIMULATED',location:{latitude:37.549,longitude:127.012}});
assert.equal(markers()[1],pin);assert.notEqual(pin.style.left,oldLeft);assert.ok(pin.style.transition.includes('4.5s'));
api.draw(container,{...view,stale:true});assert.equal(markers()[1].attrs['data-color'],'#8b9692');assert.equal(markers()[1].style.transition,'none');
nodes('img')[0].onerror();assert.ok(all().some(n=>String(n.textContent).includes('불러오지 못했습니다')));
api.draw(container,{...view,location:null});assert.equal(markers().length,1);
const zoom=nodes('button').find(n=>n.attrs['aria-label']==='지도 확대');zoom.onclick();assert.ok(requested.length>count);
const old=nodes('img')[0];api.clear(container);assert.equal(container.children.length,0);old.onerror();assert.equal(container.children.length,0);
api.draw(container,{...view,pickup:{latitude:91,longitude:127}});assert.equal(container.children.length,0);
api.draw(container,{pickup:{latitude:37.5,longitude:179.99},location:{latitude:37.5,longitude:-179.99}});
assert.ok(nodes('img').length<=9);assert.equal(markers().length,2);
for(const img of nodes('img')){const [,z,x,y]=img.src.match(/\/(\d+)\/(\d+)\/(\d+)\.png/);assert.ok(+x>=0&&+x<2**+z&&+y>=0&&+y<2**+z);}
const beforeTrip=markers()[1];api.draw(container,{...view,tripId:'new-trip'});assert.notEqual(markers()[1],beforeTrip);
console.log('10 map contracts passed: visible tiles/privacy/attribution, no invented route, cache reuse, stale marker, failure feedback, pickup-only, controls/cleanup, coordinate boundaries.');
