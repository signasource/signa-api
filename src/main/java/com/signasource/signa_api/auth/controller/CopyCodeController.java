package com.signasource.signa_api.auth.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Target of the "copy" button in the verification and password reset emails. Email clients block
 * JavaScript, so the button opens this static page, which copies the code to the clipboard.
 *
 * <p>The code travels in the URL fragment ({@code /codigo#c=482917}). Browsers never send the
 * fragment to the server, so the code does not reach access logs or proxies, and this endpoint
 * never sees it. The page validates it as six digits and writes it with {@code textContent}.
 */
@RestController
public class CopyCodeController {

    private static final String PAGE =
            """
            <!doctype html>
            <html lang="es"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <meta name="referrer" content="no-referrer">
            <title>Copiar código · Signa</title>
            <style>
            body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;background:#FAF6F2;color:#241A16;font-family:'Helvetica Neue',Helvetica,Arial,sans-serif}
            main{max-width:380px;padding:32px 24px;text-align:center}
            h1{margin:0 0 8px;font-size:26px;font-weight:800}
            p{margin:0;font-size:16px;line-height:24px;color:#6B615B}
            #code{margin:24px 0;font-size:40px;font-weight:800;letter-spacing:8px;color:#5E3ED1}
            button{border:0;border-radius:999px;padding:14px 28px;background:#7857FF;color:#fff;font-size:16px;font-weight:700;cursor:pointer}
            #ok{display:none;margin-top:20px;font-weight:700;color:#2E8F7C}
            @media (prefers-color-scheme:dark){body{background:#1E1714;color:#FBF6F2}p{color:#B8ADA6}#code{color:#B7A4FF}}
            </style></head><body><main>
            <h1 id="title">Copiá tu código</h1>
            <p id="msg">Tocá el botón y pegalo en la app de Signa.</p>
            <div id="code" aria-live="polite"></div>
            <button id="copy" type="button">Copiar código</button>
            <p id="ok">¡Copiado! Volvé a Signa y pegalo.</p>
            <script>
            (function () {
              var match = /^#c=(\\d{6})$/.exec(location.hash);
              var code = match ? match[1] : null;
              var $ = function (id) { return document.getElementById(id); };
              if (!code) {
                $("title").textContent = "Este enlace no es válido";
                $("msg").textContent = "Volvé al mail y tocá el botón de nuevo, o escribí el código a mano en la app.";
                $("copy").style.display = "none";
                return;
              }
              $("code").textContent = code;
              function done() {
                $("ok").style.display = "block";
                $("copy").textContent = "Copiar de nuevo";
              }
              function legacyCopy() {
                var area = document.createElement("textarea");
                area.value = code;
                area.setAttribute("readonly", "");
                area.style.position = "fixed";
                area.style.opacity = "0";
                document.body.appendChild(area);
                area.select();
                var copied = false;
                try { copied = document.execCommand("copy"); } catch (e) { copied = false; }
                document.body.removeChild(area);
                if (copied) done();
              }
              function copy() {
                if (navigator.clipboard && navigator.clipboard.writeText) {
                  navigator.clipboard.writeText(code).then(done, legacyCopy);
                } else {
                  legacyCopy();
                }
              }
              $("copy").addEventListener("click", copy);
              copy();
            })();
            </script>
            </main></body></html>
            """;

    @GetMapping(value = "/codigo", produces = MediaType.TEXT_HTML_VALUE)
    public String copyCode() {
        return PAGE;
    }
}
