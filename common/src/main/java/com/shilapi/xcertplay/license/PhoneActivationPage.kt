package com.shilapi.xcertplay.license

/** Self-contained LAN page. Form submission works even when the browser disables JavaScript. */
internal object PhoneActivationPage {
    fun html(path: String, csrf: String, device: String, contact: String, message: String = "", done: Boolean = false): String = """
        <!doctype html><html lang="zh-CN"><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <meta name="referrer" content="same-origin"><title>DiPlay 手机协助激活</title>
        <style>
        *{box-sizing:border-box}body{margin:0;background:#f1f5f9;color:#142233;font:16px/1.6 system-ui,sans-serif}
        main{max-width:600px;margin:auto;padding:28px 20px 40px}h1{font-size:26px;line-height:1.3;margin:8px 0 12px}
        h2{font-size:18px;margin:0 0 12px}p{margin:8px 0 16px}.muted{color:#526274;font-size:14px}
        section{background:white;border:1px solid #d9e2ec;border-radius:14px;padding:20px;margin:18px 0}
        textarea,input{font:16px/1.5 system-ui,sans-serif;width:100%;padding:12px;border:1px solid #aab9ca;border-radius:8px;color:#142233;background:white}
        textarea{resize:vertical;overflow-wrap:anywhere;word-break:break-all}label{display:block;margin:12px 0 6px}
        button{font:inherit;border:0;border-radius:8px;padding:12px 16px;margin-top:12px;background:#1463ce;color:white;width:100%}
        button.secondary{background:#e9f1fc;color:#154878}button:active{opacity:.8}
        .notice{padding:14px;border-radius:8px;background:${if (done) "#dcf5e5" else "#fff3d6"};overflow-wrap:anywhere}
        </style><script src="${escape(path)}copy.js" defer></script></head><body><main>
        <p class="muted">DiPlay 星瑞 · 手机协助激活</p><h1>${if (done) "本机已永久激活" else "在手机上完成激活"}</h1>
        <p>${if (done) "车机已保存授权，可以关闭此页面并返回 DiPlay 连接。" else "保持手机与车机在同一网络，车机停留在授权页。此入口开启后 20 分钟内有效。"}</p>
        ${if (message.isNotEmpty()) "<p class=\"notice\" role=\"status\">${escape(message)}</p>" else ""}
        ${if (done) "" else """
        <section><h2>1. 把设备码发给管理员</h2>
        <label for="device">本机设备码</label><textarea id="device" rows="3" readonly spellcheck="false">${escape(device)}</textarea>
        <button class="secondary" type="button" data-copy="device">复制设备码</button>
        <p id="copy-status" class="muted" role="status">将设备码提供给管理员，收到激活码后回到此页面。</p></section>
        <section><h2>2. 粘贴激活码并提交</h2><form method="post" action="${escape(path)}" autocomplete="off">
        <input type="hidden" name="csrf" value="${escape(csrf)}">
        <label for="code">管理员发来的完整激活码</label>
        <textarea id="code" name="code" rows="6" maxlength="4096" required spellcheck="false" autocapitalize="off" autocomplete="off" placeholder="DP-ACT1.…"></textarea>
        <button type="submit">提交并激活车机</button></form></section>
        """}
        <p class="muted">${if (done) "授权已保存。下次直接打开 DiPlay 使用，无需再次扫码；正常覆盖更新会保留授权。" else "无需公网授权服务器。页面打不开时，请检查双方网络；返回车机关闭并重新开启入口后再扫码。不要关闭车机授权页。"}</p>
        </main></body></html>
    """.trimIndent()

    // LAN HTTP is not a secure context: use the legacy copy action, with selection as a fallback.
    val copyScript = """
        document.querySelectorAll('[data-copy]').forEach(function(button){
          button.addEventListener('click',function(){
            var field=document.getElementById(button.getAttribute('data-copy'));
            field.focus();field.select();field.setSelectionRange(0,field.value.length);
            var copied=false;try{copied=document.execCommand('copy');}catch(e){}
            document.getElementById('copy-status').textContent=copied?'设备码已复制。':'已选中文本，请长按选择“复制”。';
          });
        });
    """.trimIndent()

    private fun escape(value: String) = value.replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")
}
