'use strict';
let token = '';
const $ = id => document.getElementById(id);
const status = message => { $('status').textContent = message; };
const when = seconds => seconds ? new Date(seconds * 1000).toLocaleString('zh-CN') : '尚无记录';
async function api(path, data = {}) {
  const response = await fetch(path, { method: 'POST', headers: {
    'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token
  }, body: JSON.stringify(data), cache: 'no-store', credentials: 'omit' });
  const result = await response.json();
  if (!response.ok) {
    if (response.status === 401) logout();
    throw new Error(response.status === 401 ? '管理员密钥无效，请重新登录。' :
      response.status === 429 ? '请求过于频繁，请稍后重试。' : '操作未完成，请检查服务状态。');
  }
  return result;
}
function logout() {
  token = ''; $('token').value = ''; $('code').textContent = '';
  $('rows').replaceChildren(); $('new-code').hidden = true;
  $('login').hidden = false; $('workspace').hidden = true;
}
async function refresh() {
  const { licenses } = await api('/admin/list');
  $('count').textContent = `最近 ${licenses.length} 条授权（最多显示 1000 条）`;
  $('empty').hidden = licenses.length > 0;
  $('rows').replaceChildren();
  for (const license of licenses) {
    const row = document.createElement('tr');
    const expired = license.valid_until * 1000 <= Date.now();
    const cells = [license.label || '未填写备注', !license.enabled ? '已停用' : expired ? '已到期' : license.device ? '已激活' : '待激活',
      license.device || '待绑定', when(license.valid_until), when(license.last_seen)];
    for (let index = 0; index < cells.length; index++) {
      const td = document.createElement('td'); td.textContent = cells[index];
      if (index === 0) { const small = document.createElement('small'); small.textContent = license.id; td.append(small); }
      if (index === 2) td.className = 'device';
      row.append(td);
    }
    const td = document.createElement('td'), button = document.createElement('button');
    button.textContent = license.enabled ? '停用' : '恢复授权'; button.className = license.enabled ? 'danger' : 'secondary';
    button.addEventListener('click', async () => {
      if (!confirm(`${license.enabled ? '停用' : '恢复'} ${license.label || license.id} 的授权？`)) return;
      button.disabled = true;
      try { await api('/admin/state', { id: license.id, enabled: !license.enabled }); await refresh(); status('授权状态已更新。'); }
      catch (error) { status(error.message); button.disabled = false; }
    });
    td.append(button); row.append(td); $('rows').append(row);
  }
}
$('login-form').addEventListener('submit', async event => {
  event.preventDefault(); token = $('token').value.trim();
  try { await refresh(); $('token').value = ''; $('login').hidden = true; $('workspace').hidden = false; status('已登录。'); }
  catch (error) { token = ''; status(error.message); }
});
$('issue-form').addEventListener('submit', async event => {
  event.preventDefault(); const button = event.submitter; button.disabled = true;
  try {
    const issued = await api('/admin/issue', { label: $('label').value.trim(), days: Number($('days').value) });
    $('code').textContent = issued.code; $('new-code').hidden = false;
    await refresh(); status('激活码已生成，请保存。');
  } catch (error) { status(error.message); }
  finally { button.disabled = false; }
});
$('refresh').addEventListener('click', () => refresh().then(() => status('列表已更新。')).catch(error => status(error.message)));
$('logout').addEventListener('click', () => { logout(); status('已退出。'); });
$('copy').addEventListener('click', async () => {
  try { await navigator.clipboard.writeText($('code').textContent); status('已复制激活码。'); }
  catch (_) { status('浏览器未允许复制，请手动选中激活码复制。'); }
});
