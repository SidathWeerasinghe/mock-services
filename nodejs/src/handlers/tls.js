'use strict';

function resolveProtocolByPort(port) {
  switch (port) {
    case 8442: return 'TLSv1.2';
    case 8443: return 'TLSv1.3';
    case 8444: return 'TLSv1.2 or TLSv1.3';
    default: return 'unknown';
  }
}

function tlsInfo(req, res) {
  const port = req.socket.localPort || parseInt(req.get('host')?.split(':')[1], 10) || 8080;
  const info = {
    timestamp: new Date().toISOString(),
    scheme: req.secure || req.protocol === 'https' ? 'https' : 'http',
    serverPort: port,
    remoteAddr: req.ip || req.socket.remoteAddress,
  };

  if (req.secure || req.protocol === 'https') {
    info.tlsEnabled = true;
    const proto = req.socket.getProtocol?.();
    if (proto) {
      info.negotiatedProtocol = proto;
      info.cipherSuite = req.socket.getCipher?.()?.name;
    } else {
      info.negotiatedProtocol = resolveProtocolByPort(port);
      info.note = 'SSLSession attribute unavailable; port-based protocol inferred';
    }
  } else {
    info.tlsEnabled = false;
    info.negotiatedProtocol = 'none (plain HTTP)';
    info.note = 'Connect via https:// to test TLS';
  }

  res.json(info);
}

module.exports = { tlsInfo };
