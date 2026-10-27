// Canonical ERD source: each service's reference init.sql, never a screenshot.
// node docs/database/render-erd.mjs [--check] [--model-path <ignored JSON output>]
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createHash } from 'node:crypto';

const directory = path.dirname(fileURLToPath(import.meta.url));
const check = process.argv.includes('--check');
const modelArgument = process.argv.indexOf('--model-path');
const model = { version: 1, services: {} };
const failures = [];
const escape = value => String(value).replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;');
const layouts = {
  event: { categories:[0,0], events:[1,0] },
  ticket: { ticket_types:[0,0], reservations:[1,0], outbox_events:[2,0], reservation_items:[0,1], tickets:[1,1], ticket_scans:[2,1] },
  order: { orders:[1,0], order_items:[0,1], payments:[1,1], refund_requests:[2,1], order_holders:[0,2], payment_webhooks:[1,2], outbox_events:[2,2] },
  notification: { notification_templates:[0,0], notifications:[1,0], processed_events:[2,0] },
  analytics: { sales_metrics:[0,0], dashboard_snapshot:[1,0], attendance_logs:[0,1], processed_events:[1,1] }
};

function splitFields(body) {
  const parts=[]; let depth=0, quoted=false, start=0;
  for(let i=0;i<body.length;i++) {
    const c=body[i];
    if(c==="'") { if(quoted && body[i+1]==="'") { i++; continue; } quoted=!quoted; }
    if(quoted) continue;
    if(c==='(')depth++; if(c===')')depth--;
    if(c===','&&depth===0){parts.push(body.slice(start,i).trim());start=i+1;}
  }
  parts.push(body.slice(start).trim()); return parts;
}
function parse(sql,service) {
  if (/^(<<<<<<<|=======|>>>>>>>)/m.test(sql)) throw Error('Unresolved merge conflict in '+service+'-service/init.sql');
  const clean=sql.replace(/--[^\r\n]*/g,''); const tables=[];
  for(const match of clean.matchAll(/CREATE\s+TABLE\s+(\w+)\s*\(/gi)) {
    let end=match.index+match[0].length,depth=1,quoted=false;
    const start=end;
    for(;end<clean.length;end++) {
      const c=clean[end];
      if(c==="'"){if(quoted&&clean[end+1]==="'"){end++;continue;}quoted=!quoted;}
      if(quoted)continue; if(c==='(')depth++; if(c===')'&&--depth===0)break;
    }
    if(depth!==0)throw Error('Unclosed CREATE TABLE '+match[1]);
    const fields=splitFields(clean.slice(start,end));
    const columns=fields.flatMap(field=>{
      const col=/^([a-z_][a-z_0-9]*)\s+([A-Z][A-Z0-9_]*(?:\s*\([^)]*\))?)/i.exec(field);
      if(!col||/^(?:PRIMARY|FOREIGN|UNIQUE|CHECK|INDEX|CONSTRAINT)$/i.test(col[1]))return [];
      const type=col[2].replaceAll(' ','').toUpperCase();
      const isId=col[1]==='id'||col[1].endsWith('_id')||['requested_by','approved_by','reviewed_by'].includes(col[1]);
      if(isId&&!['provider_event_id','provider_transaction_id'].includes(col[1])&&type!=='CHAR(36)')throw Error(service+'.'+match[1]+'.'+col[1]+' must use CHAR(36)');
      return [{name:col[1],type,primary:/\bPRIMARY KEY\b/i.test(field),foreign:false}];
    });
    const foreignKeys=fields.flatMap(field=>{
      const fk=/FOREIGN KEY\s*\((\w+)\)\s+REFERENCES\s+(\w+)\s*\((\w+)\)/i.exec(field);
      if(!fk)return []; const column=columns.find(c=>c.name===fk[1]);if(column)column.foreign=true;
      return [{column:fk[1],parent:fk[2],parentColumn:fk[3],child:match[1]}];
    });
    if(!columns.some(c=>c.name==='id'&&c.primary&&c.type==='CHAR(36)'))throw Error('Missing UUID PK: '+service+'.'+match[1]);
    tables.push({name:match[1],columns,foreignKeys});
  }
  if(!tables.length)throw Error('No tables: '+service);
  const names=new Set(tables.map(t=>t.name));
  for(const t of tables)for(const fk of t.foreignKeys)if(!names.has(fk.parent))throw Error('Missing/cross-service FK: '+service+'.'+fk.parent);
  return tables;
}
function emit(relative,content) {
  const file=path.join(directory,relative);content=content.trim()+'\n';
  if(check){if(!fs.existsSync(file)||fs.readFileSync(file,'utf8').replaceAll('\r\n','\n')!==content)failures.push(relative);}
  else{fs.mkdirSync(path.dirname(file),{recursive:true});fs.writeFileSync(file,content);}
}
function image(service,tables,sha) {
  const layout=layouts[service],heights=[];
  for(const table of tables){if(!layout[table.name])throw Error('Missing layout: '+table.name);table.height=60+table.columns.length*31;const [col,row]=layout[table.name];heights[row]=Math.max(heights[row]||0,table.height);table.x=65+col*750;table.row=row;}
  const rowY=[];let next=150;for(const h of heights){rowY.push(next);next+=(h||0)+110;}
  for(const t of tables)t.y=rowY[t.row];
  const width=130+(Math.max(...Object.values(layout).map(([c])=>c))+1)*750-100,height=next-10;
  const svg=[`<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}" role="img" aria-labelledby="title desc">`,
    `<title id="title">${service}-service — UUID CHAR(36)</title>`,
    `<desc id="desc">Reference ERD generated from init.sql, SHA-256 ${sha}. Every application primary key and ID reference is UUID CHAR(36); only local physical foreign keys are connected.</desc>`,
    '<defs><marker id="arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto-start-reverse"><path d="M 0 0 L 10 5 L 0 10 z" fill="#617891"/></marker></defs>',
    '<rect width="100%" height="100%" fill="white"/>',
    `<text x="65" y="65" font-family="Segoe UI,Arial,sans-serif" font-size="36" font-weight="700" fill="#163453">${service.toUpperCase()} SERVICE | UUID CHAR(36)</text>`,
    '<text x="65" y="108" font-family="Segoe UI,Arial,sans-serif" font-size="22" fill="#52657d">Nguồn: init.sql. PK và ID tham chiếu đều là UUID 36 ký tự.</text>'];
  let edgeIndex=0;
  for(const table of tables)for(const fk of table.foreignKeys){
    const parent=tables.find(t=>t.name===fk.parent),child=table;
    let points;
    if(parent.y===child.y)points=[[parent.x+650,parent.y+85],[child.x,child.y+85]];
    else{
      const startX=parent.x+325+(edgeIndex%3-1)*70,targetX=child.x+325,midY=child.y-35-(edgeIndex%3)*17;
      points=[[startX,parent.y+parent.height]];
      if(child.row-parent.row>1){const gutterX=child.x+690;points.push([startX,parent.y+parent.height+35],[gutterX,parent.y+parent.height+35],[gutterX,midY]);}
      else points.push([startX,midY]);
      points.push([targetX,midY],[targetX,child.y]);
    }
    svg.push(`<polyline data-fk="${fk.child}.${fk.column}" points="${points.map(p=>p.join(',')).join(' ')}" fill="none" stroke="#617891" stroke-width="3" marker-end="url(#arrow)"><title>${escape(fk.parent+'.'+fk.parentColumn+' → '+fk.child+'.'+fk.column)}</title></polyline>`);edgeIndex++;
  }
  for(const table of tables){
    svg.push(`<g data-table="${table.name}"><rect x="${table.x}" y="${table.y}" width="650" height="${table.height}" fill="white" stroke="#c3d0dd" stroke-width="2"/>`,
      `<rect x="${table.x}" y="${table.y}" width="650" height="48" fill="#163453"/>`,
      `<text x="${table.x+18}" y="${table.y+33}" font-family="Segoe UI,Arial,sans-serif" font-size="25" font-weight="700" fill="white">${table.name}</text>`);
    for(const [i,column]of table.columns.entries()){
      const label=column.name+(column.primary?' [PK]':column.foreign?' [FK]':''),color=column.type==='CHAR(36)'?'#08775d':'#172b4d',y=table.y+78+i*31;
      svg.push(`<text data-column="${column.name}" x="${table.x+16}" y="${y}" font-family="Consolas,monospace" font-size="20" fill="${color}">${escape(label)}</text><text x="${table.x+440}" y="${y}" font-family="Consolas,monospace" font-size="20" fill="${color}">${escape(column.type)}</text>`);
    }svg.push('</g>');
  }
  svg.push(`<text x="65" y="${height-35}" font-family="Segoe UI,Arial,sans-serif" font-size="21" fill="#52657d">Mũi tên: cha → con, chỉ FK nội bộ. DDL SHA-256: ${sha.slice(0,16)}</text>`,'</svg>');return svg.join('\n');
}
for(const service of Object.keys(layouts)){
  const relative=service+'-service',sql=fs.readFileSync(path.join(directory,relative,'init.sql'),'utf8'),sha=createHash('sha256').update(sql.replaceAll('\r\n','\n')).digest('hex'),tables=parse(sql,service);
  emit(relative+'/erd.svg',image(service,tables,sha));model.services[service]={sha,tables};
  const relationships=tables.flatMap(t=>t.foreignKeys.map(fk=>`  ${fk.parent} ||--o{ ${fk.child} : ${fk.column}`));
  const entities=tables.map(t=>`  ${t.name} {\n${t.columns.map(c=>'    '+c.type.replaceAll(',','_')+' '+c.name+(c.primary?' PK':c.foreign?' FK':'')).join('\n')}\n  }`);
  const migration=['notification','analytics'].includes(service)?` Migration nền: [V1](../../../${service}-service/src/main/resources/db/migration/V1__init.sql).`:'';
  const legacyImage=service==='order'?' [JPEG tương thích](1.JPEG).':service==='ticket'?' [JPEG tương thích](1.jpeg).':'';
  emit(relative+'/erd.md',`# ERD ${service}-service\n\nNguồn: [init.sql](init.sql), schema tham chiếu hiện hành; không tự triển khai thêm migration/field đang được đề xuất.${migration}\n\n![ERD UUID CHAR(36)](erd.svg)\n\nẢnh SVG và Mermaid được tạo từ cùng DDL, gồm đầy đủ cột. Mọi PK/tham chiếu bảng EventHub dùng UUID CHAR(36); không FK/JOIN xuyên service.${legacyImage}\n\n\`\`\`mermaid\nerDiagram\n${[...relationships,...entities].join('\n')}\n\`\`\`\n\nTạo lại bằng [render-erd.mjs](../render-erd.mjs); kiểm tra lệch nguồn với node docs/database/render-erd.mjs --check từ root. JPEG Order/Ticket tạo lại bằng [render-erd.ps1](../render-erd.ps1). Không đọc ảnh cũ để suy ra kiểu ID. Các bảng/cột kỹ thuật còn đề xuất trong [plans](../../plans/README.md) chỉ được thêm vào ERD sau khi có DDL/migration tương ứng. [Mapping và giới hạn](../README.md).\n`);
}
emit('auth-service/erd.svg',`<svg xmlns="http://www.w3.org/2000/svg" width="1400" height="450" viewBox="0 0 1400 450" role="img" aria-labelledby="title desc">
<title id="title">Auth Service — facade Keycloak, không có schema identity local</title>
<desc id="desc">Auth giữ UI/API riêng và gọi Keycloak. User ID ứng dụng là UUID CHAR(36), mapping sub cần cấu hình. Không tạo bảng mật khẩu hoặc refresh token ứng dụng; DB profile chưa được yêu cầu.</desc>
<rect width="1400" height="450" fill="white"/><g font-family="Segoe UI,Arial,sans-serif" fill="#163453"><text x="50" y="60" font-size="32" font-weight="700">AUTH SERVICE | ID ứng dụng: UUID CHAR(36)</text>
<rect x="50" y="115" width="560" height="210" fill="#f1f6fb" stroke="#617891"/><text x="75" y="155" font-size="25" font-weight="700">Auth facade — UI/API riêng</text><text x="75" y="205" font-size="21">Không lưu credentials/refresh token local</text><text x="75" y="245" font-size="21">Profile DB: chỉ thêm khi có yêu cầu riêng</text><text x="75" y="285" font-size="21">userId UUID; subject mapper còn cấu hình</text>
<path d="M 610 215 L 790 215 L 775 205 M 790 215 L 775 225" fill="none" stroke="#617891" stroke-width="3"/>
<rect x="800" y="115" width="550" height="210" fill="#f1f6fb" stroke="#617891"/><text x="825" y="155" font-size="25" font-weight="700">Keycloak — API/OIDC</text><text x="825" y="205" font-size="21">Quản lý identity, role, session, token</text><text x="825" y="245" font-size="21">Schema nội bộ do Keycloak quản lý</text><text x="825" y="285" font-size="21">EventHub không đọc SQL hoặc tạo FK vào DB này</text>
<text x="50" y="395" font-size="21">Sơ đồ ranh giới dữ liệu; không phải ERD giả định cho các bảng ứng dụng chưa tồn tại.</text></g></svg>`);
emit('auth-service/erd.md',`# Ranh giới dữ liệu Auth\n\n![Auth và Keycloak](erd.svg)\n\nAuth giữ UI/API riêng và gọi Keycloak theo xác nhận chủ dự án. Chưa có schema local được duyệt nên không vẽ các bảng users/password_hash/refresh_tokens như thể cần tạo chúng. UUID CHAR(36) áp dụng cho PK/tham chiếu ứng dụng EventHub, không sửa schema nội bộ Keycloak. [Chi tiết](README.md), [plan Auth](../../plans/auth-service.md).\n`);
if(modelArgument!==-1&&!check){const output=path.resolve(process.argv[modelArgument+1]);fs.mkdirSync(path.dirname(output),{recursive:true});fs.writeFileSync(output,JSON.stringify(model,null,2));}
if(failures.length){console.error('ERD lệch DDL: '+failures.join(', '));process.exitCode=1;}
else console.log(`${check?'Verified':'Generated'} ERD for six services; ${Object.values(model.services).reduce((n,s)=>n+s.tables.length,0)} tables, all application IDs CHAR(36).`);
