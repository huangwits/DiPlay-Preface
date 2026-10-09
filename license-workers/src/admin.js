export const adminHtml = `<!doctype html>
<html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>DiPlay · 授权管理</title><link rel="stylesheet" href="/admin.css"><script src="/admin.js" defer></script></head>
<body><div class="topbar"><div class="brand"><span class="brand-icon" aria-hidden="true">D</span><span>DiPlay <small>星瑞</small></span></div><span class="workspace">授权管理台</span></div>
<main><header class="page-heading"><div><p class="eyebrow">DEVICE AUTHORIZATION</p><h1>每一次连接，心中有数。</h1><p class="subtitle">核对申请，管理授权，让车机连接更简单。</p></div><div id="session" hidden><span class="session-state">管理员已登录</span><button id="logout" class="quiet">退出</button></div></header>
<section id="login" class="login-card"><div class="login-intro"><span class="section-kicker">欢迎回来</span><h2>进入授权管理</h2><p>使用管理员令牌登录，查看车机申请及授权状态。</p><div class="login-note">批准后，车机会在一分钟内自动查询授权，也可以在车机上手动刷新。</div></div><form id="loginForm"><label for="token">管理员令牌</label><input id="token" type="password" autocomplete="off" spellcheck="false" required placeholder="请输入管理员令牌"><button id="signIn" type="submit">进入管理 <span aria-hidden="true">→</span></button><p class="hint">令牌仅保留在本页，刷新或退出后清除。</p></form></section>
<section id="panel" hidden aria-label="授权设备"><div class="stats" aria-label="按状态筛选">
<button class="stat pending" data-filter="pending"><span>待批准</span><strong id="count-pending">0</strong><small>等待处理的申请</small></button>
<button class="stat approved" data-filter="approved"><span>授权有效</span><strong id="count-approved">0</strong><small>可申请连接许可</small></button>
<button class="stat expired" data-filter="expired"><span>已到期</span><strong id="count-expired">0</strong><small>可续期恢复授权</small></button>
<button class="stat revoked" data-filter="revoked"><span>已停用</span><strong id="count-revoked">0</strong><small>已由管理员停用</small></button></div>
<section class="list-card"><div class="list-heading"><div><h2>设备申请 <span id="total" class="total">0</span></h2><p id="updated" class="hint">正在读取申请</p></div><button id="refresh" class="secondary">刷新列表</button></div>
<form id="searchForm" class="filters"><div class="search-field"><label for="query" class="sr-only">搜索申请号或设备摘要</label><input id="query" type="search" maxlength="64" placeholder="搜索申请号或设备摘要" autocomplete="off" spellcheck="false"><button type="submit" class="secondary">搜索</button></div><label for="filter" class="sr-only">授权状态</label><select id="filter"><option value="all">全部状态</option><option value="pending">待批准</option><option value="approved">授权有效</option><option value="expired">已到期</option><option value="revoked">已停用</option></select></form>
<div class="table-head" aria-hidden="true"><span>申请 / 设备</span><span>授权状态</span><span>提交时间</span><span>有效期至</span><span>操作</span></div><div id="items"></div><div id="empty" class="empty" hidden><strong>暂无申请</strong><p>等待车机提交申请，或调整搜索条件。</p></div><div class="list-footer"><span id="resultCount" class="hint"></span><button id="more" class="secondary" hidden>加载更多</button></div></section>
<p class="policy-note">停用将在现有连接许可到期后生效，最长 5 分钟；正在进行的 CarPlay 不会被中断。</p></section>
<p id="message" role="status" aria-live="polite" hidden></p><footer>DiPlay · 软件授权管理 <span>请通过可信渠道核对申请号与设备摘要。</span></footer></main>
<dialog id="decision"><form id="decisionForm"><div class="dialog-heading"><span class="section-kicker">授权操作</span><button id="cancelX" type="button" class="quiet" aria-label="关闭">×</button></div><h2 id="decisionTitle"></h2><p class="hint">申请号 <strong id="decisionId"></strong></p><p id="decisionHint"></p><div id="duration"><label for="days">授权天数</label><input id="days" type="number" min="1" max="3650" step="1" value="365" required><div class="presets"><button type="button" data-days="30" class="secondary">30 天</button><button type="button" data-days="90" class="secondary">90 天</button><button type="button" data-days="365" class="secondary">1 年</button></div><p class="hint">从本次批准时间起计算，续期也会重新计算有效期。</p></div><p id="decisionError" role="alert" hidden></p><div class="dialog-actions"><button id="cancel" type="button" class="secondary">取消</button><button id="confirm" type="submit">确认批准</button></div></form></dialog></body></html>`;

export const adminCss = `:root{font-family:Inter,-apple-system,BlinkMacSystemFont,"Segoe UI","Microsoft YaHei",sans-serif;color:#24352f;background:#f6f7f4;color-scheme:light;font-synthesis:none}*{box-sizing:border-box}body{margin:0}button,input,select{font:inherit}button{cursor:pointer}button:disabled{opacity:.5;cursor:wait}button,input,select{min-height:44px;border-radius:9px}button{border:1px solid transparent;background:#24654e;color:#fff;padding:10px 18px;font-size:14px;font-weight:600}button:hover{filter:brightness(.96)}button:focus-visible,input:focus-visible,select:focus-visible{outline:3px solid #a8ceb8;outline-offset:3px}input,select{border:1px solid #dce1da;background:#fff;color:#24352f;padding:11px 13px;width:100%}input::placeholder{color:#8b938e}label{display:block;font-size:14px;font-weight:600;margin-bottom:9px}h1,h2,p{margin:0}h1{font-size:32px;font-weight:600;letter-spacing:-1px;line-height:1.4}h2{font-size:19px;font-weight:600}p{line-height:1.7}.topbar{height:76px;background:#fff;border-bottom:1px solid #e4e8e0;display:flex;align-items:center;justify-content:space-between;padding:0 max(28px,calc((100vw - 1180px)/2))}.brand{display:flex;align-items:center;gap:11px;font-size:20px;font-weight:650}.brand-icon{display:grid;place-items:center;background:#24654e;width:34px;height:34px;border-radius:10px;color:#fff;font-size:22px}.brand small{font-size:12px;font-weight:400;border-left:1px solid #d5ddd4;padding-left:12px;margin-left:12px;color:#6f7e73}.workspace{font-size:13px;color:#6c7970}main{max-width:1236px;margin:auto;padding:42px 28px 22px}.page-heading{display:flex;align-items:center;justify-content:space-between;gap:20px;margin-bottom:32px}.eyebrow{color:#778778;font-size:10px;letter-spacing:2px;font-weight:600;margin-bottom:10px}.subtitle{color:#788278;font-size:14px;margin-top:10px}#session{display:flex;align-items:center;gap:15px}.session-state{font-size:12px;color:#60816a}.session-state:before{content:'';display:inline-block;width:6px;height:6px;border-radius:50%;background:#51835f;margin-right:7px}.quiet{background:transparent;color:#6e7b70;padding:8px 12px}.secondary{background:#fff;border-color:#dce2d9;color:#526454}.login-card{display:grid;grid-template-columns:1.1fr 1fr;background:#fff;border:1px solid #e1e6dd;border-radius:18px;overflow:hidden;max-width:880px;margin:54px auto 90px;box-shadow:0 10px 35px #233c2705}.login-intro{padding:42px;background:#edf2e9}.section-kicker{font-size:12px;color:#6c816c}.login-intro h2{font-size:25px;margin:12px 0}.login-intro p{color:#667663;font-size:14px}.login-note{font-size:12px;color:#768271;line-height:1.9;border-top:1px solid #d7e1d2;padding-top:20px;margin-top:36px}#loginForm{padding:46px 38px;align-self:center}#signIn{width:100%;margin:18px 0 12px;display:flex;justify-content:space-between}.hint{font-size:12px;color:#7b867d;line-height:1.7}.stats{display:grid;grid-template-columns:repeat(4,1fr);gap:16px;margin-bottom:26px}.stat{text-align:left;color:#4a5a4e;background:#fff;border:1px solid #e0e5dd;border-radius:13px;padding:20px 22px;font-weight:400;display:flex;flex-direction:column;gap:7px}.stat span{font-size:13px}.stat strong{font-size:32px;font-weight:550;line-height:1.2;font-variant-numeric:tabular-nums}.stat small{font-size:11px;color:#8a938a}.stat.pending{background:#eff3e9;border-color:#d8e2cd}.stat.pending strong{color:#4d693c}.stat[aria-pressed=true]{outline:2px solid #6e8b68;outline-offset:2px}.list-card{border:1px solid #e1e5dd;border-radius:14px;background:white;overflow:hidden}.list-heading{display:flex;justify-content:space-between;align-items:center;padding:24px 24px 17px;gap:16px}.list-heading .hint{margin-top:5px}.total{font-size:11px;display:inline-block;vertical-align:middle;color:#75816f;font-weight:400;background:#f0f3ed;border-radius:5px;padding:3px 7px;margin-left:7px}.filters{display:flex;justify-content:space-between;gap:16px;padding:0 24px 22px}.search-field{display:flex;gap:8px;max-width:480px;flex:1}.search-field input{min-width:0}.search-field button{flex-shrink:0}.filters select{width:150px;font-size:13px}.table-head,.device{display:grid;grid-template-columns:minmax(205px,1.5fr) minmax(90px,.7fr) minmax(110px,.85fr) minmax(110px,.85fr) minmax(145px,1fr);gap:14px;align-items:center;padding:17px 24px}.table-head{background:#f8f9f6;border-top:1px solid #eef0e9;border-bottom:1px solid #e9ede5;color:#8a9489;font-size:11px}.device{border-bottom:1px solid #eef0e9;font-size:13px;min-height:92px}.request-id{margin:0;font-family:ui-monospace,Consolas,monospace;font-size:15px;letter-spacing:1px;font-weight:600}.device details{margin-top:6px;color:#8a948b;font-size:11px}.device summary{cursor:pointer}.device code{display:block;overflow-wrap:anywhere;white-space:normal;color:#778575;margin-top:6px;font-size:11px;letter-spacing:0}.badge{display:inline-flex;align-items:center;gap:6px;font-size:11px;border-radius:5px;padding:6px 8px;width:max-content}.badge:before{content:'';width:5px;height:5px;border-radius:50%;background:currentColor}.badge.pending{background:#faf1d9;color:#97741d}.badge.approved{background:#edf5eb;color:#487a4c}.badge.expired{background:#f3f0eb;color:#87755b}.badge.revoked{background:#f9eded;color:#a46e6e}.date{color:#627162;font-size:12px;line-height:1.8}.date small{display:block;color:#a0a79e;font-size:11px}.actions{display:flex;gap:6px}.actions button{font-size:12px;padding:8px 12px;min-height:38px}.actions .danger{background:transparent;color:#a77471}.danger{background:#a25451;color:white}.list-footer{display:flex;justify-content:space-between;align-items:center;padding:15px 24px;min-height:56px}.policy-note{font-size:12px;color:#929c90;margin:18px 2px 0}.empty{text-align:center;padding:58px 20px;color:#64715f}.empty strong{font-size:15px;font-weight:500}.empty p{font-size:13px;color:#9aa194;margin-top:7px}#message{padding:13px 17px;border-radius:9px;background:#eaf1e5;color:#496944;font-size:13px;margin-top:18px;overflow-wrap:anywhere}#message.error,#decisionError{background:#faeeee;color:#a25353}footer{display:flex;justify-content:space-between;gap:14px;margin-top:38px;padding-top:20px;border-top:1px solid #e4e8df;font-size:11px;color:#99a190}dialog{border:1px solid #dde3d8;border-radius:16px;padding:28px;width:min(440px,calc(100vw - 28px));box-shadow:0 20px 80px #19331c22}dialog::backdrop{background:#23352955;backdrop-filter:blur(3px)}.dialog-heading{display:flex;align-items:center;justify-content:space-between;margin-bottom:6px}dialog h2{font-size:24px;margin-bottom:12px}#decisionHint{font-size:13px;color:#74806e;margin:16px 0}#days{max-width:100%}.presets{display:flex;gap:8px;margin:10px 0}.presets button{flex:1;font-size:12px}.dialog-actions{display:flex;justify-content:flex-end;gap:10px;margin-top:24px}#decisionError{font-size:12px;padding:10px;margin-top:10px;border-radius:6px}.sr-only{position:absolute;width:1px;height:1px;padding:0;margin:-1px;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;border:0}[hidden]{display:none!important}@media(max-width:900px){h1{font-size:27px}.page-heading{align-items:flex-start}.table-head{display:none}.device{grid-template-columns:1fr 1fr;gap:12px}.device .identity{grid-column:1}.device .badge{grid-column:2;grid-row:1;justify-self:end}.actions{grid-column:1/-1;justify-content:flex-end}.date:before{content:attr(data-label);display:block;font-size:10px;color:#9aa193}.stats{gap:10px}.stat{padding:16px}.stat small{font-size:10px}.login-intro{padding:30px}#loginForm{padding:32px}}@media(max-width:580px){.topbar{height:64px;padding:0 20px}main{padding:28px 16px 20px}.page-heading{display:block;margin-bottom:24px}h1{font-size:26px}.eyebrow{font-size:9px}#session{margin-top:12px;justify-content:space-between}.stats{grid-template-columns:1fr 1fr;gap:12px}.stat{padding:16px 18px}.stat strong{font-size:30px}.stat small{font-size:11px}.list-heading{padding:20px 16px 15px}.filters{padding:0 16px 16px;flex-wrap:wrap;gap:10px}.search-field{flex-basis:100%;max-width:none}.filters select{width:100%}.device{padding:19px 16px;column-gap:10px}.list-footer{padding:15px 16px}.login-card{display:block;margin:28px 0 46px}.login-intro{padding:27px}.login-note{margin-top:20px;padding-top:16px}#loginForm{padding:28px}.policy-note{font-size:11px}footer{flex-direction:column;margin-top:26px;gap:4px}.workspace{font-size:11px}.dialog-actions button{flex:1}.request-id{font-size:14px}}`;

export const adminJs = `(() => {
  const el = id => document.getElementById(id);
  const labels = {pending:'待批准',approved:'授权有效',expired:'已到期',revoked:'已停用'};
  let token='', next=null, busy=false, epoch=0, loaded=0, selected=null, filter='all', query='', displayedFilter='all', displayedQuery='';
  function message(text, error=false) { el('message').textContent=text;el('message').className=error?'error':'';el('message').hidden=!text; }
  function logout() {
    epoch++;token='';next=null;loaded=0;selected=null;filter='all';query='';displayedFilter='all';displayedQuery='';
    el('token').value='';el('query').value='';el('filter').value='all';el('items').replaceChildren();
    el('decision').close();el('panel').hidden=true;el('session').hidden=true;el('login').hidden=false;message('');
  }
  async function api(path, body) {
    const generation=epoch;
    const response=await fetch(path,{method:'POST',cache:'no-store',credentials:'omit',headers:{'Content-Type':'application/json',Authorization:'Bearer '+token},body:JSON.stringify(body)});
    if(generation!==epoch)throw new Error('cancelled');
    if(response.status===401){logout();throw new Error('管理员令牌无效，请重新登录。');}
    if(!response.ok)throw new Error(response.status===429?'操作过于频繁，请一分钟后重试。':'操作未完成，请检查网络后重试（'+response.status+'）。');
    const data=await response.json();if(generation!==epoch)throw new Error('cancelled');return data;
  }
  function controls(disabled) { document.querySelectorAll('button,input,select').forEach(n=>n.disabled=disabled);el('panel').setAttribute('aria-busy',String(disabled)); }
  async function run(action) {
    if(busy)return;busy=true;controls(true);message('');
    try { await action(); } catch(error) {
      if(error.message!=='cancelled'){
        filter=displayedFilter;query=displayedQuery;el('filter').value=filter;el('query').value=query;
        const text=error.message==='Failed to fetch'?'网络连接失败，请稍后重试。':error.message||'操作失败，请稍后重试。';
        if(el('decision').open){el('decisionError').textContent=text;el('decisionError').hidden=false;}else message(text,true);
      }
    } finally { busy=false;controls(false); }
  }
  function add(parent,tag,text,cls) {const n=document.createElement(tag);n.textContent=text;if(cls)n.className=cls;parent.append(n);return n;}
  function date(parent,value,label) {
    const n=add(parent,'div','','date');n.dataset.label=label;
    if(!value){n.textContent='—';return;}
    const d=new Date(value*1000);add(n,'span',d.toLocaleDateString('zh-CN'));add(n,'small',d.toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit'}));
  }
  function openDecision(row,status,current) {
    selected={row,status};el('decisionId').textContent=row.request_id;
    const title=status==='revoked'?'停用授权':current==='pending'?'批准申请':'续期 / 恢复授权';
    el('decisionTitle').textContent=title;el('confirm').textContent='确认'+(status==='revoked'?'停用':'批准');
    el('confirm').className=status==='revoked'?'danger':'';el('duration').hidden=status==='revoked';
    el('days').required=status==='approved';el('decisionHint').textContent=status==='revoked'?'现有连接许可最长 5 分钟后到期，正在进行的 CarPlay 不受影响。':'请先通过可信渠道核对该申请号与设备摘要。';
    el('decisionError').hidden=true;el('decision').showModal();el('cancel').focus();
  }
  function render(row,now) {
    const status=row.status==='approved'&&row.valid_until<=now?'expired':row.status;
    const card=add(el('items'),'article','','device');card.setAttribute('aria-label','申请 '+row.request_id);
    const identity=add(card,'div','','identity');add(identity,'h3',row.request_id,'request-id');
    const details=add(identity,'details','');add(details,'summary','查看设备摘要');add(details,'code',row.device);
    add(card,'span',labels[status]||'未知状态','badge '+status);date(card,row.created_at,'提交时间');date(card,row.valid_until,'有效期至');
    const actions=add(card,'div','','actions');const approve=add(actions,'button',status==='pending'?'批准':status==='revoked'?'恢复':'续期','secondary');
    approve.onclick=()=>openDecision(row,'approved',status);
    if(status!=='revoked'){const revoke=add(actions,'button','停用','danger');revoke.onclick=()=>openDecision(row,'revoked',status);}
  }
  async function list(append=false) {
    const data=await api('/admin/list',{status:filter,query,...(append&&next?{before:next}:{})});
    if(!append){el('items').replaceChildren();loaded=0;}
    data.items.forEach(row=>render(row,data.now));loaded+=data.items.length;next=data.next;
    el('more').hidden=!next;el('empty').hidden=loaded>0;el('resultCount').textContent='已显示 '+loaded+' 条'+(next?' · 还有更多申请':'');
    el('total').textContent=data.counts.total;for(const key of Object.keys(labels))el('count-'+key).textContent=data.counts[key];
    document.querySelectorAll('[data-filter]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.filter===filter)));
    el('updated').textContent='最近更新 '+new Date().toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit'});
    displayedFilter=filter;displayedQuery=query;el('filter').value=filter;el('query').value=query;el('login').hidden=true;el('panel').hidden=false;el('session').hidden=false;
  }
  function search(){const value=el('query').value.trim().replace(/\\s/g,'');if(!/^[0-9a-fA-F]{0,64}$/.test(value))throw new Error('申请号或设备摘要只包含数字与 A–F，请检查输入。');query=value;filter=el('filter').value;return list();}
  el('loginForm').onsubmit=e=>{e.preventDefault();const value=el('token').value.trim();if(!value)return;run(async()=>{token=value;el('token').value='';await list();});};
  el('searchForm').onsubmit=e=>{e.preventDefault();run(search);};el('filter').onchange=()=>run(search);
  document.querySelectorAll('[data-filter]').forEach(b=>b.onclick=()=>run(()=>{filter=b.dataset.filter;return list();}));
  el('refresh').onclick=()=>run(()=>list());el('more').onclick=()=>run(()=>list(true));el('logout').onclick=logout;
  document.querySelectorAll('[data-days]').forEach(b=>b.onclick=()=>{el('days').value=b.dataset.days;});
  for(const id of ['cancel','cancelX'])el(id).onclick=()=>el('decision').close();
  el('decision').addEventListener('cancel',e=>{if(busy)e.preventDefault();});
  el('decisionForm').onsubmit=e=>{e.preventDefault();if(!selected)return;run(async()=>{
    const {row,status}=selected,days=Number(el('days').value);
    if(status==='approved'&&(!Number.isInteger(days)||days<1||days>3650))throw new Error('授权天数请输入 1 至 3650 的整数。');
    await api('/admin/decision',{requestId:row.request_id,status,days});el('decision').close();
    const done='申请 '+row.request_id+(status==='approved'?' 已批准 '+days+' 天。车机可在一分钟内自动查询，也可手动刷新。':' 已停用。');
    try {await list();message(done);}catch(error){message(done+' 列表刷新失败，请手动刷新。',true);}
  });};
  window.addEventListener('pagehide',logout);
})();`;
