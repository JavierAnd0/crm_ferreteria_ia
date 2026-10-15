import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import '@fortawesome/fontawesome-svg-core/styles.css';
import './app.css';
import App from './App.jsx';
import { ProveedorAvisos } from './componentes.jsx';

createRoot(document.getElementById('raiz')).render(
  <StrictMode><ProveedorAvisos><App /></ProveedorAvisos></StrictMode>);

if ('serviceWorker' in navigator) navigator.serviceWorker.register('/sw.js').catch(() => {});
