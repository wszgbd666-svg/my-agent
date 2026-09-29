const QRCode = require('qrcode');
QRCode.toFile('../扫码打开.png', 'http://192.168.10.20:8000', {
  width: 600, margin: 2,
  color: { dark: '#10202f', light: '#ffffff' },
}, (err) => {
  if (err) { console.error('ERR', err); process.exit(1); }
  console.log('QR OK');
});
