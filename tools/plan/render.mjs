import { readFileSync, writeFileSync } from 'node:fs';
import { Dwg_File_Type, LibreDwg } from '@mlightcad/libredwg-web';
const lib = await LibreDwg.create('./node_modules/@mlightcad/libredwg-web/wasm/');
const db = lib.convert(lib.dwg_read_data(readFileSync('plano.dwg'), Dwg_File_Type.DWG));
const blocks = new Map(db.tables.BLOCK_RECORD.entries.map((b) => [b.name, b]));
const segs = []; // [x1,y1,x2,y2,layer]
const texts = [];
function tf(m, p) { return [m[0]*p[0]+m[1]*p[1]+m[2], m[3]*p[0]+m[4]*p[1]+m[5]]; }
function mul(a,b){return [a[0]*b[0]+a[1]*b[3], a[0]*b[1]+a[1]*b[4], a[0]*b[2]+a[1]*b[5]+a[2], a[3]*b[0]+a[4]*b[3], a[3]*b[1]+a[4]*b[4], a[3]*b[2]+a[4]*b[5]+a[5]];}
function walk(ents, m, depth, layerOverride) {
  for (const e of ents) {
    const layer = (e.layer === '0' && layerOverride) ? layerOverride : e.layer;
    if (e.type === 'LINE') segs.push([...tf(m,[e.startPoint.x,e.startPoint.y]), ...tf(m,[e.endPoint.x,e.endPoint.y]), layer]);
    else if (e.type === 'LWPOLYLINE' || e.type === 'POLYLINE2D' || e.type==='POLYLINE') {
      const v = e.vertices||[]; for (let i=1;i<v.length;i++) segs.push([...tf(m,[v[i-1].x,v[i-1].y]), ...tf(m,[v[i].x,v[i].y]), layer]);
      if ((e.flag & 1) && v.length>2) segs.push([...tf(m,[v[v.length-1].x,v[v.length-1].y]), ...tf(m,[v[0].x,v[0].y]), layer]);
    } else if (e.type === 'ARC' || e.type === 'CIRCLE') {
      const a0 = e.type==='CIRCLE'?0:e.startAngle, a1 = e.type==='CIRCLE'?Math.PI*2:e.endAngle; let span=a1-a0; if (span<=0) span+=Math.PI*2;
      const n = Math.max(4, Math.ceil(span/0.3)); let prev=null;
      for (let i=0;i<=n;i++){ const a=a0+span*i/n; const p=tf(m,[e.center.x+e.radius*Math.cos(a), e.center.y+e.radius*Math.sin(a)]); if(prev) segs.push([...prev,...p,layer]); prev=p; }
    } else if (e.type === 'INSERT' && depth < 4) {
      const b = blocks.get(e.name); if (!b) continue;
      const c=Math.cos(e.rotation||0), s=Math.sin(e.rotation||0), sx=e.xScale||1, sy=e.yScale||1;
      const bp=b.basePoint||{x:0,y:0};
      const local=[sx*c, -sy*s, e.insertionPoint.x, sx*s, sy*c, e.insertionPoint.y];
      const base=[1,0,-bp.x,0,1,-bp.y];
      walk(b.entities||[], mul(m, mul(local, base)), depth+1, layer);
    } else if ((e.type==='TEXT'||e.type==='MTEXT') && depth===0) {
      const p=e.startPoint||e.insertionPoint; if(p) texts.push({x:p.x,y:p.y,h:e.textHeight||e.height||0.2,t:(e.text||'').replace(/\\[A-Za-z][^;]*;|[{}]/g,'').replace(/\\P/g,' '),layer});
    }
  }
}
walk(db.entities, [1,0,0,0,1,0], 0, null);
writeFileSync('segs.json', JSON.stringify({segs, texts}));
console.log('segs', segs.length, 'texts', texts.length);
