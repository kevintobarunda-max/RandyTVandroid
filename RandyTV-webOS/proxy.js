#!/usr/bin/env node
/**
 * RandyTV Proxy Server
 * Corre en tu Mac y reenvía peticiones al servidor Xtream
 * con headers de navegador para evitar el bloqueo de Cloudflare.
 * 
 * Uso: node proxy.js
 * Puerto: 9090
 */

var http  = require('http');
var https = require('https');
var url   = require('url');

var TARGET_HOST = 'tv.streamid.tv';
var TARGET_PORT = 443;
var TARGET_HTTPS = true;
var PROXY_PORT  = process.env.PORT || 9090;

var BROWSER_HEADERS = {
  'User-Agent':      'Mozilla/5.0 (SMART-TV; Linux; Tizen 5.0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/56.0.2924.0 Safari/537.36',
  'Accept':          'application/json, text/plain, */*',
  'Accept-Language': 'es-ES,es;q=0.9',
  'Accept-Encoding': 'identity',
  'Connection':      'keep-alive',
  'Cache-Control':   'no-cache',
  'Pragma':          'no-cache'
};

var server = http.createServer(function(req, res) {

  /* CORS preflight */
  if (req.method === 'OPTIONS') {
    res.writeHead(200, {
      'Access-Control-Allow-Origin':  '*',
      'Access-Control-Allow-Methods': 'GET, OPTIONS',
      'Access-Control-Allow-Headers': '*'
    });
    res.end();
    return;
  }

  var parsedUrl = url.parse(req.url);
  var targetPath = parsedUrl.path;

  console.log('[PROXY] ' + targetPath.substring(0, 80));

  var options = {
    hostname: TARGET_HOST,
    port:     TARGET_PORT,
    path:     targetPath,
    method:   'GET',
    headers:  BROWSER_HEADERS
  };

  var proxyReq = (TARGET_HTTPS ? https : http).request(options, function(proxyRes) {
    var chunks = [];

    proxyRes.on('data', function(chunk) { chunks.push(chunk); });

    proxyRes.on('end', function() {
      var body = Buffer.concat(chunks);

      /* Si Cloudflare devuelve HTML en vez de JSON, loguear */
      var ct = proxyRes.headers['content-type'] || '';
      if (ct.indexOf('html') !== -1) {
        console.log('[PROXY] WARN: respuesta HTML (posible bloqueo Cloudflare)');
      }

      res.writeHead(proxyRes.statusCode, {
        'Content-Type':                  ct || 'application/json',
        'Access-Control-Allow-Origin':   '*',
        'Access-Control-Allow-Methods':  'GET, OPTIONS',
        'Cache-Control':                 'no-cache'
      });
      res.end(body);
    });
  });

  proxyReq.on('error', function(e) {
    console.error('[PROXY] Error: ' + e.message);
    res.writeHead(502, { 'Access-Control-Allow-Origin': '*' });
    res.end(JSON.stringify({ error: e.message }));
  });

  proxyReq.setTimeout(20000, function() {
    proxyReq.abort();
    res.writeHead(504, { 'Access-Control-Allow-Origin': '*' });
    res.end(JSON.stringify({ error: 'timeout' }));
  });

  proxyReq.end();
});

server.listen(PROXY_PORT, '0.0.0.0', function() {
  console.log('');
  console.log('  RandyTV Proxy activo en puerto ' + PROXY_PORT);
  console.log('  El TV debe usar: http://172.20.10.14:' + PROXY_PORT);
  console.log('  Presiona Ctrl+C para detener');
  console.log('');
});
