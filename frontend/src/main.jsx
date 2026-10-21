import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import '@fortawesome/fontawesome-svg-core/styles.css';
// Fuente Inter de la plantilla, empaquetada con la app para que también funcione sin internet.
import '@fontsource/inter/latin-400.css';
import '@fontsource/inter/latin-500.css';
import '@fontsource/inter/latin-600.css';
import './app.css';
import App from './App.jsx';
import { ProveedorAvisos } from './componentes.jsx';

createRoot(document.getElementById('raiz')).render(
  <StrictMode><ProveedorAvisos><App /></ProveedorAvisos></StrictMode>);

if ('serviceWorker' in navigator) navigator.serviceWorker.register('/sw.js').catch(() => {});
